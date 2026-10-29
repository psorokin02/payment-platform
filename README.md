# Payment Platform

Три Spring Boot сервиса и веб-консоль для управления платежами.
Все пользователи, суммы и платежи тестовые; денежных операций нет.

## Запуск

Нужны JDK 21, Node.js 22+ и Docker Desktop. Gradle поставляется через Wrapper.

```bash
cd payment-platform
./scripts/start.sh       # первый запуск: build → Docker → Java → UI
```

UI: **http://127.0.0.1:5173/**.

```bash
make up                  # обычный запуск из готовых сборок, включая зависимости Docker
make rebuild-payment     # собрать и перезапустить только Payment
make rebuild             # после изменения Common Libs: собрать и перезапустить Java + UI
make ui                  # только frontend
make restart-ui
make status              # Docker и Java/UI отдельно
make logs-payment        # Ctrl+C завершает только просмотр логов
make stop                # остановить Java/UI, сохранив Docker-данные
make help
```

Вместо payment подходят antifraud, audit, ui. Все команды и IDE: [памятка](docs/local-development.md).
Лимиты памяти, результаты проверок и откат настроек: [память окружения](docs/memory-limits.md).

## Пять вкладок

- **Обзор:** встроенная форма создания и последние платежи.
- **Платежи:** все записи, поиск, фильтр статуса и страницы по 20 строк.
- **Сервисы:** доступность, настройки Payment/Antifraud, управление consumer Audit и реальные логи.
  В карточке Payment доступны загрузка данных, кэш, инвалидация и HTTP и 1–5 публикаций одного события.
- **API:** настоящий тёмный Swagger каждого backend; открыт один сервис, запросы идут в этот backend.
- **Аудит:** таблица последних 100 обработанных событий, сохранённых Audit в PostgreSQL.

Одна и та же карточка открывается из обзора и списка. Три источника читаются раздельно:
Payment — состояние; Antifraud — сохранённое решение этой проверки; Audit — вся история платежа.
JSON каждого источника раскрывается отдельно. Недоступность отличается от отсутствия данных.
У старых платежей может не быть ссылки на проверку; UI не придумывает результат.
Карточка открывается слева. Обновление показывает ожидание, завершение или ошибку фактических запросов.
На API нет фонового чтения списка платежей. Для отдельной проверки Hibernate/Redis закройте карточку.

В Сервисах — последние 200 сообщений каждого backend, новые сверху, фильтр по источнику.
Это обычные SLF4J/Logback-логи, включая Hibernate SQL, HTTP-попытки и Kafka-доставки.
Настройки после сохранения повторно читаются из backend.

## Правила

Типы: CARD, QR, INSTALLMENT, RECURRENT; пропущенный тип — CARD.
CARD и QR: сумма строго больше 100000 RUB получает отказ; ровно 100000 разрешены.
Для INSTALLMENT/RECURRENT это учебное правило суммы не применяется.
Общая валидация: положительная сумма до 10 млн, максимум два знака после запятой.

Создание возвращает NEW либо REJECTED; отдельный confirm меняет NEW → CONFIRMED.
Отказ остаётся записью с причиной. Timeout/503 — техническая ошибка, не бизнес-отказ.
Автоматического процессинга и переключения статусов таймером нет.

## HTTP API

| Сервис | Операции |
|---|---|
| Payment :8081 | GET /api/customers; POST /api/payments |
| Payment :8081 | GET /api/payments; GET /api/payments/{id} |
| Payment :8081 | POST /api/payments/{id}/confirm |
| Antifraud :8082 | POST /checks; GET /checks/{requestId} |
| Audit :8083 | GET /entries; GET /entries?paymentId={id} |

Создание принимает customerId, amount, description, paymentType. Confirm возвращает PaymentDto.
Каждый backend: /swagger-dark/index.html; штатный /swagger-ui/index.html также доступен.
OpenAPI: /v3/api-docs. Основные операции выбраны по умолчанию,
настройки доступны в селекторе Swagger. UI proxy снимает /api/antifraud и /api/audit;
в прямых запросах к этим портам префиксы не нужны.

Логи: Payment GET /api/admin/logs, Antifraud и Audit GET /admin/logs.
По умолчанию возвращаются 200 записей, limit — 1–500; буфер процесса хранит до 500 записей
и начинается заново после рестарта. Полные консольные логи доступны через make logs.

## Код и данные

Основные пути обработки: HTTP → Hibernate → Redis → Kafka:

1. `PaymentController` → `PaymentService.createPayment` → `AntifraudClient`.
2. `PaymentService.getPayments` → два запроса `PaymentRepository`: LAZY и JOIN FETCH.
3. `PaymentReadService.getPayment` → `PaymentCache`; инвалидация — в `PaymentService.confirmPayment` после commit.
4. `PaymentService` напрямую вызывает `PaymentEventPublisher` → `KafkaTemplate.send` → consumer Audit.

