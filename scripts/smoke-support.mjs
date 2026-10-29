// Ошибка одного backend не отменяет попытку восстановить остальные.
export async function restoreAll(steps) {
  const failures = [];
  for (const [step, restore] of Object.entries(steps)) {
    try {
      await restore();
    } catch (error) {
      failures.push({ step, error });
    }
  }
  return failures;
}

export function auditProcessingComplete(entries, eventId, expectedReceipts) {
  const messages = entries.filter((entry) => entry.message.includes(eventId));
  const receipts = messages.filter((entry) =>
    entry.message.includes("Audit received"),
  ).length;
  const outcomes = messages.filter(
    (entry) =>
      entry.message.includes("Audit saved") ||
      entry.message.includes("Audit duplicate ignored"),
  ).length;
  return receipts === expectedReceipts && outcomes === expectedReceipts;
}
