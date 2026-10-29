# Как показывать Payment Platform

Один платёж, одно действие и один вывод за раз. Режимы меняются в **Сервисах**,
результат виден в карточке платежа, механизм — в коде, Swagger и обычных логах.

## Подготовка к проверке

```bash
make build
make up
make status
node scripts/scenario.mjs normal
node scripts/prepare-showcase.mjs
```

UI: http://127.0.0.1:5173/. Полная проверка с учебными записями: `make smoke` — 31 сценарий.
После тестов можно снова выполнить prepare-showcase.mjs: старые данные не удаляются.
Дополнительно проверьте полный цикл через UI.

В Сервисах раскрыть настройки нужного backend. Payment сохраняет загрузка данных, кэш, инвалидация и HTTP
и число публикаций одного события; Antifraud — режим проверки; Audit — защиту от дублей,
паузу и учебные ошибки.
После сохранения UI проверяет фактические настройки. Ниже — последние 200 сообщений каждого
сервиса, новые сверху; фильтр позволяет оставить Payment, Antifraud или Audit.

## 1. Сначала результат

В форме Обзора выбрать владельца, CARD, 1500 RUB → создать. Карточка открывается слева:
Payment=NEW; Antifraud=APPROVED из сохранённой проверки; Audit вскоре получает PaymentCreated.
JSON каждого источника раскрывается отдельно. Подтвердить → Payment=CONFIRMED,
Audit получает отдельный PaymentConfirmed. В Аудите события стоят в общей таблице.

CARD/QR на 100000.01 даёт REJECTED с причиной RISK_LIMIT; запись остаётся в списке.
Ровно 100000 разрешены. INSTALLMENT на 150000 показывает отличие учебного правила.
Это учебные правила, денежных операций нет.

Кнопка обновления ждёт запросы и сообщает результат. При ошибке backend UI показывает ошибку.
Старые seed-платежи могут не иметь ссылки на Antifraud или события создания.

## 2. HTTP — ответ нужен сейчас

В API раскрыть Payment, POST /api/payments. ID владельца взять из GET /api/customers.

```json
{"customerId":1,"amount":1500,"description":"Оплата заказа","paymentType":"CARD"}
```

Try it out → Execute → 201. Вернуться в Обзор или Платежи: список обновится автоматически.
В каждом backend используется настоящий Swagger в тёмном оформлении; кнопка «В новом окне»
открывает ту же страницу отдельно. Группа основных операций выбрана по умолчанию.

В IDE: PaymentController → AntifraudClient → AntifraudController → AntifraudService.
GET /checks/{requestId} читает завершённую проверку по ссылке из Payment.
В логах Payment видны requestId и ответ каждого HTTP-вызова; в Antifraud — причина решения.

Для сцены недоступности: Antifraud mode=error → создать платёж в Обзоре.
Красная плашка объясняет: «Невозможно создать платёж: система недоступна
(Antifraud возвращает ошибку HTTP 503)». Нового платежа и события создания нет.
Это не бизнес-отказ: mode=reject сохраняет REJECTED, а его HTTP остаётся доступным.

В slow контроллер Antifraud ждёт 60 секунд и возвращает HTTP 503, не выполняя бизнес-проверку
и не сохраняя решение. Payment ждёт одну попытку 0,9 секунды. Обычный и reject-режимы не спят.
Цифры UI получает из реальной конфигурации. Slow показан недоступным во всех режимах клиента:
увеличенный таймаут позволит дождаться 503, а retry повторит тот же сбой.
Состояние относится к новой POST-проверке, а не к доступности GET /admin/status.

Для сцены retry: в Сервисах Antifraud mode=error, Payment HTTP client=retry.
Создать платёж → три HTTP 503 с одним requestId и итоговая ошибка. Режим transient удалён.
Бизнес-отказ не вызывает retry. Timeout ограничивает ожидание клиента, но не отменяет работу сервера.
После сцены вернуть `node scripts/scenario.mjs normal`.

