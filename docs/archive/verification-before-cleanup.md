# Фактическая проверка Payment Platform

> Архив результатов до упрощения 29 октября. Не описание актуальных API и схемы.

## 26 октября 2026 · актуальная UI-проверка, enum, настройки и логи

- `make test` завершился успешно: общий набор — 20 тестов скриптов, 54 Java и 39 UI,
  всего 113. Java-задачи в этом запуске были UP-TO-DATE; XML предыдущего полного успешного
  Gradle-прогона содержит 54 теста без failures/errors/skipped. `make build` прошёл.
- Полный `make smoke` прошёл: 13 технологических + 8 консоли + 6 новых интеграционных = 27.
  Результат сохранён в `.runtime/live-ui-make-smoke.log`; отдельные JSON —
  `.runtime/smoke-results.json`, `.runtime/console-smoke-results.json`,
  `.runtime/features-smoke-results.json`.
- Новый реальный прогон: N+1 при 12 разных владельцах дал 13 SQL, JOIN FETCH — один;
  stale Redis вернул NEW при CONFIRMED в PostgreSQL, safe после commit вернул CONFIRMED;
  пять Kafka-публикаций с одним eventId дали пять received, четыре duplicate ignored
  и одну сохранённую запись Audit.
- Найдена и исправлена ошибка normal preset: eventCopies оставался равен 5.
  Интеграционный тест сначала получил `5 !== 1`, после правки normal и smoke baseline
  возвращают eventCopies=1.
- PaymentStatus и AntifraudReason используются в DTO, HTTP, событиях и хранилищах.
  Проверены прежние строковые JSON/DB-значения и nullable-поля исторических событий.
  Timeline/TimelineEntryDto, их зависимости и вызовы удалены; SQL list/count и нужные счётчики сохранены.
  Seed из 12 платежей сохранён, удалён только приватный DemoPayment.
- Проверки RED → GREEN покрыли общий Kafka deadline, отправку всех копий до ожидания ACK
  и interrupt; гонку сохранения настроек с polling; порядок логов с разной точностью времени;
  все значения eventCopies=1–5; ожидание и ошибку refresh; исторические строки Audit.
- Встроенный браузер: форма создала и подтвердила PAY-0113. Тёмный Swagger отрисован в iframe.
  Управление CUA не смогло выполнить вложенный клик Execute; в отдельной вкладке того же IAB
  через ту же страницу Swagger выполнены Payment POST — PAY-0115, INSTALLMENT 150000, HTTP 201;
  GET сохранённой проверки Antifraud — 200/APPROVED; Audit GET по paymentId=115 — 200/один факт.
  Это не считается выполнением Execute внутри вложенного iframe.
- Общий тёмный entry использует существующие Swagger webjar и Springdoc config;
  `/swagger-ui/index.html` сохранён. Логи — реальные SLF4J/Logback, последние 200 на сервис
  в UI; буфер до 500, limit вне 1–500 даёт 400. SQL виден через Hibernate без параметров и таймера.

[Прежний отчёт](../live-ui-report-2026-10-26.md), [сценарий показа](../scenarios.md).
Разделы ниже описывают прежние версии и сохранены как история проверок.

## 26 октября 2026 · пять вкладок и карточка трёх источников

- Согласованный интерфейс реализован: Обзор с формой, Платежи, Сервисы, API и Аудит.
  Lab и техническая лента удалены. Common Libs переименована в common-libs.
- Свежий `make test`: 20 lifecycle, 24 Java (11 Payment / 10 Antifraud / 3 Audit),
  18 frontend — все прошли. `make build` собрал новые JAR и production bundle.
- Свежий `make smoke` на пересобранных и перезапущенных backend: 13 прежних технологических
  и 8 новых сценариев консоли прошли. Данные не удалялись; создана свежая демонстрационная выборка.
- Реально проверены Swagger JSON/HTML трёх сервисов и выбор основных операций по умолчанию,
  CARD/QR 100000 против 100000.01, сохранённая проверка Antifraud, Audit create/confirm/reject,
  одинаковый eventId при повторе, отсутствие второй записи и полный список больше 20 платежей.
