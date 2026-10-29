import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { writeFile } from "node:fs/promises";
import { restoreAll } from "./smoke-support.mjs";

// Только локальный учебный стенд. Платежи не удаляются; исходные режимы восстанавливаются.
const base = "http://127.0.0.1:5173";
const results = [];
const observations = {};
let restorationFailures = [];
const runFile = promisify(execFile);

async function api(path, method = "GET", body) {
  const response = await fetch(base + path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(12000),
  });
  const value = await response.json();
  assert.ok(response.ok, `${method} ${path}: ${JSON.stringify(value)}`);
  return value;
}

async function eventually(read, matches) {
  for (let attempt = 0; attempt < 60; attempt++) {
    const value = await read();
    if (matches(value)) return value;
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  throw new Error("Ожидаемый результат не появился");
}

async function test(name, action) {
  await action();
  results.push({ name, passed: true });
  console.log("PASS", name);
}

const settings = (value) => api("/api/admin/settings", "PUT", value);
const history = (id) => api("/api/audit/entries?paymentId=" + id);
const paymentLogs = () => api("/api/admin/logs?limit=500");
const create = (description) =>
  api("/api/payments", "POST", {
    customerId: 1,
    amount: 2300,
    description,
    paymentType: "CARD",
  });
const original = {
  payment: (await api("/api/admin/status")).settings,
  antifraud: await api("/api/antifraud/admin/status"),
  audit: await api("/api/audit/admin/status"),
};

try {
  await test("Normal preset resets eventCopies as well as the other demo modes", async () => {
    await settings({ eventCopies: 5 });
    await runFile(process.execPath, ["scripts/scenario.mjs", "normal"], {
      cwd: new URL("..", import.meta.url).pathname,
    });
    assert.equal((await api("/api/admin/status")).settings.eventCopies, 1);
  });

  await test("N+1 and JOIN use real SQL visible in ordinary Hibernate logs", async () => {
    const startSequence = (await paymentLogs()).entries.at(-1)?.sequence ?? 0;
    await settings({ fetchMode: "nplus1" });
    const before = await api("/api/payments");
    const owners = new Set(before.payments.map((payment) => payment.customerId))
      .size;
    assert.ok(owners > 1);
    const lazySql = (await paymentLogs()).entries
      .filter(
        (entry) =>
          entry.sequence > startSequence &&
          entry.logger === "org.hibernate.SQL",
      )
      .map((entry) => entry.message);
    assert.ok(lazySql.some((sql) => /from\s+customers\b/i.test(sql)));
    const joinStart = (await paymentLogs()).entries.at(-1)?.sequence ?? 0;
    await settings({ fetchMode: "join" });
    const after = await api("/api/payments");
    const joinSql = (await paymentLogs()).entries
      .filter(
        (entry) =>
          entry.sequence > joinStart && entry.logger === "org.hibernate.SQL",
      )
      .map((entry) => entry.message);
    assert.ok(joinSql.some((sql) => /join\s+customers\b/i.test(sql)));
    assert.deepEqual(
      before.payments.map((payment) => payment.id),
      after.payments.map((payment) => payment.id),
    );
    // Точное число запросов проверяет JPA-тест. Здесь показываем тот же SQL, что видит ведущий.
    observations.nplus1 = { owners, lazySql, joinSql };
  });

  await test("Redis stale exposes NEW while DB is CONFIRMED; safe invalidates after commit", async () => {
    await settings({ cacheEnabled: true, cacheInvalidationEnabled: false });
    const stalePayment = await create("Проверка · устаревший кэш");
    await api("/api/payments/" + stalePayment.id);
    await api("/api/payments/" + stalePayment.id + "/confirm", "POST");
    const cached = await api("/api/payments/" + stalePayment.id);
    assert.equal(cached.source, "REDIS");
    assert.equal(cached.payment.status, "NEW");
    await settings({ cacheEnabled: false });
    const actual = await api("/api/payments/" + stalePayment.id);
    assert.equal(actual.source, "POSTGRESQL");
    assert.equal(actual.payment.status, "CONFIRMED");
    await settings({ cacheEnabled: true, cacheInvalidationEnabled: true });
    const safePayment = await create("Проверка · инвалидация кэша");
    await api("/api/payments/" + safePayment.id);
    await api("/api/payments/" + safePayment.id + "/confirm", "POST");
    const fresh = await api("/api/payments/" + safePayment.id);
    assert.equal(fresh.source, "POSTGRESQL");
    assert.equal(fresh.payment.status, "CONFIRMED");
    observations.redis = {
      stalePayment: stalePayment.id,
      cache: cached.payment.status,
      database: actual.payment.status,
      safePayment: safePayment.id,
      safe: fresh.payment.status,
    };
  });

  await test("Five Kafka records produce five receipts, four ignored duplicates and one Audit row", async () => {
    await settings({ eventCopies: 5 });
    const payment = await create("Проверка · пять копий Kafka");
    const entries = await eventually(
      () => history(payment.id),
      (rows) => rows.length === 1,
    );
    const eventId = entries[0].eventId;
    const logs = await eventually(
      () => api("/api/audit/admin/logs?limit=500"),
      (value) =>
        value.entries.filter(
          (entry) =>
            entry.message.includes(eventId) &&
            entry.message.includes("duplicate ignored"),
        ).length === 4,
    );
    const messages = logs.entries.filter((entry) =>
      entry.message.includes(eventId),
    );
    assert.equal(
      messages.filter((entry) => entry.message.includes("Audit received"))
        .length,
      5,
    );
    assert.equal(
      messages.filter((entry) => entry.message.includes("Audit saved")).length,
      1,
    );
    assert.equal((await history(payment.id)).length, 1);
    const producer = await api("/api/admin/logs?limit=500");
    assert.equal(
      producer.entries.filter(
        (entry) =>
          entry.message.includes(eventId) &&
          entry.message.includes("Kafka published"),
      ).length,
      5,
    );
    observations.kafka = {
      paymentId: payment.id,
      eventId,
      published: 5,
      received: 5,
      ignoredDuplicates: 4,
      saved: 1,
    };
  });

  await test("Dark Swagger pages and official assets are served by all three services", async () => {
    for (const port of [8081, 8082, 8083]) {
      for (const path of [
        "/swagger-dark/index.html",
        "/swagger-dark/theme.css",
        "/swagger-dark/init.js",
        "/swagger-ui/swagger-ui-bundle.js",
      ]) {
        const response = await fetch("http://127.0.0.1:" + port + path);
        assert.equal(response.status, 200, `${port}${path}`);
      }
    }
  });

  await test("Real log endpoints are bounded and reject an invalid limit", async () => {
    for (const prefix of ["/api", "/api/antifraud", "/api/audit"]) {
      const logs = await api(prefix + "/admin/logs");
      assert.ok(logs.entries.length > 0 && logs.entries.length <= 200);
      assert.ok(
        logs.entries.every(
          (entry) =>
            entry.service === logs.service &&
            Number.isFinite(Date.parse(entry.timestamp)) &&
            typeof entry.sequence === "number",
        ),
      );
      assert.equal(
        (await fetch(base + prefix + "/admin/logs?limit=501")).status,
        400,
      );
    }
  });
} finally {
  restorationFailures = await restoreAll({
    auditProtection: () =>
      api("/api/audit/admin/settings", "PUT", {
        idempotencyEnabled: original.audit.idempotencyEnabled,
      }),
    payment: () => settings(original.payment),
    antifraud: () =>
      api("/api/antifraud/admin/mode", "PUT", {
        mode: original.antifraud.mode,
      }),
    auditConsumer: () =>
      api(
        "/api/audit/admin/" + (original.audit.paused ? "pause" : "resume"),
        "POST",
      ),
  });
  for (const failure of restorationFailures)
    console.error("RESTORE FAILED", failure.step, failure.error);
  await writeFile(
    new URL("../.runtime/features-smoke-results.json", import.meta.url),
    JSON.stringify(
      {
        at: new Date().toISOString(),
        results,
        observations,
        restorationFailures: restorationFailures.map(({ step, error }) => ({
          step,
          message: String(error),
        })),
      },
      null,
      2,
    ),
  );
}

if (restorationFailures.length)
  throw new AggregateError(
    restorationFailures.map((value) => value.error),
    "Не все настройки восстановлены",
  );
console.log(`${results.length} feature integration scenarios passed.`);
