import assert from "node:assert/strict";
import { mock, test } from "node:test";

test("showcase restores single publication and Audit protection before creating examples", async () => {
  const state = { eventCopies: 5, idempotencyEnabled: false };
  const fetchMock = mock.method(
    globalThis,
    "fetch",
    async (url, options = {}) => {
      const path = new URL(url).pathname;
      const body = options.body ? JSON.parse(options.body) : {};
      if (path === "/api/admin/settings") {
        if (body.eventCopies !== undefined)
          state.eventCopies = body.eventCopies;
      } else if (path === "/api/audit/admin/settings") {
        state.idempotencyEnabled = body.idempotencyEnabled;
      } else if (path === "/api/customers") {
        assert.equal(state.eventCopies, 1);
        assert.equal(state.idempotencyEnabled, true);
        return Response.json([]);
      } else if (
        ![
          "/api/antifraud/admin/mode",
          "/api/admin/circuit/reset",
          "/api/audit/admin/resume",
          "/api/admin/cache/clear",
        ].includes(path)
      ) {
        throw new Error(`Unexpected showcase request: ${path}`);
      }
      return Response.json({});
    },
  );

  try {
    await import("../prepare-showcase.mjs");
  } finally {
    fetchMock.mock.restore();
  }
});
