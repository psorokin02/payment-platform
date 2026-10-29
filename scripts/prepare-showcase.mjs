// Добавляет новую красивую выборку после тестов. Существующие платежи/аудит не удаляет.
const root = "http://127.0.0.1:5173";
async function call(path, method = "GET", body) {
  const r = await fetch(root + path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!r.ok) throw Error(await r.text());
  return r.json();
}
await call("/api/admin/settings", "PUT", {
  fetchMode: "join",
  cacheEnabled: true,
  cacheInvalidationEnabled: true,
  httpMode: "timeout",
  eventCopies: 1,
});
await call("/api/antifraud/admin/mode", "PUT", { mode: "normal" });
await call("/api/admin/circuit/reset", "POST");
await call("/api/audit/admin/resume", "POST");
await call("/api/audit/admin/settings", "PUT", { idempotencyEnabled: true });
await call("/api/admin/cache/clear", "POST");
const customers = await call("/api/customers");
const descriptions = [
  "Mechanical keyboard",
  "Cloud infrastructure",
  "Java Backend · подписка",
  "IDE лицензия",
  "Монитор 27 дюймов",
  "Server hosting",
  "Developer toolkit",
  "SSD накопитель",
  "Книга System Design",
  "Open source support",
  "Workshop ticket",
  "Кофе для команды",
];
const amounts = [
  12990, 2490, 150000, 8990, 34990, 100000, 6900, 150000, 3200, 110000, 120000,
  150000,
];
const paymentTypes = [
  "CARD",
  "QR",
  "INSTALLMENT",
  "RECURRENT",
  "CARD",
  "QR",
  "INSTALLMENT",
  "RECURRENT",
  "CARD",
  "QR",
  "CARD",
  "INSTALLMENT",
];
for (let i = 0; i < Math.min(12, customers.length); i++) {
  const p = await call("/api/payments", "POST", {
    customerId: customers[i].id,
    amount: amounts[i],
    description: descriptions[i],
    paymentType: paymentTypes[i],
  });
  await call(`/api/payments/${p.id}`);
  await call(`/api/payments/${p.id}`);
  if (p.status === "NEW" && [2, 5, 8, 11].includes(i))
    await call(`/api/payments/${p.id}/confirm`, "POST");
  console.log(p.reference, customers[i].name);
}
console.log("Новая демонстрационная выборка готова. Старые данные сохранены.");
