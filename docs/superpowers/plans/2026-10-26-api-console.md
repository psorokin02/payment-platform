# Payment Platform API Console Implementation Plan

**СТАТУС: СОГЛАСОВАН С УТОЧНЕНИЯМИ 26.10.2026.**
Владелец прямо разрешил продолжить: пять вкладок, форма создания, агрегированная карточка,
CARD/QR строго больше 100000 → отказ. Источник точных решений:
`docs/superpowers/specs/2026-10-26-payment-console-approved.md` (имеет приоритет над черновиком ниже).

> **For agentic workers:** Use dispatching-parallel-agents for independent backend/UI scopes;
> root integrates scripts/docs/runtime. Follow test-driven-development and final independent review.

**Goal:** заменить Lab простой консолью платежей с настоящим Swagger и историей изменений.

**Architecture:** существующие три Spring Boot процесса, общая библиотека и React/Vite.
Проверка Antifraud остаётся синхронной; изменения состояния передаются Audit после commit.

**Tech Stack:** Java 21, Boot 3.5.3, Gradle, springdoc 2.8.x, PostgreSQL, Kafka, Redis, React.

**Spec:** `docs/superpowers/specs/2026-10-26-payment-console-approved.md`.

## Global Constraints

- Только локальный стенд; не удалять данные и не публиковать проект.
- Пять экранов: «Обзор», «Платежи», «Сервисы», «API», «Аудит»; ответы не моделируются.
- Сохранять YAML, читаемые пакеты/DTO/комментарии; Common Libs = common-libs.
- NEW, CONFIRMED, REJECTED; CARD, QR, INSTALLMENT, RECURRENT.
- Не добавлять Outbox, автоматическое подтверждение, RAM-профили или offline-режимы.
- Папка без Git: worktree/commits неприменимы; сохранять локальный журнал проверок.

## Review Focus

- Отказ Antifraud не исчезает из списка и отличается от недоступности HTTP.
- Создание через Swagger появляется в UI без ручного обновления браузера.
- История старого платежа не теряется из-за лимита последних 100 событий.
- Повтор события не создаёт вторую запись; failed commit не публикует событие.
- Недоступный сервис не показывает устаревший зелёный статус или ложную пустую историю.

### Task 1: Common Libs, admin routes, Swagger

**Files:** common-libs/** (move from contracts), settings.gradle.kts, */build.gradle.kts,
*/src/main/resources/application.yml, */api/*AdminController.java, scripts/**.
**Interfaces:** common DTO packages school.sorokin.commonlibs; /v3/api-docs and /swagger-ui/index.html
on :8081/:8082/:8083; /api/admin for Payment, /admin for Antifraud/Audit.

- [ ] Move shared source and mechanically update imports/project dependency.
- [ ] Add springdoc 2.8.x to all backend services and collapse docs by default.
- [ ] Rename lab Java classes/packages to admin/settings; update scripts and docs routes.
- [ ] Verify Gradle tests/build and no source references to old module remain.

### Task 2: Types, status events, durable per-payment history

**Files:** common-libs/**/payment/PaymentType.java, common event/check DTOs;
payment domain/API/mapper/publisher; antifraud domain; audit domain/repository/API/schema.sql;
Java test files and scripts/smoke.mjs.
**Interfaces:** CreatePaymentRequest adds paymentType; PaymentDto adds paymentType and rejectionReason.
PaymentEvent and PaymentAuditDto add paymentType,status,reason. GET /entries?paymentId=ID returns
all matching entries. Repeat endpoint republishes the latest committed event with unchanged eventId.

- [ ] Write failing Java tests for type rules, rejected payment representation, and all committed events.
- [ ] Run RED; implement defaults and explicit type rules with readable tests.
- [ ] Publish creation/rejection/confirmation only AFTER_COMMIT; retain documented non-Outbox limitation.
- [ ] Extend Audit schema additively; filter by paymentId without global LIMIT 100.
- [ ] Remove hardcoded 20 from backend reads, retain N+1/join alternatives.
- [ ] Run Java tests GREEN, update real API smoke assertions for multiple events per payment.

### Task 3: Minimal UI and embedded Swagger

**Files:** ui/src/{App,Overview,Services,AuditPage,PaymentDrawer,ApiExplorer,usePlatform,types,components}.*,
ui/src/styles.css, ui/tests/console.test.mjs; remove ui/src/Lab.tsx.
**Interfaces:** API origins :8081/:8082/:8083; all HTTP data from Task 2; health/admin data from Task 1.

- [ ] Add SSR/view tests proving no Lab/boilerplate and exactly five navigation sections.
- [ ] Watch tests fail; implement inline create form + overview + list + aggregated drawer + services + Audit + Swagger.
- [ ] Refresh payments while their screen is visible; no background list polling during API measurements.
- [ ] Load drawer history by paymentId and show unavailable state separately from empty history.
- [ ] Run frontend tests/build GREEN and visually check real create/confirm/audit/duplicate in browser.

### Task 4: Launch and demonstration handoff

**Files:** compose.yaml, docs/scenarios.md, README.md, docs/local-development.md,
CONTEXT.md, docs/verification.md.

- [x] Do not change Docker memory budget without the separately requested agreement; Compose unchanged.
- [ ] Document short IDE → make rebuild-payment workflow and exact Swagger examples.
- [ ] Run make test/build, cold rebuild/restart, real integration smoke, inspect health and memory caps.
- [ ] Get one independent whole-change review; fix important findings with regression tests.
- [ ] Update verification and owner intention memory; hand off working UI and demo instructions.

## Фактическое выполнение

- [x] Common Libs, согласованные admin-маршруты и настоящий Swagger.
- [x] Типы, граница CARD/QR, сохранённый fraud-check, события AFTER_COMMIT и scoped Audit.
- [x] Пять вкладок, форма, карточка трёх источников, полный список, SSR/data tests и build.
- [x] Свежие make test/build:20 lifecycle +24 Java +18 frontend, без проваленных тестов.
- [x] Свежий реальный make smoke:13 технологических +8 сценариев консоли.
- [x] Независимое ревью, README/инструкция по проверке/verification, сохранение прежних данных и память проекта.
- [ ] Визуальная проверка браузером: основной канал недоступен, иной способ пока не согласован.

Подробные результаты имеют приоритет над первоначальными пошаговыми чекбоксами выше:
[verification.md](../../verification.md). Повторять завершённые изменения не требуется.