Во всех трёх сервисах `admin` содержит административные контроллеры, Settings/Status DTO,
настройки демонстрации и выдачу логов. Бизнесовые контроллеры и DTO остаются в `api`.
Общая поддержка логов находится в `commonlibs.admin.logging`.

В каждой сцене один переключатель меняет вариант реализации; подтверждение результата видно
в Swagger, карточке платежа и обычных логах. Подробный порядок — в инструкции по проверке.

```text
common-libs/         общие DTO, enum типов/статусов/причин и ограниченный захват Logback
payment-service/     платежи, Hibernate, Redis, HTTP-клиент, Kafka producer
antifraud-service/   проверка и собственное хранилище завершённых решений
audit-service/       Kafka consumer и идемпотентное хранилище фактов через Hibernate
ui/                  React/TypeScript/Vite, пять экранов и агрегированная карточка
scripts/             запуск, состояния демонстрации и интеграционные проверки
```

Payment ждёт Antifraud по HTTP. После commit PaymentCreated, PaymentRejected или PaymentConfirmed
отправляется в payment.events с key=paymentId. Audit защищает эффект уникальным event_id
в отдельном журнале обработанных событий; сами факты аудита могут повторяться.
eventCopies=1–5 публикует одинаковые eventId/payload. Ручного replay API нет.
Отправка — обычный асинхронный KafkaTemplate.send; результат брокера пишется в лог через callback.
HTTP не ждёт подтверждения Kafka. Локальных Spring events и commit-listener нет.
PaymentStatus и AntifraudReason сохраняются строками в JSON и БД; исторические null читаются.
Три backend используют существующий PostgreSQL, каждый свои таблицы.
Audit и Antifraud работают через Spring Data JPA/Hibernate; нативные @Query сохраняют
атомарные INSERT ON CONFLICT DO NOTHING для отметок событий и проверок Antifraud.
Audit не делает retry и не отправляет события в DLT: ошибка логируется, запись пропускается.
Схема Audit создаётся одним актуальным schema.sql без исторических ALTER и backfill.
Antifraud имеет собственный schema.sql, таблицы Payment создаёт Hibernate.
PostgreSQL/Redis/Kafka работают на обычных дисковых volumes.

## Демонстрация и проверки

[Инструкция по проверке](docs/scenarios.md). Режимы переключаются в Сервисах,
механизм виден в коде, Swagger и логах. SQL-перехватчика, таймеров запросов и счётчиков в API нет.
Основные сцены: HTTP → N+1/JOIN → Redis → Kafka/идемпотентность.
HTTP retry/breaker сохранены как резерв в Payment. Retry/DLT Audit и демонстрации блокировок удалены.
Режимы Antifraud: normal, reject, error (сразу 503), slow (60 секунд в контроллере, затем 503).

```bash
node scripts/scenario.mjs normal
node scripts/scenario.mjs hibernate-broken
node scripts/scenario.mjs hibernate-fixed
node scripts/scenario.mjs redis-broken
node scripts/scenario.mjs redis-fixed
node scripts/scenario.mjs kafka-paused
node scripts/scenario.mjs kafka-active
make test
make build
make smoke               # реальные API, HTTP/SQL/Redis/Kafka, консоль и Audit
make smoke-console       # только Swagger, правила, история и Kafka-копии
node scripts/features-smoke.mjs # N+1, Redis, Kafka-копии, логи, Swagger, normal reset
node scripts/prepare-showcase.mjs # свежие красивые данные без удаления старых
```

Smoke создаёт учебные платежи; сценарии восстанавливают настройки в finally.
Штатное состояние: join/timeout, cacheEnabled=true, cacheInvalidationEnabled=true,
eventCopies=1, Antifraud normal, Audit активен. Устаревших режимов safe/stale больше нет.
Команда node scripts/scenario.mjs normal восстанавливает его и очищает только кэш стенда.
Отчёты: .runtime/smoke-results.json, .runtime/console-smoke-results.json,
.runtime/features-smoke-results.json. [Актуальная структура и проверка](docs/simple-service-flow.md),
[архив прежних проверок](docs/archive/verification-before-cleanup.md).

## Ограничения

- Без Outbox commit БД и отправка Kafka не атомарны: платёж может существовать без события Audit.
- Проверка статуса защищает от последовательного повторного confirm. Защита от конкурентных
  подтверждений убрана вместе с демонстрацией блокировок; общей exactly-once гарантии здесь нет.
- Seed-платежи созданы напрямую при заполнении пустой БД и не имеют fraud-check/события создания.
  Новые платежи UI/API проходят полный маршрут.
- Это не промышленный платёжный шлюз, банковская antifraud-модель или гарантия exactly-once.
