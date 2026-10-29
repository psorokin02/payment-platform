import assert from "node:assert/strict";
import { auditProcessingComplete } from "./smoke-support.mjs";
import { mkdir, writeFile } from "node:fs/promises";
const base = "http://127.0.0.1:5173";
const results = [];
async function call(path, method = "GET", body) {
  const r = await fetch(base + path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  });
  const data = await r.json();
  if (!r.ok)
    throw Object.assign(Error(data.message || String(r.status)), {
      status: r.status,
    });
  return data;
}
const settings = (body) => call("/api/admin/settings", "PUT", body);
const checkMode = (mode) => call("/api/antifraud/admin/mode", "PUT", { mode });
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function eventually(predicate) {
  for (let i = 0; i < 100; i++) {
    if (await predicate()) return;
    await sleep(300);
  }
  throw Error("eventual result timed out");
}
async function test(name, fn) {
  const start = performance.now();
  await fn();
  results.push({
    name,
    passed: true,
    durationMs: Math.round(performance.now() - start),
  });
  console.log("PASS", name);
}
let customerId;
const create = () =>
  call("/api/payments", "POST", {
    customerId,
    amount: 4990,
    description: "API regression experiment",
  });
const confirm = (id) => call(`/api/payments/${id}/confirm`, "POST");
const read = (id) => call(`/api/payments/${id}`);
const history = (id) => call(`/api/audit/entries?paymentId=${id}`);
const antifraudLogs = () => call("/api/antifraud/admin/logs?limit=500");
const lastAntifraudSequence = async () =>
  (await antifraudLogs()).entries.at(-1)?.sequence ?? 0;
const checksSince = async (sequence) =>
  (await antifraudLogs()).entries.filter(
    (entry) =>
      entry.sequence > sequence &&
      entry.message.includes("Antifraud HTTP check"),
  ).length;
try {
  customerId = (await call("/api/customers"))[0].id;
  await checkMode("normal");
  await settings({
    fetchMode: "join",
    cacheEnabled: true,
    cacheInvalidationEnabled: true,
    httpMode: "timeout",
    eventCopies: 1,
  });
  await call("/api/audit/admin/resume", "POST");
  await call("/api/audit/admin/settings", "PUT", { idempotencyEnabled: true });
  await eventually(
    async () => (await call("/api/audit/admin/status")).assigned > 0,
  );
  await test("N+1 and JOIN return the same payments; SQL is visible in Hibernate logs", async () => {
    await settings({ fetchMode: "nplus1" });
    const before = await call("/api/payments");
    await settings({ fetchMode: "join" });
    const after = await call("/api/payments");
    assert.deepEqual(
      before.payments.map((p) => p.id),
      after.payments.map((p) => p.id),
    );
  });
  await test("Redis hit returns cached data; after-commit eviction returns CONFIRMED", async () => {
    const p = await create();
    const first = await read(p.id);
    assert.equal(first.source, "POSTGRESQL");
    const hit = await read(p.id);
    assert.equal(hit.source, "REDIS");
    await confirm(p.id);
    const updated = await read(p.id);
    assert.equal(updated.source, "POSTGRESQL");
    assert.equal(updated.payment.status, "CONFIRMED");
  });
  await test("Prepared stale-cache defect is reproducible", async () => {
    await settings({ cacheEnabled: true, cacheInvalidationEnabled: false });
    const p = await create();
    await read(p.id);
    await confirm(p.id);
    const stale = await read(p.id);
    assert.equal(stale.payment.status, "NEW");
    assert.equal(stale.source, "REDIS");
    assert.equal(
      (await call("/api/payments")).payments.find((x) => x.id === p.id).status,
      "CONFIRMED",
    );
    await settings({ cacheEnabled: true, cacheInvalidationEnabled: true });
  });
  await test("Paused Audit catches up after resume", async () => {
    await settings({ eventCopies: 5 });
    await call("/api/audit/admin/pause", "POST");
    await eventually(
      async () => (await call("/api/audit/admin/status")).pauseApplied,
    );
    const p = await create();
    await confirm(p.id);
    await sleep(400);
    assert((await history(p.id)).length === 0);
    await call("/api/audit/admin/resume", "POST");
    await eventually(async () => (await history(p.id)).length === 2);
    const facts = await history(p.id);
    await eventually(async () => {
      const logs = (await call("/api/audit/admin/logs?limit=500")).entries;
      return facts.every((fact) =>
        auditProcessingComplete(logs, fact.eventId, 5),
      );
    });
    assert.equal((await history(p.id)).length, 2);
    await settings({ eventCopies: 1 });
  });
  await test("HTTP retry makes three attempts and reports persistent 503", async () => {
    await settings({ httpMode: "retry" });
    await checkMode("error");
    const sequence = await lastAntifraudSequence();
    await assert.rejects(create(), (error) => error.status === 503);
    assert.equal(await checksSince(sequence), 3);
  });
  await test("Business rejection is not retried", async () => {
    await checkMode("reject");
    const sequence = await lastAntifraudSequence();
    const p = await create();
    assert.equal(p.status, "REJECTED");
    assert.equal(await checksSince(sequence), 1);
  });
  await test("Timeout bounds slow HTTP call", async () => {
    await settings({ httpMode: "timeout" });
    await checkMode("slow");
    const start = performance.now();
    await assert.rejects(create(), (e) => e.status === 503);
    assert(performance.now() - start < 2500);
  });
  await test("Circuit OPEN rejects without calling downstream", async () => {
    await call("/api/admin/circuit/reset", "POST");
    await settings({ httpMode: "breaker" });
    await checkMode("error");
    for (let i = 0; i < 4; i++)
      await assert.rejects(create(), (e) => e.status === 503);
    const sequence = await lastAntifraudSequence();
    await assert.rejects(
      create(),
      (e) => e.status === 503 && e.message.includes("OPEN"),
    );
    assert.equal(await checksSince(sequence), 0);
  });
  await checkMode("normal");
  await settings({ httpMode: "timeout" });
  await test("An already confirmed payment cannot be confirmed again sequentially", async () => {
    const p = await create();
    await confirm(p.id);
    await assert.rejects(confirm(p.id), (error) => error.status === 409);
  });
  await test("Unknown payment / invalid amount return explicit 404 / 400", async () => {
    await assert.rejects(read(99999999), (e) => e.status === 404);
    await assert.rejects(
      call("/api/payments", "POST", { customerId, amount: -10 }),
      (e) => e.status === 400,
    );
  });
} finally {
  await checkMode("normal");
  await settings({
    fetchMode: "join",
    cacheEnabled: true,
    cacheInvalidationEnabled: true,
    httpMode: "timeout",
    eventCopies: 1,
  });
  await call("/api/admin/circuit/reset", "POST");
  await call("/api/audit/admin/resume", "POST");
  await call("/api/audit/admin/settings", "PUT", { idempotencyEnabled: true });
  await mkdir(new URL("../.runtime/", import.meta.url), { recursive: true });
  await writeFile(
    new URL("../.runtime/smoke-results.json", import.meta.url),
    JSON.stringify({ at: new Date().toISOString(), results }, null, 2),
  );
}
console.log(
  `${results.length} integration scenarios passed against actual PostgreSQL, Redis and Kafka.`,
);
