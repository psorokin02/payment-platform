const presets = {
  normal: {
    fetchMode: "join",
    cacheEnabled: true,
    cacheInvalidationEnabled: true,
    httpMode: "timeout",
    eventCopies: 1,
  },
  "hibernate-broken": { fetchMode: "nplus1" },
  "hibernate-fixed": { fetchMode: "join" },
  "redis-broken": { cacheEnabled: true, cacheInvalidationEnabled: false },
  "redis-fixed": { cacheEnabled: true, cacheInvalidationEnabled: true },
  "http-timeout": { httpMode: "timeout" },
  "http-retry": { httpMode: "retry" },
  "http-breaker": { httpMode: "breaker" },
};
const preset = process.argv[2] || "normal";
async function call(port, path, method = "POST", body) {
  const response = await fetch(`http://127.0.0.1:${port}${path}`, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw Error(await response.text());
  return response.json();
}
if (preset === "kafka-paused" || preset === "kafka-active")
  console.log(
    await call(
      8083,
      `/admin/${preset === "kafka-paused" ? "pause" : "resume"}`,
    ),
  );
else if (presets[preset]) {
  console.log(await call(8081, "/api/admin/settings", "PUT", presets[preset]));
  if (preset === "normal") {
    await call(8082, "/admin/mode", "PUT", { mode: "normal" });
    await call(8081, "/api/admin/circuit/reset");
    await call(8081, "/api/admin/cache/clear");
    await call(8083, "/admin/resume");
    await call(8083, "/admin/settings", "PUT", { idempotencyEnabled: true });
  }
} else {
  console.error(
    "Сценарии:",
    Object.keys(presets).concat(["kafka-paused", "kafka-active"]).join(", "),
  );
  process.exitCode = 2;
}