## 3. Hibernate — где именно возникает N+1

В Сервисах → настройки Payment → «Чтение платежей» → N+1 → сохранить.
Закрыть карточку платежа. В API выполнить GET /api/payments; на этой вкладке список
не опрашивается в фоне. Открыть Сервисы и отфильтровать логи Payment: Hibernate SQL покажет
один SELECT платежей и дополнительные SELECT владельцев. В JSON больше нет SQL-диагностики:
для показа используются обычные логи, а точное число запросов проверяет JPA-тест.

Путь в коде:

1. [PaymentService.getPayments](../payment-service/src/main/java/school/sorokin/payment/domain/PaymentService.java)
   внутри read-only транзакции выбирает findAllByOrderByIdDesc().
2. [PaymentEntity.customer](../payment-service/src/main/java/school/sorokin/payment/domain/db/PaymentEntity.java)
   — @ManyToOne(fetch=LAZY): обычный список ещё не загрузил владельцев.
3. [PaymentEntityMapper.toDto](../payment-service/src/main/java/school/sorokin/payment/domain/PaymentEntityMapper.java)
   вызывает customer.getName()/getEmail(), и Hibernate загружает нужного владельца.
4. Режим join выбирает [findLatestWithCustomers](../payment-service/src/main/java/school/sorokin/payment/domain/db/PaymentRepository.java)
   с JOIN FETCH payment.customer. Владельцы приходят в том же SELECT.

Переключить чтение в JOIN → сохранить → снова GET списка. Данные те же, SELECT один.
В реальном интеграционном прогоне 12 разных владельцев дали **13 SQL → 1 SQL**.
Дополнительный запрос приходится на каждого различного владельца в текущем persistence context:
повторяющийся владелец уже загружен в first-level cache. Поэтому это не всегда один запрос
на каждый платёж. Здесь to-one связь; Redis не исправляет эту загрузку списка.

## 4. Redis — быстрый, но старый ответ

В Сервисах → Payment: «Кэш Redis → Включён», «Инвалидация кэша → Выключена» → сохранить.
Создать свежий платёж. В API дважды GET /api/payments/{id}: POSTGRESQL → REDIS.
POST confirm → GET ID всё ещё возвращает NEW из Redis. Для сравнения установить
«Кэш Redis → Выключен» и повторить GET: PostgreSQL возвращает CONFIRMED.

На новом платеже включить «Инвалидация кэша»: заполнить кэш → confirm →
первый GET возвращает CONFIRMED из PostgreSQL, следующий — из Redis.
В логах видны hit/miss, пропуск при cacheInvalidationEnabled=false и удаление ключа после commit.
Вызов invalidatePaymentCache расположен после commit; сам флаг проверяется внутри PaymentCache.

Код: [PaymentService.confirmPayment](../payment-service/src/main/java/school/sorokin/payment/domain/PaymentService.java)
и [PaymentCache](../payment-service/src/main/java/school/sorokin/payment/cache/PaymentCache.java).
Быстрое устаревшее значение остаётся неверным. Пример не доказывает отсутствие всех гонок cache-aside.

## 5. Kafka — пять доставок, один сохранённый факт

Включить кэш и инвалидацию. В Сервисах → Payment → «Публикаций одного события» → 5 → сохранить.
Создать один NEW-платёж и дождаться Audit. В логах Payment — пять Kafka published
с copy=1/5…5/5 и одним eventId. В логах Audit — пять received, один saved,
четыре duplicate ignored. В истории платежа одна запись PaymentCreated.

Это проверено на настоящих Kafka и PostgreSQL: **5 публикаций → 5 получений → 4 пропуска → 1 запись**.
Кнопки ручного повтора в карточке больше нет: копии задаются настройкой Payment.
Подтверждение создаёт другой факт PaymentConfirmed: у платежа будет две записи,
по одной на создание и подтверждение. При включённой защите уникальность eventId в журнале
предотвращает повторный эффект. Журнал — `audit_processed_events`, а не таблица фактов.