- Старые N+1, Redis stale/safe, HTTP retry/timeout/breaker, Kafka pause/retry/DLT и блокировки
  остались работоспособными. Новые факты Audit требуют двух записей для create + confirm,
  но не двух эффектов от одного eventId.
- Независимое read-only ревью: Critical/Important не найдено. Незначительное замечание:
  существующая утилита Timeline остаётся в Common Libs вместе с контрактами; отдельный новый
  модуль или дублирование кода ради её переноса в этой правке не добавлялись.
- Старые generated build/target артефакты contracts перенесены в `.runtime/legacy-contracts-artifacts`,
  исходников там не было. Перенос обратим. Docker-данные и настройки ресурсов в этой правке не очищались.
- Визуальный браузерный прогон **не выполнен**: основной канал встроенного браузера отклонил
  подключение; альтернативный способ управления тем же браузером запрошен, но пока не согласован.
  Проверки HTTP, React SSR, TypeScript и production bundle не заменяют визуальную проверку.

Памятка актуального показа: [scenarios.md](../scenarios.md).

## 26 октября 2026 · обычный запуск и очистка Docker

- По прямому разрешению владельца удалены все прежние локальные Docker-образы,
  контейнеры, volumes и build cache. Проверка `docker system df` после очистки: везде 0.
  Удалены также Docker-ресурсы расширения Portainer; сам Docker Desktop не переустанавливался.
  Резервные копии старых данных не создавались.
- Удалён дополнительный Compose-файл и специальные режимы запуска. PostgreSQL, Redis и Kafka
  используют обычные именованные volumes. `make up` ждёт инфраструктуру перед запуском Java.
- `make test`: 20 сценариев lifecycle и два frontend API-теста прошли;
  Gradle test завершился успешно (существующие Java-тесты UP-TO-DATE).
- `make build`: Gradle и production bundle Vite собраны успешно.
- Новые тесты сначала воспроизвели проблему: прежний `make up` не запускал Docker и не
  останавливался при его ошибке. После изменения оба сценария проходят.
- После read-only review проверка PID ужесточена до полного launch suffix и UID;
  похожий путь `.jar.old` и JAR как посторонний аргумент не дают разрешения на сигнал.

Повторное скачивание образов выявило отдельную проблему DNS внутри Docker: `auth.docker.io`
не разрешался, хотя на Mac адрес успешно разрешался и отвечал по HTTPS. После обычного
перезапуска Docker Desktop образы скачались. Сетевые настройки не менялись.

- Новый `make up` с чистого состояния поднял три healthy Docker-контейнера, три Java API и UI.
  Audit получил Kafka partition; UI отвечает HTTP 200. Повторный `make up` сохранил четыре PID.
- `make smoke`: все 13 сценариев прошли на настоящих PostgreSQL, Redis и Kafka нового стенда.
  Созданы только учебные платежи. Настройки после проверки возвращены к штатным значениям.
- PostgreSQL и Kafka пишут в volumes на `/dev/vda1`, не в shared memory.
  После очистки и нового запуска доступно около 52 GiB в Docker VM, диск занят на 7%.
- Независимое read-only ревью не нашло блокирующих проблем. После замечания тесты отдельно
  покрывают выключенный Docker и сбой Compose при работающем Docker.

Пересоздание контейнеров с проверкой сохранности данных отдельно не проверялось;
проверены конфигурация/mounts обычных volumes, холодный запуск и реальные API-сценарии.

Команды и ограничения: [памятка](../local-development.md).

## 25 октября 2026 · после рефакторинга

- Gradle Wrapper 8.14.5, Java 21, Spring Boot 3.5.3: сборка прошла.
  Два JUnit-теста перехода статуса без ошибок.
- Сервисы полностью перезапущены из `build/libs`. Health всех трёх API — UP, Kafka consumer
  получил partition. Группы сохранены: основная группа `audit`; настройки теперь в YAML.
- `node scripts/smoke.mjs`: все 13 сценариев прошли после полного перезапуска на реальных
  PostgreSQL, Redis и Kafka. Данные не удалялись, схема таблиц не менялась.
