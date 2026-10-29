# Live UI and enums — implementation plan

> Implementation: browser verification and TDD; independent UI and Java changes may run in parallel under dispatching-parallel-agents. No Git initialization, commits, infrastructure cleanup or extra feature removal.

**Goal:** implement Pavel's explicit corrections and test the real five-tab console.

**Spec:** current request: dark Swagger; left payment drawer; remove PAYMENT CONSOLE / 01; real refresh feedback; aligned audit; typed statuses/reasons; remove Timeline/Observability and the private DemoPayment helper. Additional cuts are proposals only.

**Architecture:** preserve REST/Kafka/DB string values. PaymentStatus and AntifraudReason live in Common Libs and are serialized as strings. Remove technical timeline plumbing, retain SQL inspection and counters used by N+1/load scripts. Keep the real Swagger and all demo controls.

## Constraints and review focus

- No deletes of databases, data, volumes, demo modes, seed customers or event history.
- Existing string status/reason values and old Kafka events with nullable fields remain readable.
- A failed refresh must not display false success; overlapping requests must remain serialized.
- Audit rows must align even when a historical event has no status or long text.
- Swagger Execute must work for all three services after theming; JSON remains readable.
- A drawer must stay left on desktop and fit a narrow viewport.

## Task 1 — Java cleanup

- [x] Tests: string enum JSON/DB mapping; business transitions and rejection decisions; historical nullable events.
- [x] Introduce PaymentStatus / AntifraudReason through DTOs, persistence and events without wire changes.
- [x] Remove Timeline, TimelineEntryDto, timeline beans/injections/calls and unused diagnostic events fields.
- [x] Move retained SQL inspection to database/config packages with clear names.
- [x] Replace the private DemoPayment record, retaining the initial sample data.
- [x] Run the full Java tests; report additional simplification candidates without implementing them.

## Task 2 — UI corrections

- [x] Reproduce refresh/audit behaviour, add failing regression tests at real seams.
- [x] Left drawer; remove the header decoration.
- [x] Refresh waits for actual requests, shows progress and truthful completion/failure.
- [x] Format Audit as aligned semantic table, keep opening a payment.
- [x] Run UI tests/build; preserve the five tabs and payment card actions.

## Task 3 — Swagger and live acceptance

- [x] Theme the actual Swagger with minimal maintainable customization, no fake API explorer.
- [x] Browser: create an allowed boundary payment and a rejected QR payment; inspect JSON, confirm and audit.
- [x] Browser: send real requests from each service's Swagger and verify responses.
- [x] Browser: test refresh, drawer side and audit alignment; save screenshots.
- [x] Run make test, make build, make smoke; restart only this project's processes.
- [x] Write the report: observed bugs, implemented fixes, untouched suggestions and test evidence.

## Approved extension — latest owner message

- [x] Services: editable Payment загрузка данных, кэш, инвалидация и HTTP/eventCopies settings and existing Antifraud/Audit controls; saved settings must be read back.
- [x] N+1: explain the existing Payment list → LAZY Customer path; demonstrate nplus1 versus join with actual Hibernate SQL logs.
- [x] Kafka: eventCopies=1..5, same eventId for every copy; log receipt, one saved effect and ignored duplicates in Audit.
- [x] Logs: bounded per-service Logback capture, GET /api/admin/logs or /admin/logs, real SLF4J output in Services with all/per-service filter. No arbitrary file reads or new log infrastructure.
- [x] Remove read/list durationMs timing; keep minimal SQL list/count used by existing N+1/Redis verification.
- [x] Cache: demonstrate off/stale/safe with actual DB-new/cache-old contrast and invalidation after commit.

Acceptance boundaries: live geometry/alignment checked at viewport996; narrow overflow is CSS/SSR-covered, not a separate narrow live run. Real Swagger Execute passed in same-IAB standalone pages for all3 services; CUA actions inside the cross-origin iframe are unavailable and explicitly reported.