### Сравнение с выключенной защитой

В Сервисах → Audit → Настройки Audit выбрать «Защита от дублей → Выключена»
и нажать «Сохранить настройки». До сохранения выбор остаётся черновиком.
Дождаться подтверждения сохранения. Payment по-прежнему публикует 5 копий.
Создать новый платёж: Audit сохранит **5 записей с одним eventId**, каждая со своим id.
Это видно и в карточке платежа, и в общей таблице Аудита; JSON позволяет сравнить eventId.
В логах — пять received и пять saved, без duplicate ignored.

Включить защиту обратно: старые пять записей останутся. Создать ещё один платёж — снова
одна запись. При выключенной защите журнал eventId не пополняется. Если затем включить защиту
и повторно доставить такой старый eventId, первая доставка добавит ещё один факт и отметку,
остальные будут пропущены. Эту границу проверяет тест AuditService; в живом показе используем
пять копий нового события.

Менять режим до создания следующего платежа, дождавшись обработки предыдущих пяти доставок.
Настройка фиксируется в начале обработки каждой доставки, не переписывает историю и не выполняет DDL.
Рестарт Audit возвращает значение `app.audit.idempotency-enabled` из YAML; по умолчанию true.
Отдельная быстрая проверка: `make smoke-audit` — четыре сценария на настоящей Kafka/БД.

В IDE: `AuditService.processPaymentEvent` → `ProcessedPaymentEventRepository.markProcessed`
→ `PaymentAuditLogRepository.save`. Отметка eventId и факт — одна Hibernate-транзакция:
если факт не записался, отметка тоже откатится. Новая доставка такого eventId сможет записать факт.

В Сервисах → Audit выбрать «Обработка Kafka → Остановлена» и сохранить настройки.
Создать/подтвердить платёж → история отстаёт. Выбрать «Включена» и сохранить → consumer догоняет.
HTTP-доступность Audit и пауза consumer показаны раздельно.

У Audit только две настройки: защита от дублей и пауза обработки Kafka.
Имитация ошибок, retry и DLT удалены. Если обработка реально падает, ошибка пишется в лог,
а запись пропускается. Автоматического восстановления такой записи этот учебный вариант не обещает.

Логи переключаются четырьмя кнопками: «Все сервисы», «Payment», «Anti-Fraud», «Audit».
Фильтрация происходит сразу, без сохранения настроек и перезапуска сервисов.

KafkaTemplate отправляет копии асинхронно; результат каждой публикации пишется в callback.
Вызов send может кратко ждать metadata или свободный буфер, но отдельного ожидания ACK в коде нет.
Платёж уже сохранён; результат асинхронной Kafka-публикации виден в логах, HTTP не ждёт ACK.
Commit БД и Kafka остаются неатомарными без Outbox. После сцены:

```bash
node scripts/scenario.mjs normal
```

Команда возвращает join/cacheEnabled=true/cacheInvalidationEnabled=true/timeout/eventCopies=1, Antifraud normal,
сбрасывает breaker, очищает кэш стенда, возобновляет Audit и убирает инъекцию ошибок.
Также возвращает защиту Audit от дублей во включённое состояние.

## Маленькие правки и восстановление

Java работает локально:

```bash
make rebuild-payment
make rebuild-antifraud
make rebuild-audit
```

Common Libs изменён → make rebuild для всех. React/CSS обновляются через HMR.
Не запускать одну JVM одновременно из IDE и Make.

- Смешались режимы — `node scripts/scenario.mjs normal`.
- Упал backend — его логи и make restart-payment/antifraud/audit.
- Нужны изменённые исходники — rebuild; restart использует готовую сборку.
- Для повторных доставок выбрать eventCopies=5 и создать свежий платёж.
- Задержался Audit — проверить consumer в Сервисах.
- Нужны прежние сообщения — make logs: веб-панель ограничена буфером текущего процесса.
- Не работают зависимости — make status, затем make up.
