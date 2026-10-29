# Simpler publisher and Antifraud implementation plan

> Execute inline with superpowers:executing-plans; the owner's explicit scoped request authorizes these reversible edits. No Git repository is present: retain a source snapshot and this ledger, do not create Git/worktrees/commits. One final fresh read-only review.

**Goal:** make event publication and Antifraud readable for the project.

**Spec:** owner's current request: extract Kafka single-copy publication; remove method Swagger annotations from AntifraudController; remove transient recovery; use explicit early business decisions; move the slow simulation to the controller, wait one minute and return 503.

**Architecture:** publisher serializes once, loops over copies and delegates one send. Controller owns HTTP faults; domain service owns validation, idempotency and approved/rejected decisions. Existing shared response fields and database schemas stay unchanged.

## Constraints and review focus

- Keep asynchronous Kafka ACK handling; synchronous send failure must not skip later copies. Copies retain one eventId/key/payload.
- Supported Antifraud modes: normal/reject/error/slow. Slow defaults to 60000 ms in YAML, then returns 503 without persisting a check. Normal/reject have no sleep. A request counter is diagnostic only.
- Preserve risk limit boundaries, saved-decision idempotency, concurrent insert conflict behavior and existing response DTO fields.
- Remove transient from API validation, UI types/options/status text and live scripts. Keep Payment retry/breaker as previously authorized reserve.
- Swagger continues to expose real endpoints without per-method examples.
- No DB, Docker, memory-limit or unrelated source changes. Rebuild/restart only changed services, refresh UI through Vite.

## Tasks

- [x] Verify publisher baseline tests; extract sendCopy and completion logging, keeping loop one line.
- [x] Write RED checks for rejected transient mode, slow returning 503 without a saved decision, and correct UI availability even with retry. Use a short injected YAML delay only in controller tests.
- [x] Separate controller faults from domain decisions; simplify approve/reject early returns, remove service delays and method Swagger annotations. Keep real HTTP request counting in controller. Align concurrency test synchronization with business decision evaluation.
- [x] Update UI/settings/scripts/docs and run targeted tests, then full make test/build.
- [x] Restart changed services, verify actual one-minute delayed 503 and normal/risk/reject/error behavior, Kafka copies/idempotency and OpenAPI.
- [x] Fresh review; record results, update owner intention memory and report.

## Approved scope extension: Hibernate and Audit cleanup

- [x] Remove Audit error injection, retry/DLT configuration/counters/admin routes/UI/script calls. Use CommonLoggingErrorHandler explicitly so framework defaults do not silently retry. Errors are logged and skipped; broker offsets and idempotency journal still have their usual restart/duplicate semantics.
- [x] Replace Audit/Antifraud JdbcTemplate adapters with Hibernate entities and Spring Data repositories. Audit history uses @Query; processed-event and Antifraud insert-if-absent use native @Query ON CONFLICT DO NOTHING. Audit fact + marker remain one transaction.
- [x] Keep existing SQL schemas/data and application RAM limits. Test real JPA transactions, mapping, rollback and concurrent duplicates, then full build/live checks. Remove only the obsolete project DLT topic after inspecting broker topics; retain the main event topic and history.

## Ledger

- Snapshot: `.runtime/simpler-faults-before/source.tgz`.
- Pre-flight: controller owns mode faults and request counting, service retains only normal/reject decisions. UI reads slowDelayMs from status, so it must always describe eventual 503; a longer client timeout cannot make this mode available.
- First full test/build exposed a concurrently edited Audit listener with missing retryingEvents and an unresolved Lombok annotation. The owner's subsequent request removes this exact retry machinery; rewrite that listener as scoped cleanup rather than restoring it.
- RED: slow returned 200 and transient was accepted (2 Java failures); 5 UI checks reproduced old options/availability. Publisher baseline 3 tests passed.
- Design uses codebase-design at the repository interface: SQL and idempotent inserts stay inside repositories; no parallel JDBC adapter or extra persistence service.
- Hibernate service tests GREEN: real JPA transactions preserve marker rollback, concurrent duplicates, saved Antifraud decisions, enums and full per-payment history. Initial full suite/build GREEN, 170 tests.
- Real slow request: 60066ms, HTTP503, no saved check; health/admin responsive during sleep. Both obsolete DLT topics removed explicitly; main event history retained.
- Live restart found a missed Payment DLT NewTopic bean after its YAML property was removed. KafkaConfigurationTest reproduced the missing-property startup failure RED; obsolete bean removed and full suite GREEN (171 tests). First live smoke could not start while Payment was down; not counted as success.
- Same startup exposed a preexisting malformed ALTER TYPE caused by DEFAULT embedded in columnDefinition. Separate ColumnDefault preserves CARD default and varchar(32) while removing invalid PostgreSQL DDL; fresh build/startup verification follows.
- Fresh source review: no Critical/Important. One documentation note about historical test results is addressed by the already planned report rewrite. Parent owns live results; existing Audit/Antifraud schema.sql verified byte-identical to pre-Hibernate snapshot. Browser rendering not exercised; old unrelated payment transaction/outbox tradeoffs remain documented.

- Final verification: 171 tests (25 scripts, 93 Java, 53 UI) and complete build GREEN. 28 live smoke scenarios GREEN, plus actual 60066ms delayed 503 and targeted PostgreSQL native-query concurrency/rollback checks.
- Poison event received once, marker rolled back, no retry, next event saved; Kafka audit lag=0. Existing SQL schemas byte-identical. Runtime resource limits and CARD SQL default verified. Removed modes/endpoints absent from Swagger/API.
- Final presets restored: normal/join/safe/timeout/1, protection ON and consumer resumed. HTTP retry/breaker retained only in Payment. Browser rendering not rerun; source/UI tests and actual Swagger assets checked.
- Documentation and owner intention memory updated. No pending source work, no unrelated deletions or deployments.
