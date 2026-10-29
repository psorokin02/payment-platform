import assert from "node:assert/strict";
import { test } from "node:test";
import { restoreAll, auditProcessingComplete } from "../smoke-support.mjs";

test("restoration attempts every independent service even if another restore fails", async () => {
  const state = { protection: false, mode: "error", paused: true };
  const failures = await restoreAll({
    payment: async () => {
      throw new Error("Payment unavailable");
    },
    audit: async () => {
      state.protection = true;
    },
    antifraud: async () => {
      state.mode = "normal";
    },
    anotherFailure: async () => {
      throw new Error("Second failure");
    },
    consumer: async () => {
      state.paused = false;
    },
  });
  assert.deepEqual(state, { protection: true, mode: "normal", paused: false });
  assert.deepEqual(
    failures.map((failure) => failure.step),
    ["payment", "anotherFailure"],
  );
  assert.equal(failures[0].error.message, "Payment unavailable");
});

const log = (message) => ({ message });
const received = Array.from({ length: 5 }, () =>
  log("Audit received eventId=event-one"),
);

test("five received messages alone are not completed Audit processing", () => {
  const entries = [
    ...received,
    ...Array.from({ length: 4 }, () => log("Audit saved eventId=event-one")),
  ];
  assert.equal(auditProcessingComplete(entries, "event-one", 5), false);
});

test("processing waits for saved or ignored outcomes and scopes them to the eventId", () => {
  const done = [
    ...received,
    log("Audit saved eventId=event-one"),
    ...Array.from({ length: 4 }, () =>
      log("Audit duplicate ignored eventId=event-one"),
    ),
    log("Audit received eventId=other"),
    log("Audit saved eventId=other"),
  ];
  assert.equal(auditProcessingComplete(done, "event-one", 5), true);
  assert.equal(auditProcessingComplete(done, "absent", 5), false);
});
