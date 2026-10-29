# Code cleanup — implementation plan

> **For agentic workers:** use executing-plans; isolated HTTP fixes may run in parallel. No Git initialization, commits or publication without resolving the owner's branch preference.

**Goal:** readable teaching code matching Mini Payment System, preserving the project's existing scenarios.

**Architecture:** keep three Spring Boot services and their existing API/domain/database boundaries. Remove measurement infrastructure, historical schema changes and obsolete demo scaffolding; do not replace them with new frameworks.

**Tech stack:** existing Java 21, Spring Boot, Gradle, PostgreSQL, Redis, Kafka and React.

**Spec:** simplify persistence and HTTP code while preserving configurable scenarios.

## Constraints and pending scope choices

- Preserve create/read/list, business rejection vs HTTP503/timeout, N+1 vs JOIN, Redis stale vs eviction, Kafka duplicate vs idempotent consumer, service logs and Swagger.
- SQL for N+1 is proven by normal Hibernate logs, not a custom interceptor or duration counters.
- The owner permits resetting this project's disposable database if necessary. No unrelated Docker resources may be touched.
- No Liquibase currently exists: avoid adding it just to remove historical SQL.
- Owner confirmed: retain HTTP retry/breaker, lock variants and Kafka error demos as reserve.
- Owner confirmed: readable code variants with runtime switches, not real Git branches. Current folder has no Git; do not initialize it.

## Review focus

- Overlapping Antifraud requests must return one persisted decision, not a duplicate-key500.
- Timeout, network failure, malformed response and HTTP503 must remain distinguishable.
- Failed Redis cleanup must not silently apply settings or prevent a subsequent retry from clearing stale values.
- Removing SQL instrumentation must preserve real N+1 and JOIN SQL behavior and all UI/API consumers.
- Initial schema must work on an empty database without historical ALTER/backfill; normal startup must remain repeatable.

## Task1 — HTTP correctness and Antifraud idempotency

- [x] Add failing tests for concurrent same-ID checks and distinct HTTP failure causes.
- [x] Implement atomic persistence/readback of the decision and clear exception handling with original causes.
- [x] Run focused Java tests; all reserve runtime modes preserved.

## Task2 — simplify read paths and settings

- [x] Add regression for Redis-cleanup failure before settings become visible (observed RED→GREEN).
- [x] Fix settings application order without introducing a configuration framework.
- [x] Remove SqlCapture/DatabaseConfiguration and queryCount/queries/counters from DTOs, UI types and smoke consumers; preserve source for Redis demonstration (response-contract tests RED→GREEN).
- [x] Validate N+1/JOIN with standard Hibernate statistics in tests and ordinary SQL logs in integration checks.

## Task3 — initial schemas, reference readability and legacy

- [x] Collapse historical schema work to initial CREATE definitions; observed RED→GREEN for repeat startup not synthesizing marks.
- [x] Correct obsolete comments and retain useful transaction-boundary comments.
- [x] Showcase restores normal publication count and Audit protection; observed RED→GREEN.
- [x] Recoverably move old Maven target artifacts out of active modules; archive laboratory stub and old verification, update active documentation.

## Task4 — verify and hand off

- [x] Resolved optional-demo and branch choices: preserve reserve modes and runtime code variants.
- [x] Run make test (24 scripts +88 Java +55 UI), rebuild, full successful repeat of31 live smoke scenarios; additionally verify8 concurrent Antifraud calls on PostgreSQL.
- [x] Fresh read-only review: no Critical/Important; code entry points added to README.
- [x] Update owner intention memory; report no DB reset and verification limits in docs/verification.md.

## Execution record and rulings

- Non-Git checkout: no worktree/commit scripts or Git history were created. Source snapshot and recoverable archives retained instead of deleting the workspace.
- User resolved scope: reserve modes stay; runtime code variants replace any proposed real Git branches.
- Existing DB schema already matched current columns/constraints, so no destructive reset was needed. Fresh-schema automated checks use H2; existing PostgreSQL restart/live behavior verified separately.
- Parallel Gradle test runs collided in shared result files once; all final tests were rerun centrally and passed.
- First live smoke encountered a normal-mode900ms timeout before downstream handler started; no timeout setting changes. Full second run passed; both logs retained.
- Final review minor (deferred): explicit automated retry-after-Redis-recovery coverage; current implementation judged correct, failure leaves old settings for a repeat cleanup.
- Final review boundaries: parent verified build/tests/SQL logs/Redis/Kafka and PostgreSQL concurrent repeats. Fresh isolated PostgreSQL schema, manual browser rendering and live Redis outage/recovery were not exercised in this pass; these limits are explicit in verification.md.