- `npm run build`: TypeScript и production bundle собраны; Prettier check прошёл.
- `npm test`: два теста API-helper подтверждают успех при `sent:true` и ошибку при `sent:false`,
  даже если HTTP-ответ — 200. Тест с неподтверждённой публикацией сначала воспроизвёл ошибку,
  затем прошёл после исправления. Здесь fetch подменён, это не тест отказа настоящей Kafka.
- В браузере проверены создание и подтверждение платежа, Redis hit, after-commit eviction,
  сохранение аудита, раскрытие технических полей и отдельные карточки события, Audit и системы.
- В лаборатории фактический N+1 дал 13 SQL, JOIN FETCH — один. Redis без инвалидации вернул
  NEW при CONFIRMED в БД; исправленный режим вернул CONFIRMED из PostgreSQL с двумя SQL.
- Независимый read-only review не выявил Critical/Important-регрессий. После замечаний уточнены
  сообщение пустого поиска, статус недоступного consumer и обработка `sent:false` в UI.

Прежние скриншоты: [обзор](../screenshots/overview-minimal.png),
[система](../screenshots/system-details-minimal.png),
[N+1](../screenshots/hibernate-lab-minimal.png),
[Redis](../screenshots/redis-lab-minimal.png),
[событие](../screenshots/event-details-minimal.png),
[карточка платежа](../screenshots/payment-details-minimal.png),
[крупный режим интерфейса](../screenshots/presentation-mode-minimal.png).

Нагрузка ниже — исторический замер 24 октября, после рефакторинга не повторялась.
Дополнительно требуется сквозная проверка через UI.

## 24 октября 2026 · исходная версия

Проверялся настоящий локальный стенд: Spring Boot 3.5.3 / Java 21, PostgreSQL 17,
Redis 8.4, Kafka 3.9.1. Это историческая проверка прежней конфигурации;
она не доказывает готовность текущего запуска.

- Сборка исходной Java-версии: успешно; 2 JUnit-теста переходов Payment.
- `npm run build`: TypeScript и production bundle собраны.
- `node scripts/smoke.mjs`: 13 интеграционных сценариев прошли.
- Проверено через UI: создание через форму, открытие карточки, cache hit, подтверждение,
  обновлённый статус, инвалидация, сохранённый аудит, переключение N+1/исправление,
  фактические SQL 13 → 1; Redis-эксперименты stale/safe с NEW → CONFIRMED.

Smoke проверяет: N+1/один JOIN; Redis hit без SQL; eviction после commit; воспроизводимый stale;
пауза/resume; duplicate eventId с одним INSERT; retry consumer; DLT после исчерпания попыток;
HTTP retry после двух 503; отсутствие retry бизнес-отказа; timeout; circuit OPEN без вызова
соседа; optimistic и pessimistic конкурентное подтверждение; 404 и невалидную сумму.
Две проверки конкуренции считаются отдельными сценариями; stale/cache также проверяются раздельно.

## Локальная нагрузка GET by ID

Одинаковая выборка из 10 горячих paymentId, 300 запросов, concurrency 10, прогрев до замера.

| Режим | SQL за замер | Cache hits | p50 | p95 | RPS |
|---|---:|---:|---:|---:|---:|
| Без Redis | 600 | 0 | 13.07 ms | 26.56 ms | 669 |
| С Redis | 0 | 300 | 7.12 ms | 11.68 ms | 1307 |

Это короткий локальный эксперимент, не capacity planning и не универсальная скорость технологий.
Главное наблюдение: повторные чтения не дошли до БД. Без искусственной задержки PostgreSQL.
Скрипты сохраняют исходные результаты в `.runtime/smoke-results.json` и `.runtime/load-results.json`.

## Экраны

- [Обзор](../screenshots/overview.png)
- [Карточка платежа](../screenshots/payment-details.png)
- [N+1 и исправление](../screenshots/hibernate-lab.png)
- [Redis и актуальность](../screenshots/redis-lab.png)
- [Крупный режим интерфейса](../screenshots/presentation-mode.png)

После изменений проверьте полный цикл API и переключение настроек через UI.
