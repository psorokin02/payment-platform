import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { writeFile } from "node:fs/promises";
import { restoreAll, auditProcessingComplete } from "./smoke-support.mjs";

// Локальный учебный стенд: создаём факты, ничего не удаляем; настройки восстанавливаем.
const base = "http://127.0.0.1:5173";
const runFile = promisify(execFile);
const results = [];
const observations = {};
let restorationFailures = [];

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
  for (let attempt = 0; attempt < 80; attempt++) {
    const value = await read();
    if (matches(value)) return value;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error("Audit не завершил ожидаемую обработку");
}

async function test(name, action) {
  await action();
  results.push({ name, passed: true });
  console.log("PASS", name);
}

const history = (id) => api("/api/audit/entries?paymentId=" + id);
async function protection(enabled) {
  await api("/api/audit/admin/settings", "PUT", {
    idempotencyEnabled: enabled,
  });
  assert.equal(
    (await api("/api/audit/admin/status")).idempotencyEnabled,
    enabled,
  );
}
async function messages(eventId, received) {
  const logs = await eventually(
    () => api("/api/audit/admin/logs?limit=500"),
    (value) => auditProcessingComplete(value.entries, eventId, received),
  );
  return logs.entries.filter((entry) => entry.message.includes(eventId));
}
async function fiveCopies(enabled, expectedRows) {
  await protection(enabled);
  const payment = await api("/api/payments", "POST", {
    customerId: 1,
    amount: 3700,
    paymentType: "CARD",
    description: enabled
      ? "Audit · защита включена · пять копий"
      : "Audit · защита выключена · пять копий",
  });
  const rows = await eventually(
    () => history(payment.id),
    (value) => value.length === expectedRows,
  );
  const eventId = rows[0].eventId;
  const logs = await messages(eventId, 5);
  assert.equal(
    logs.filter((entry) => entry.message.includes("Audit saved")).length,
    expectedRows,
  );
  assert.equal(
    logs.filter((entry) => entry.message.includes("duplicate ignored")).length,
    5 - expectedRows,
  );
  assert.equal(new Set(rows.map((row) => row.eventId)).size, 1);
  assert.equal(new Set(rows.map((row) => row.id)).size, expectedRows);
  assert.equal((await history(payment.id)).length, expectedRows);
  return {
    paymentId: payment.id,
    eventId,
    received: 5,
    saved: expectedRows,
    ignored: 5 - expectedRows,
  };
}

const original = {
  payment: (await api("/api/admin/status")).settings,
  antifraud: await api("/api/antifraud/admin/status"),
  audit: await api("/api/audit/admin/status"),
};
// Проверяем контракт до любых записей: старый ещё не обновлённый Audit даст понятный отказ.
assert.equal(
  typeof original.audit.idempotencyEnabled,
  "boolean",
  "Сначала пересоберите и запустите Audit",
);

try {
  await test("Normal preset restores Audit protection after it was disabled", async () => {
    await protection(false);
    await runFile(process.execPath, ["scripts/scenario.mjs", "normal"], {
      cwd: new URL("..", import.meta.url).pathname,
    });
    assert.equal(
      (await api("/api/audit/admin/status")).idempotencyEnabled,
      true,
    );
    await api("/api/admin/settings", "PUT", { eventCopies: 5 });
  });
  await test("Protection ON: five receipts, four ignored repeats, one saved fact", async () => {
    observations.on = await fiveCopies(true, 1);
  });
  await test("Protection OFF: five receipts and five facts with the same eventId", async () => {
    observations.off = await fiveCopies(false, 5);
  });
  await test("Re-enable keeps old duplicates; new event IDs are protected", async () => {
    await protection(true);
    assert.equal((await history(observations.off.paymentId)).length, 5);
    observations.reenabled = await fiveCopies(true, 1);
    assert.equal((await history(observations.off.paymentId)).length, 5);
  });
} finally {
  restorationFailures = await restoreAll({
    auditProtection: () => protection(original.audit.idempotencyEnabled),
    payment: () => api("/api/admin/settings", "PUT", original.payment),
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
    new URL(
      "../.runtime/audit-idempotency-smoke-results.json",
      import.meta.url,
    ),
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
console.log(`${results.length} Audit idempotency scenarios passed.`);
