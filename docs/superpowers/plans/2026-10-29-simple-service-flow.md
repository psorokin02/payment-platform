# Simpler service flow

> Execute inline with superpowers:executing-plans. Owner directly requested all three changes: ordinary KafkaTemplate publication without local Spring listeners; remove optimistic/pessimistic locking; consolidate administrative code under admin in every service. Do not add Outbox or another event framework.

## Design and constraints

- PaymentService explicitly completes its database transaction, then invalidates Redis when needed and calls the small KafkaTemplate-based PaymentEventPublisher directly.
- Use TransactionTemplate for the write transaction so the call order is visible in one class. Antifraud HTTP precedes the write transaction. A service write owns its commit (REQUIRES_NEW); it does not join a caller's larger transaction. Cache/Kafka actions follow that owned commit.
- Kafka send is the normal asynchronous KafkaTemplate.send(...).whenComplete(...) flow. Keep 1–5 identical copies for the Audit idempotency demo; log synchronous/asynchronous failure without pretending to roll back an already committed payment.
- Delete PaymentCommitted, PaymentCommitListener, lockMode, @Version, SELECT FOR UPDATE, the version DTO field, and locking UI/scenarios. Ordinary transactions remain; concurrent-confirmation protection is no longer promised.
- Move administrative controllers, settings/status/request DTOs, log capture configuration and admin-only JSON parsing under each service's admin package. Shared log support belongs to commonlibs.admin.logging. Business HTTP URLs stay unchanged.
- Current source already removed PaymentPersistenceService and manual repeat-event, while some tests/docs still reference them. Align those tests/scripts; do not restore removed features.
- Source snapshot: `.runtime/pre-simple-service-flow-2026-10-29.tgz`. No Git initialization or unrelated refactoring. Keep the resource limits.
- The owner previously allowed simplifying/resetting this disposable project's database. Initially remove the obsolete payment version column after backup. Follow-up: remove the legacy prefix from all table names; merge concurrent old/new datasets without losing payments or Audit facts. Keep volumes and Kafka offsets.

## Steps

- [x] Inspect current source and reference project; snapshot source.
- [x] Rewrite persistence tests around the public PaymentService interface; add checks that Kafka/cache calls occur after commit and failed DB writes publish nothing. Observe failures before implementation.
- [x] Simplify Kafka publisher with a regression proving send does not wait for ACK; preserve duplicate payloads and error logging.
- [x] Implement explicit payment writes and remove locking across backend, UI, scripts and active docs.
- [x] Move admin packages and update all imports/tests, preserving HTTP paths.
- [x] Build/test all modules and UI. Review transaction ordering, asynchronous Kafka errors and remaining admin placement.
- [x] Back up obsolete version values, apply the scoped schema cleanup, restart the services and run the actual demo smoke checks.
- [x] Update documentation and owner memory; report remaining limitations.

## Review focus

- Creation must really send an event; current protected @Transactional self-invocation cannot provide that guarantee.
- Cache eviction and Kafka send follow a successful DB commit; failure to publish cannot undo that commit.
- No Kafka event after a failed database write; no HTTP call inside the DB transaction.
- Copies share eventId and payload; no manual replay feature is restored accidentally.
- Package moves must leave Swagger/admin/log routes and strict Audit boolean validation working.

## Ledger

- Baseline had stale references to removed persistence/replay classes; tests/scripts now use the public service and eventCopies. Removed legacy classes were not restored.
- RED: persistence tests reproduced missing Kafka creation event (5 failed / 16); publisher test reproduced blocking ACK wait (1 failed / 3); UI test reproduced the unwanted lock control.
- GREEN: full `make test` passed, including transaction rollback/commit ordering, nonblocking sends, admin HTTP contracts, scripts and UI. Effective test counts and live verification follow below.
- Ruling: write operations own their transaction instead of joining an external batch transaction. This keeps post-commit call order explicit without Spring listeners; callers must not expect an enclosing transaction to undo an already committed payment. The current entrypoints are nontransactional HTTP controllers.
- Database snapshot saved privately to `.runtime/pre-simple-flow-2026-10-29.sql` before schema cleanup; all rows and volumes will be retained.
- Full test results: 25 scripts, 88 Java, 55 UI (168 total), all GREEN. Java build and TypeScript/Vite build GREEN; assembled JARs contain no old local-event classes or old admin/log class paths.
- Fresh read-only review approved the source with no Critical/Important findings. The one stale ACK/deadline sentence was corrected as part of the requested scenario-guide update; no producer deadline was reintroduced.
- Schema cleanup complete: dropped only payments.version after backup. Row counts unchanged: 440 payments and 532 Audit facts. Volumes retained. Reopening the expired terminal required a fresh local shell; no application state was lost.
- Concurrent source change renamed JPA tables while tests were in progress. Hibernate created a second 15-row dataset; Audit history still used original IDs, causing the initial paused-consumer smoke assertion to see old facts. No consumer bug was inferred from that failure.
- Owner follow-up explicitly requested removing the legacy prefix from all table names. Updated repositories/schema/tests/scripts/docs. Payment persistence baseline reproduced three stale-name failures; full `make test` then passed all 168 tests.
- Preserving merge rehearsed against a separate restored PostgreSQL database, then applied transactionally after stopping applications. Preserved 455 payments, 12 unique customers, 538 Audit facts, 514 processing marks and 436 checks; latest 15 payment IDs remapped to 441–455 and six Audit facts adjusted. Bidirectional full-row comparisons passed. Test database removed; backup retained privately.
- All public tables/indexes/sequences now have names without the legacy prefix. Kafka payloads/offsets were not rewritten; replaying historical messages with reset offsets and disabled protection is outside supported scenes after this one-off ID repair.
- Final `make build` GREEN and `make smoke` GREEN: 12 core + 8 console + 6 feature + 4 Audit = 30 live scenarios. Administrative Swagger routes preserved. Final lag=0 and settings normal/join/safe/timeout/1, protection ON and consumer resumed. Total payments after 44 test creations = 499, including all 440 original and 15 preserved concurrent records.
- Owner intention memory updated with direct KafkaTemplate flow, removal of both locking modes, admin package placement and table naming decision. Resource limits preserved. Browser rendering and load testing not rerun for this package/schema refactor.
