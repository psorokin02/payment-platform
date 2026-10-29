# Memory Limits Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. The owner approved the values and Docker/IDEA restarts with «погнали». No repeated approval is needed within this scope.

**Goal:** Reduce memory consumption of the local development environment and IntelliJ IDEA while preserving the demos.

**Architecture:** Apply ordinary resource limits in Compose, the process launcher and Gradle. Limit the Docker VM separately; preserve all volumes and unrelated application settings.

**Tech Stack:** Docker Desktop on macOS, Kafka 3.9.1, PostgreSQL 17, Redis 8.4, Java 21, Gradle, Node/Vite, IntelliJ IDEA.

**Spec:** Approved chat table: Docker VM 2048 MiB; Kafka container 512 MiB / heap 256 MiB; PostgreSQL 256 MiB; Redis 128 MiB / data 64 MiB; each Java service heap 192 MiB; frontend V8 old space 256 MiB; Gradle heap 384 MiB / one worker / no persistent daemon; IDEA heap 1536 MiB.

## Global Constraints

- Keep data and volumes. No cleanup, image deletion, RAM profile or offline mode.
- Back up changed project and application configuration before applying limits.
- This is a non-Git folder: no Git initialization, worktrees, commits or publishing.
- Preserve PID ownership checks when changing JVM/Node launch options.
- Fix only the observed missing getPessimisticLock() call needed to rebuild existing code; retain both locking modes.
- JVM and V8 limits are heap limits, not hard limits on total process memory.

## Review Focus

- A currently running process with old launch flags must still be safely stoppable.
- A similarly named foreign JAR must never pass ownership checks.
- Kafka health-check JVM must fit within the broker's container budget.
- IDEA uses an explicitly configured VM options file; verify the running JVM, not only a default file.
- A failed build or restart must not lose database data or leave the owner without a recovery path.

## Tasks and ledger

- [x] Snapshot configuration into `.runtime/memory-limits-2026-10-29-before` (private directory).
- [x] Add a launcher regression test for restarting an owned process started with old JVM flags; observe failure before changing launcher.
- [x] Configure Compose limits, Kafka heap/health check and PostgreSQL buffers.
- [x] Configure Java/Node launcher and Gradle limits; resolve the known compilation error.
- [x] Run script tests and full build with reduced limits before stopping existing services.
- [x] Stop the project safely, apply Docker VM and IDEA heap settings, restart approved applications and the project.
- [x] Verify effective VM/container/heap limits, health, smoke demos and actual memory footprint; document results and rollback instructions.
- [x] Fresh read-only review of the configuration changes (no findings); owner intention memory updated, report in docs/memory-limits.md.

## Rulings

- Configuration-only changes use effective-runtime checks rather than tests that repeat configuration literals. The launcher safety behavior has a focused regression test.
- Current root directory has no Git; progress stays in this file and backups are retained.
- Launcher ownership still accepts the exact previous JVM invocation, solely for a safe first restart; foreign JAR suffix tests remain green.
- Test evidence: launcher RED then all 25 script tests GREEN; `make build` passed with Gradle 384 MiB / Java tests 256 MiB / frontend 256 MiB (`.runtime/memory-build.log`).
- IDEA Change Memory Settings did not open a dialog. The actual VM options file was backed up and its sole `-Xmx4096m` line changed to `-Xmx1536m`; other options were preserved.
- Global effective limits confirmed after restart: Docker `MemTotal=2012721152` bytes (2 GiB VM less guest overhead), IDEA `MaxHeapSize=1610612736` bytes (1536 MiB).
- Container inspection confirmed exact hard memory/swap limits 512/256/128 MiB, all healthy, no OOM kill. Existing volumes retain creation date 2026-10-26; pre-smoke DB has 349 payments and 416 Audit entries.
- Initial cold start was slow (Payment 84.573 s, Vite 10.767 s). First smoke timed out at startup request before any scenario completed. No timeout was increased.
- The old Docker Dashboard remained busy after its backend was restarted. Graceful termination of that UI stopped the engine but left the UI running; that exact verified owner process was force-closed, and Docker Desktop was relaunched from its original Desktop copy. A smoke attempted during this controlled restart also timed out. Final recovery and smoke results remain pending.
- Fresh read-only review: 0 Critical, 0 Important, 0 Minor. Reviewer left sustained-budget behavior and effective app settings for parent runtime verification; static review does not replace those checks. No deliberate OOM/exhaustion or unrelated whole-directory audit is claimed.
- All 88 Java tests explicitly rerun with 256 MiB test heaps: GREEN in 1m18s (`.runtime/memory-java-tests.log`); UI 55/55 GREEN; scripts 25/25 GREEN. No compilation/test work overlaps the final smoke.
- Final recovered-runtime smoke: 31/31 GREEN, no timeout increase; all normal presets restored. VM footprint 1.8G; all containers healthy/OOMKilled=false after the run.
- IDEA PID 43158 later exited: system crash report records EXC_BAD_ACCESS / SIGABRT at 12:08, with no explicit Java heap space. Cause is not established; no inference that heap caused it. Relaunched with the approved 1536 MiB value. No IDE runtime update, plugin change or cache deletion is authorized/performed.
- Relaunched IDEA PID 47840 reopened the project, effective MaxHeapSize=1610612736; footprint 3.2G. All 31 smoke scenarios passed again with IDEA open (`.runtime/memory-smoke-with-idea.log`). Long-term IDE stability is not established by that short run.
- Two idle Gradle 8.14.5 daemons were still resident (old 512 MiB / new 384 MiB); stopped normally with `./gradlew --stop`. Added a 60-second idle expiry for IDEA Tooling API daemons, which can ignore `daemon=false`; CLI builds still use a single-use daemon.
- Final Gradle `help --info`: successful in 6s, launched with heap 384 MiB, single-use daemon stops at command completion; its own log confirms idleTimeout=60000. IDEA PID 47840 still alive at final check. Configuration work complete; long-term IDE stability remains explicitly unverified.
