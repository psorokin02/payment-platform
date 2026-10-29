import assert from "node:assert/strict";
import { auditProcessingComplete } from "./smoke-support.mjs";
import { mkdir, writeFile } from "node:fs/promises";

// Настоящие запросы к локальному стенду. Создаются только учебные платежи; удаления нет.
const base = "http://127.0.0.1:5173";
const results = [];

async function call(path, method = "GET", body) {
  const response = await fetch(base + path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  });
  const data = await response.json();
  if (!response.ok)
    throw Object.assign(new Error(data.message ?? `HTTP ${response.status}`), {
      status: response.status,
    });
  return data;
}

async function eventually(read, expected) {
  for (let attempt = 0; attempt < 80; attempt++) {
    const result = await read();
    if (expected(result)) return result;
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  throw new Error("Audit did not reach the expected state");
}

async function test(name, action) {
  await action();
  results.push({ name, passed: true });
  console.log("PASS", name);
}

const history = (id) => call(`/api/audit/entries?paymentId=${id}`);
let customerId;
const create = (
  paymentType = "CARD",
  amount = 1500,
  description = "Проверка консоли",
) =>
  call("/api/payments", "POST", {
    customerId,
    amount,
    paymentType,
    description,
  });

try {
  await call("/api/antifraud/admin/mode", "PUT", { mode: "normal" });
  await call("/api/admin/settings", "PUT", {
    fetchMode: "join",
    cacheEnabled: true,
    cacheInvalidationEnabled: true,
    httpMode: "timeout",
    eventCopies: 1,
  });
  await call("/api/audit/admin/resume", "POST");
  await call("/api/audit/admin/settings", "PUT", { idempotencyEnabled: true });
  customerId = (await call("/api/customers"))[0].id;

  await test("All three real OpenAPI documents and Swagger UIs are served", async () => {
    for (const [port, path] of [
      [8081, "/api/payments"],
      [8082, "/checks/{requestId}"],
      [8083, "/entries"],
    ]) {
      const response = await fetch(`http://127.0.0.1:${port}/v3/api-docs`);
      assert.equal(response.status, 200);
      const document = await response.json();
      assert.match(document.openapi, /^3\./);
      assert.ok(document.paths[path], `${port} is missing ${path}`);
      const configResponse = await fetch(
        `http://127.0.0.1:${port}/v3/api-docs/swagger-config`,
      );
      const config = await configResponse.json();
      const defaultGroup = config.urls.find(
        (group) => group.name === config["urls.primaryName"],
      );
      assert.equal(
        defaultGroup?.url,
        "/v3/api-docs/business",
        "Swagger must open business operations, not admin settings",
      );
      const page = await fetch(
        `http://127.0.0.1:${port}/swagger-ui/index.html`,
      );
      assert.equal(page.status, 200);
      assert.match(await page.text(), /Swagger UI/);
    }
  });

  await test("CARD and QR allow 100000 and reject 100000.01", async () => {
    for (const type of ["CARD", "QR"]) {
      const allowed = await create(type, 100000);
      assert.equal(allowed.status, "NEW");
      assert.equal(allowed.paymentType, type);
      const rejected = await create(type, 100000.01);
      assert.equal(rejected.status, "REJECTED");
      assert.ok(rejected.rejectionReason);
      assert.equal(
        (await call(`/api/payments/${rejected.id}`)).payment.status,
        "REJECTED",
      );
    }
  });

  await test("INSTALLMENT and RECURRENT are not rejected by the card amount rule", async () => {
    for (const type of ["INSTALLMENT", "RECURRENT"]) {
      assert.equal((await create(type, 150000)).status, "NEW");
    }
  });

  await test("Payment links to the real saved Antifraud decision", async () => {
    const payment = await create("QR", 120000);
    assert.ok(payment.antifraudRequestId);
    const check = await call(
      `/api/antifraud/checks/${payment.antifraudRequestId}`,
    );
    assert.equal(check.requestId, payment.antifraudRequestId);
    assert.equal(check.paymentType, "QR");
    assert.equal(check.amount, 120000);
    assert.equal(check.approved, false);
    assert.equal(check.reason, payment.rejectionReason);
    assert.ok(check.checkedAt);
    assert.deepEqual(
      await call(`/api/antifraud/checks/${payment.antifraudRequestId}`),
      check,
    );
  });

  await test("Created and confirmed facts are saved once with five copies of each event", async () => {
    await call("/api/admin/settings", "PUT", { eventCopies: 5 });
    const payment = await create();
    const created = await eventually(
      () => history(payment.id),
      (entries) => entries.length === 1,
    );
    assert.equal(created[0].type, "PaymentCreated");
    assert.equal(created[0].status, "NEW");
    const updated = await call(`/api/payments/${payment.id}/confirm`, "POST");
    assert.equal(updated.status, "CONFIRMED");
    const before = await eventually(
      () => history(payment.id),
      (entries) => entries.length === 2,
    );
    assert.deepEqual(before.map((entry) => entry.type).sort(), [
      "PaymentConfirmed",
      "PaymentCreated",
    ]);
    await eventually(
      () => call("/api/audit/admin/logs?limit=500"),
      (logs) =>
        before.every((fact) =>
          auditProcessingComplete(logs.entries, fact.eventId, 5),
        ),
    );
    const after = await history(payment.id);
    assert.deepEqual(
      after.map((entry) => entry.eventId).sort(),
      before.map((entry) => entry.eventId).sort(),
    );
    assert.ok(after.every((entry) => entry.paymentId === payment.id));
    await call("/api/admin/settings", "PUT", { eventCopies: 1 });
  });

  await test("Rejected payment has its own Audit fact and cannot be confirmed", async () => {
    const payment = await create("CARD", 110000);
    const entries = await eventually(
      () => history(payment.id),
      (rows) => rows.length === 1,
    );
    assert.equal(entries[0].type, "PaymentRejected");
    assert.equal(entries[0].status, "REJECTED");
    assert.equal(entries[0].reason, payment.rejectionReason);
    await assert.rejects(call(`/api/payments/${payment.id}/confirm`, "POST"), {
      status: 409,
    });
  });

  await test("The payment list does not permanently hide records after the twentieth", async () => {
    const ids = [];
    for (let index = 0; index < 21; index++)
      ids.push(
        (await create("CARD", 100 + index, "Проверка полного списка")).id,
      );
    const visibleIds = new Set(
      (await call("/api/payments")).payments.map((payment) => payment.id),
    );
    assert.ok(
      ids.every((id) => visibleIds.has(id)),
      "Every created payment must remain accessible in the list",
    );
  });

  await test("Unknown saved check and invalid payment type return 404 and 400", async () => {
    await assert.rejects(call("/api/antifraud/checks/unknown-check"), {
      status: 404,
    });
    await assert.rejects(create("NOT_A_PAYMENT_TYPE"), { status: 400 });
  });
} finally {
  await mkdir(new URL("../.runtime/", import.meta.url), { recursive: true });
  await writeFile(
    new URL("../.runtime/console-smoke-results.json", import.meta.url),
    JSON.stringify({ at: new Date().toISOString(), results }, null, 2),
  );
}

console.log(`${results.length} console integration scenarios passed.`);
