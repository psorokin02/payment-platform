# Cache invalidation and fresh demo environment

Owner directly authorized the cache refactor, removal of Audit counters and a full rebuild/reset of this teaching project's environment. Execute inline, with one final read-only review. No Git repository: snapshot current sources instead of reverting or creating branches.

## Design

- Replace cacheMode off/stale/safe with independent Boolean settings cacheEnabled and cacheInvalidationEnabled, both true by default. PUT fields remain nullable for partial updates.
- PaymentService invokes PaymentCache.invalidatePaymentCache only after its commit. Cache component owns the invalidation flag check, Redis deletion and logging.
- Remove Audit deliveries/duplicates counters and status fields; retain duplicate logs, idempotency switch and DB journal.
- Follow-up: remove Antifraud calls counter/status field and response attempt. Verify retries through logs and controlled test servers, not application counters.
- Preserve owner's current processPaymentEvent method and early return when idempotency is off. Off now writes no journal mark; first delivery after enabling can add one fact for an unmarked ID. Align stale tests/docs, do not restore the former behavior.
- Explain requestId from current source: generated once in Payment client, retained across retry. Antifraud fallback serves direct calls without an ID; do not change this API merely because the owner asked why it exists.
- Reset only this Compose project's three containers and volumes (PostgreSQL/Redis/Kafka); all their demo data and offsets are disposable. Back up source/SQL, stop local applications, recreate infrastructure, rebuild/restart services/UI, verify and then create a clean showcase dataset.
- Keep resource limits, owner's Lombok/style changes and unrelated code. Snapshot: .runtime/cache-reset-before/source.tgz.

## Checklist

- [x] Test new cache flags/defaults, invalidation boundary and removed counter API.
- [x] Implement backend, UI and script/config contract changes; preserve owner's domain edits.
- [x] Full tests/build and final review. Review flag partial updates, failed Redis cleanup and post-commit ordering.
- [x] Back up and reset project Compose volumes; fresh startup and live scenarios.
- [x] Recreate showcase after tests; verify statuses, Redis, Kafka history and settings.
- [x] Update current docs and owner intention memory; report requestId behavior and final runtime.

## Ledger

- Inspection verified owner changes: Lombok in PaymentSettings, AuditService.processPaymentEvent with early bypass of journal when disabled, listener formatting/renaming, parenthesized requestId fallback in Antifraud. Preserve these changes.
- RED: cache flag/default test failed against the old settings. GREEN: 175 tests (97 Java, 53 UI, 25 scripts); complete build and 28 live scenarios pass. Redis deletion gate, after-commit invocation and independent partial flag updates covered.
- Final review: no Critical/Important. Minor wording about old cache modes corrected during planned report update. RequestId fallback applies to null or blank. Reviewer deferred actual runtime/reset/browser to parent; parent verified reset/live/API/data, browser not rerun.
- Owner edited AntifraudClient during work (formatting and status >=500). Preserved and rebuilt Payment again; module tests GREEN. AuditService remains byte-identical to source snapshot; Antifraud formatting edits preserved with only requested calls removal.
- SQL backup 281288 bytes and source archive retained privately. Only project's three volumes/containers deleted and recreated; Docker Desktop/global resources untouched. Fresh applications started successfully.
- Final dataset: 24 payments, 12 Antifraud decisions, 16 Audit facts/marks, statuses NEW18/CONFIRMED4/REJECTED2; Kafka offset16 lag0. Actual response lacks attempt; status APIs lack calls/deliveries/duplicates. Cache HIT verified. No pending code work.
