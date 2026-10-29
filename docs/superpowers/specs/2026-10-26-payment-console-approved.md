# Payment Platform — согласованная консоль

26.10.2026 владелец одобрил продолжение с уточнениями ниже. Этот документ заменяет прежний
трёхвкладочный черновик и произвольные предложения лимитов Antifraud.

## Интерфейс

Пять вкладок: Обзор, Платежи, Сервисы, API, Аудит.

- Обзор: красивая встроенная форма создания и последние платежи; нажатие открывает карточку.
- Платежи: полный список, поиск/фильтр и такая же карточка. Не обрезать навсегда до 20 записей.
- Сервисы: доступность, порты и краткое текущее состояние трёх существующих сервисов.
- API: настоящий Swagger каждого backend, раскрываемый отдельно. Не писать собственную замену.
- Аудит: последние фактически обработанные события из БД Audit, не диагностическая лента.
- Карточка: состояние в Payment, сохранённый результат Antifraud и события Audit;
  каждый источник запрашивается у своего сервиса, его JSON можно раскрыть.
- «Order» в формулировке владельца трактуем как существующий Payment, не добавляем четвёртый сервис.
- Удалить Lab/сценарные кнопки, общую техническую ленту, LOCAL PLAYGROUND, shell-инструкции,
  счётчики, скорости и отвлекающую диагностику. Основная стилистика остаётся тёмной минималистичной.
- Создание через Swagger тоже появляется в списке автоматически; в API не создавать фоновую
  нагрузку чтениями списка, мешающую измерениям Hibernate/Redis.

## Правила и данные

- paymentType: CARD, QR, INSTALLMENT, RECURRENT. Пропущенный тип означает CARD.
- CARD и QR: сумма строго больше 100000 RUB → REJECTED; ровно 100000 разрешено.
- Для INSTALLMENT/RECURRENT это правило не применяется (решение реализации, озвучено владельцу);
  не изобретать дополнительные лимиты. Общая валидация положительной суммы до 10 млн сохраняется.
- Статусы NEW, REJECTED, CONFIRMED. Отдельный confirm меняет NEW → CONFIRMED. Нет UI-таймеров
  или придуманного процессинга. Отказ остаётся платёжной записью с причиной.
- Ошибка HTTP Antifraud не является бизнес-отказом.
- Antifraud хранит завершённые проверки по requestId и даёт отдельный GET проверки.
  Payment сохраняет ссылку requestId, чтобы карточка не выполняла новую проверку вместо чтения старой.
- Audit хранит факты создания/отказа и подтверждения. Публикация только после commit;
  повтор последнего события сохраняет eventId, UNIQUE(event_id) устраняет второй эффект.
- Outbox не добавляется; ограничения атомарности и повторов после рестарта описываются честно.
- Схема расширяется совместимо. Данные, прежние таблицы и старые Kafka topics не удаляются.
- Общая библиотека common-libs / Common Libs, только общие типы/DTO/контракты.
- Gradle/YAML/Make и обычные дисковые volumes сохраняются. Прежние admin-переименования
  завершаются согласованно во всех вызовах. Никаких RAM/offline обходных режимов.

## Фиксированный HTTP-контракт для независимых работ

- POST /api/payments: {customerId, amount, description, paymentType}.
- PaymentDto: прежние поля + paymentType, rejectionReason (nullable), antifraudRequestId (nullable).
- GET /api/payments: прежний ListResult {payments,source,queryCount,queries,durationMs}, но все записи.
- GET /api/payments/{id}: прежний ReadResult. POST /{id}/confirm, POST /{id}/repeat-event → {sent:boolean}.
- GET Antifraud /checks/{requestId}: {requestId,amount,paymentType,approved,reason,checkedAt}.
- GET Audit /entries?paymentId=ID: все события платежа; без paymentId — последние 100.
- Audit DTO: прежние поля {id,eventId,paymentId,amount,customer,type,occurredAt,receivedAt}
  + paymentType,status,reason (nullable у исторических записей).
- Служебные маршруты: Payment /api/admin/*; Antifraud /admin/*; Audit /admin/*.
- UI proxy: /api/antifraud/* → Antifraud /*, /api/audit/* → Audit /*, /api/* → Payment.
- Swagger: http://127.0.0.1:808{1,2,3}/swagger-ui/index.html, JSON /v3/api-docs.
- Kafka topic payment.events, DLT payment.events.DLT. Старая группа Audit сохраняется.

## Проверка

TDD правил и commit-событий; реальные API, Kafka idempotency, per-payment history и Swagger;
frontend tests/build; lifecycle suite; визуальная проверка пяти вкладок, формы и карточки через
встроенный браузер. Недоступность источника отличать от отсутствия данных, не показывать stale health.
