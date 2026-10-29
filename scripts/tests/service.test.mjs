import assert from "node:assert/strict";
import { execFile, spawn } from "node:child_process";
import { promisify } from "node:util";
import {
  mkdtemp,
  mkdir,
  writeFile,
  readFile,
  copyFile,
  chmod,
  rm,
  realpath,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const execute = promisify(execFile);
const project = fileURLToPath(new URL("../../", import.meta.url));

// Реальные дочерние процессы и сигналы; JVM/сеть/сборка заменены только на внешней границе.
async function fixture(t) {
  const root = await realpath(await mkdtemp(join(tmpdir(), "payment-make-")));
  await mkdir(join(root, "scripts"), { recursive: true });
  await mkdir(join(root, ".runtime"));
  await mkdir(join(root, "bin"));

  for (const filename of [
    "scripts/service.sh",
    "scripts/start.sh",
    "Makefile",
  ]) {
    try {
      await copyFile(join(project, filename), join(root, filename));
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
  }

  async function file(path, contents, executable = false) {
    await mkdir(join(root, path, ".."), { recursive: true });
    await writeFile(join(root, path), contents);
    if (executable) await chmod(join(root, path), 0o755);
  }

  const background = `#!/bin/bash
trap 'exit 0' TERM INT
while :; do sleep 0.1; done
`;
  await file("bin/java", background, true);
  await file(
    "bin/node",
    `#!/bin/bash
case "$1" in *vite.js) exec /bin/bash "$PAYMENT_FIXTURE/bin/java" "$@";; esac
exec "$PAYMENT_REAL_NODE" "$@"
`,
    true,
  );
  await file(
    "bin/curl",
    `#!/bin/bash
case "$*" in
  *8081*) service=payment-service;; *8082*) service=antifraud-service;;
  *8083*) service=audit-service;; *5173*) service=ui;; *) exit 1;;
esac
pid=$(head -n 1 "$PAYMENT_FIXTURE/.runtime/$service.pid" 2>/dev/null)
kill -0 "$pid" 2>/dev/null || exit 7
printf '{"status":"UP","assigned":1}'
`,
    true,
  );
  await file(
    "bin/lsof",
    `#!/bin/bash
[ -f "$PAYMENT_FIXTURE/port-busy" ] && { printf '999999'; exit 0; }
exit 1
`,
    true,
  );
  await file(
    "bin/docker",
    `#!/bin/bash
touch "$PAYMENT_FIXTURE/docker-called"
printf '%s\\n' "$*" >> "$PAYMENT_FIXTURE/docker-args"
if [ -f "$PAYMENT_FIXTURE/allow-infra" ]; then
  if [ "$1" = info ] && [ -f "$PAYMENT_FIXTURE/fail-docker-info" ]; then exit 1; fi
  if [ "$1" = compose ] && [ -f "$PAYMENT_FIXTURE/fail-infra" ]; then exit 1; fi
  exit 0
fi
printf 'docker must not be called' >&2
exit 90
`,
    true,
  );
  await file(
    "gradlew",
    `#!/bin/bash
printf '%s\n' "$*" >> "$PAYMENT_FIXTURE/build-args"
if [ -f "$PAYMENT_FIXTURE/fail-build" ]; then exit 9; fi
for service in payment-service antifraud-service audit-service; do
  if [ -f "$PAYMENT_FIXTURE/.runtime/$service.pid" ]; then
    pid=$(head -n 1 "$PAYMENT_FIXTURE/.runtime/$service.pid")
    kill -0 "$pid" 2>/dev/null && printf '%s\n' "$service:$pid" >> "$PAYMENT_FIXTURE/alive-during-build"
  fi
done
exit 0
`,
    true,
  );
  await file(
    "bin/npm",
    '#!/bin/bash\n[ -f "$PAYMENT_FIXTURE/fail-ui-build" ] && exit 8\nexit 0\n',
    true,
  );
  for (const service of [
    "payment-service",
    "antifraud-service",
    "audit-service",
  ]) {
    await file(`${service}/build/libs/${service}-1.0.0.jar`, "test artifact");
  }
  await file("ui/node_modules/vite/bin/vite.js", "test artifact");
  const env = {
    ...process.env,
    PATH: `${join(root, "bin")}:${process.env.PATH}`,
    PAYMENT_JAVA: join(root, "bin/java"),
    PAYMENT_FIXTURE: root,
    PAYMENT_REAL_NODE: process.execPath,
  };

  const run = async (...args) => {
    try {
      return {
        code: 0,
        ...(await execute("bash", ["scripts/service.sh", ...args], {
          cwd: root,
          env,
        })),
      };
    } catch (error) {
      return {
        code: error.code,
        stdout: error.stdout ?? "",
        stderr: error.stderr ?? "",
      };
    }
  };
  const pid = async (service) =>
    Number(
      (await readFile(join(root, `.runtime/${service}.pid`), "utf8")).trim(),
    );
  t.after(async () => {
    for (const service of [
      "payment-service",
      "antifraud-service",
      "audit-service",
      "ui",
    ]) {
      try {
        const candidate = await pid(service);
        const { stdout } = await execute("ps", [
          "-p",
          String(candidate),
          "-o",
          "command=",
        ]);
        if (stdout.includes(root)) process.kill(candidate, "SIGTERM");
      } catch {}
    }
    await rm(root, { recursive: true, force: true });
  });
  return { root, run, pid, file, env };
}

test("start повторно использует свой PID и не запускает Docker", async (t) => {
  const f = await fixture(t);
  assert.equal((await f.run("start", "payment")).code, 0);
  const first = await f.pid("payment-service");
  assert.equal((await f.run("start", "payment")).code, 0);
  assert.equal(await f.pid("payment-service"), first);
  await assert.rejects(readFile(join(f.root, "docker-called")), {
    code: "ENOENT",
  });
});

test("Java запускается с небольшим heap, а старый управляемый процесс можно перезапустить", async (t) => {
  const f = await fixture(t);
  const jar = join(
    f.root,
    "payment-service/build/libs/payment-service-1.0.0.jar",
  );
  const previous = spawn(
    "bash",
    [join(f.root, "bin/java"), "-Xms96m", "-Xmx256m", "-jar", jar],
    { env: f.env, stdio: "ignore" },
  );
  await new Promise((resolve, reject) => {
    previous.once("spawn", resolve);
    previous.once("error", reject);
  });
  await f.file(".runtime/payment-service.pid", String(previous.pid));

  assert.equal((await f.run("restart", "payment")).code, 0);
  const current = await f.pid("payment-service");
  assert.notEqual(current, previous.pid);
  const { stdout } = await execute("ps", [
    "-ww",
    "-p",
    String(current),
    "-o",
    "command=",
  ]);
  assert.ok(stdout.trim().endsWith(` -Xms32m -Xmx192m -jar ${jar}`), stdout);
});

test("restart одного сервиса сохраняет PID другого", async (t) => {
  const f = await fixture(t);
  assert.equal((await f.run("start", "payment")).code, 0);
  assert.equal((await f.run("start", "antifraud")).code, 0);
  const paymentPid = await f.pid("payment-service");
  const antifraudPid = await f.pid("antifraud-service");
  assert.equal((await f.run("restart", "payment")).code, 0);
  assert.notEqual(await f.pid("payment-service"), paymentPid);
  assert.equal(await f.pid("antifraud-service"), antifraudPid);
});

test("ошибка сборки не останавливает работающий сервис", async (t) => {
  const f = await fixture(t);
  assert.equal((await f.run("start", "payment")).code, 0);
  const previous = await f.pid("payment-service");
  await f.file("fail-build", "");
  assert.notEqual((await f.run("rebuild", "payment")).code, 0);
  assert.equal(await f.pid("payment-service"), previous);
  assert.doesNotThrow(() => process.kill(previous, 0));
});

test("rebuild собирает выбранный модуль до остановки старого PID", async (t) => {
  const f = await fixture(t);
  assert.equal((await f.run("start", "payment")).code, 0);
  const previous = await f.pid("payment-service");
  assert.equal((await f.run("rebuild", "payment")).code, 0);
  assert.notEqual(await f.pid("payment-service"), previous);
  assert.match(
    await readFile(join(f.root, "build-args"), "utf8"),
    /:payment-service:build/,
  );
  assert.match(
    await readFile(join(f.root, "alive-during-build"), "utf8"),
    new RegExp(`payment-service:${previous}`),
  );
});

test("чужой живой PID не останавливается", async (t) => {
  const f = await fixture(t);
  await f.file(".runtime/payment-service.pid", String(process.pid));
  assert.equal((await f.run("stop", "payment")).code, 1);
  assert.doesNotThrow(() => process.kill(process.pid, 0));
});

test("занятый порт не приводит к запуску второго процесса", async (t) => {
  const f = await fixture(t);
  await f.file("port-busy", "");
  assert.equal((await f.run("start", "ui")).code, 1);
  await assert.rejects(f.pid("ui"), { code: "ENOENT" });
});

test("UI перезапускается отдельно от Java", async (t) => {
  const f = await fixture(t);
  assert.equal((await f.run("start", "payment")).code, 0);
  assert.equal((await f.run("start", "ui")).code, 0);
  const paymentPid = await f.pid("payment-service");
  const uiPid = await f.pid("ui");
  assert.equal((await f.run("restart", "ui")).code, 0);
  assert.notEqual(await f.pid("ui"), uiPid);
  assert.equal(await f.pid("payment-service"), paymentPid);
});

test("неизвестное имя не превращается в путь или команду", async (t) => {
  const f = await fixture(t);
  assert.equal((await f.run("start", "../../other")).code, 1);
});

test("Make-алиас выполняет тот же контролируемый restart", async (t) => {
  const f = await fixture(t);
  assert.equal((await f.run("start", "payment")).code, 0);
  const previous = await f.pid("payment-service");
  await execute("make", ["restart-payment"], { cwd: f.root, env: f.env });
  assert.notEqual(await f.pid("payment-service"), previous);
});

test("общий rebuild заменяет Java и UI, не вызывая Docker", async (t) => {
  const f = await fixture(t);
  assert.equal((await f.run("start", "all")).code, 0);
  const names = ["payment-service", "antifraud-service", "audit-service", "ui"];
  const before = await Promise.all(names.map(f.pid));
  assert.equal((await f.run("rebuild", "all")).code, 0);
  const after = await Promise.all(names.map(f.pid));
  names.forEach((name, index) =>
    assert.notEqual(after[index], before[index], name),
  );
  await assert.rejects(readFile(join(f.root, "docker-called")), {
    code: "ENOENT",
  });
});

test("ошибка frontend build сохраняет все работающие процессы", async (t) => {
  const f = await fixture(t);
  assert.equal((await f.run("start", "all")).code, 0);
  const names = ["payment-service", "antifraud-service", "audit-service", "ui"];
  const before = await Promise.all(names.map(f.pid));
  await f.file("fail-ui-build", "");
  assert.notEqual((await f.run("rebuild", "all")).code, 0);
  assert.deepEqual(await Promise.all(names.map(f.pid)), before);
  before.forEach((pid) => assert.doesNotThrow(() => process.kill(pid, 0)));
});

for (const variant of ["jar-with-suffix", "path-as-text"]) {
  test(`похожий путь не разрешает остановить чужой процесс: ${variant}`, async (t) => {
    const f = await fixture(t);
    const jar = join(
      f.root,
      "payment-service/build/libs/payment-service-1.0.0.jar",
    );
    const args =
      variant === "jar-with-suffix"
        ? ["-Xms96m", "-Xmx256m", "-jar", `${jar}.old`]
        : ["--reference", jar];
    const foreign = spawn("bash", [join(f.root, "bin/java"), ...args], {
      env: f.env,
      stdio: "ignore",
    });
    await new Promise((resolve, reject) => {
      foreign.once("spawn", resolve);
      foreign.once("error", reject);
    });
    await f.file(".runtime/payment-service.pid", String(foreign.pid));
    assert.equal((await f.run("stop", "payment")).code, 1);
    assert.doesNotThrow(() => process.kill(foreign.pid, 0));
  });
}

test("make up поднимает инфраструктуру перед приложениями", async (t) => {
  const f = await fixture(t);
  await f.file("allow-infra", "");
  await f.file("docker-args", "");
  await execute("make", ["up"], { cwd: f.root, env: f.env });
  assert.match(
    await readFile(join(f.root, "docker-args"), "utf8"),
    /compose up -d --wait/,
  );
  for (const service of [
    "payment-service",
    "antifraud-service",
    "audit-service",
    "ui",
  ]) {
    const pid = await f.pid(service);
    assert.ok(pid > 1);
    assert.doesNotThrow(() => process.kill(pid, 0));
  }
});

test("ошибка Compose останавливает make up до запуска Java", async (t) => {
  const f = await fixture(t);
  await f.file("allow-infra", "");
  await f.file("fail-infra", "");
  await assert.rejects(execute("make", ["up"], { cwd: f.root, env: f.env }));
  assert.match(
    await readFile(join(f.root, "docker-args"), "utf8"),
    /compose up -d --wait/,
  );
  await assert.rejects(f.pid("payment-service"), { code: "ENOENT" });
});

test("выключенный Docker останавливает make up до запуска Compose и Java", async (t) => {
  const f = await fixture(t);
  await f.file("allow-infra", "");
  await f.file("fail-docker-info", "");
  await assert.rejects(execute("make", ["up"], { cwd: f.root, env: f.env }));
  assert.doesNotMatch(
    await readFile(join(f.root, "docker-args"), "utf8"),
    /compose/,
  );
  await assert.rejects(f.pid("payment-service"), { code: "ENOENT" });
});

for (const flag of ["--offline", "--ephemeral", "--foreground", "--no-build"]) {
  test(`start.sh не принимает удалённый флаг ${flag}`, async (t) => {
    const f = await fixture(t);
    await assert.rejects(
      execute("bash", ["scripts/start.sh", flag], { cwd: f.root, env: f.env }),
      { code: 2 },
    );
    await assert.rejects(readFile(join(f.root, "docker-called")), {
      code: "ENOENT",
    });
  });
}
