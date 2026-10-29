# Audit idempotency implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Execute in this session, without further approval loops for the approved migration.

**Goal:** Runtime Audit protection: five copies save one fact when ON, five when OFF, with existing data preserved.
**Architecture:** A processed-event ledger owns unique eventId. In one transaction mark eventId, conditionally skip repeat, insert Audit fact. Mark even while OFF. Startup SQL backfills ledger before removing the old unique constraint; switching settings never runs DDL.
**Tech Stack:** Existing Java21/Spring JDBC/PostgreSQL/Gradle; H2 tests; React/Vite; no new dependencies.
**Spec:** docs/superpowers/specs/2026-10-26-antifraud-and-audit-controls.md (owner approved migration).

## Global Constraints

- Preserve all existing Audit ids, eventIds, timestamps and facts; default protection ON.
- Do not delete old duplicates when re-enabling. Protect all previously encountered eventIds.
- Preserve Kafka offsets, retry/DLT, HTTP paths, Payment behavior and frontend style.
- Keep configuration in YAML, clear DTO field comments and bean comments, packages api/admin/domain/db.
- Project is not a Git repository: edit in place, keep source/DB backup and ledger; do not initialize Git or create a worktree.

## Review Focus

- Crash/failure after eventId marking must roll back both writes; Task1 rollback/retry test.
- Concurrent copies must yield one fact ON and all facts OFF; Task1 transaction tests.
- Migration re-run and backfill of old/null-field facts preserve original rows; Task1 migration tests and Task3 hashes.
- Missing/invalid settings must not silently disable protection; Task2 MockMvc and UI boundary tests.
- Re-enable after OFF must not add new effects for old IDs or hide previous duplicates; Task1/Task3 tests.

## Task 1: SQL migration and transactional recording

Files: audit-service/src/main/resources/{schema.sql,application.yml}; domain/AuditService.java;
domain/db/{ProcessedPaymentEventRepository,PaymentAuditLogRepository}.java; admin/AuditRuntimeState.java;
tests domain/{AuditSchemaMigrationTest,AuditServiceTest}.java.

Interfaces: ProcessedPaymentEventRepository.tryMarkProcessed(String):boolean;
PaymentAuditLogRepository.insert(PaymentEvent):void; AuditRuntimeState.isIdempotencyEnabled():boolean
and setIdempotencyEnabled(boolean); AuditService.recordPaymentEvent(PaymentEvent):boolean remains compatible.

- [x] Write migration preservation/re-run tests; observe RED against existing unique-only schema.
- [x] Add ledger table, backfill existing IDs, remove only named old UNIQUE(event_id); observe GREEN.
- [x] Write default ON, five OFF, OFF→ON (old + new IDs), concurrent copies and rollback/retry tests; observe RED.
- [x] Implement single transactional mark/check/insert with one flag snapshot per message; default from app.audit.idempotency-enabled=true.
- [x] Run ./gradlew test; all existing and new Java tests GREEN.

## Task 2: Settings endpoint and minimal UI

Files: AuditAdminService, AuditAdminController, api/dto/{AuditSettingsRequest,AuditStatusDto};
tests api/AuditAdminControllerTest; ui/src/{api,types,ServiceControls,styles}; ui/tests/console.test.mjs.

Interfaces: PUT /admin/settings {idempotencyEnabled:boolean} returns AuditStatusDto;
GET /admin/status includes idempotencyEnabled; saveAuditSettings(boolean,AbortSignal?) returns verified AuditStatus.

- [x] Write MockMvc default/readback/invalid-input tests and UI render/network verification tests; observe RED.
- [x] Add validated Boolean request, atomic runtime setting, log changes and return flag in status.
- [x] Add one switch inside collapsed Audit settings with ON/OFF explanation; use save/readback/refresh feedback.
- [x] Run make test and npm run build; preserve pause/retry/DLT controls.

## Task 3: Backed-up live migration, real Kafka and documentation

Files: scripts/audit-idempotency-smoke.mjs, scripts/scenario.mjs, Makefile; CONTEXT.md;
docs/scenarios.md and audit controls report.

- [x] Add real integration verifier for ON5→1, OFF5→5, re-enable/replay no extra rows and default/invalid settings. Normal preset resets protection ON.
- [x] Independent read-only review of whole scoped change before deployment; resolve substantive findings with RED→GREEN tests.
- [x] Stop only Audit; pg_dump full DB into .runtime; capture old-row count/maxId/hash and source backup.
- [x] Build/start Audit; startup migration backfills ledger. Verify old rows by original maxId/hash, marker backfill and default ON.
- [x] Run make test, make build, make smoke (old27 + new Audit scenarios), check actual logs and browser switch/list/card.
- [x] Verify repeat startup is harmless; leave protection ON, original modes restored, all services up.
- [x] Update glossary, approved spec/report, scenario instructions and owner's intention memory; hand off evidence and backup path.
