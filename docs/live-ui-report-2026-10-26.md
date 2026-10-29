# Payment Platform · изменения и проверка 26 октября 2026

> Исторический отчёт. SQL-перехватчик и диагностические поля удалены 29 октября.

Сохранены пять вкладок, три backend, начальные данные, учебные режимы и прежние строковые
REST/Kafka/DB-значения. Ниже — выполненные изменения и проверенные результаты.

## Интерфейс

- Карточка платежа открывается слева; убраны надпись PAYMENT CONSOLE и декоративное 01.
- Refresh ждёт новые ответы и показывает ход, успех или ошибку. Повторные запросы сериализованы;
  закрытый экран не принимает поздние ответы. Ошибка одного источника остаётся видимой.
- Audit оформлен таблицей с устойчивыми колонками, историческими null и открытием записи/платежа.
- В Сервисах доступны Payment загрузка данных, кэш, инвалидация и HTTP/eventCopies, Antifraud mode,
  пауза/resume и инъекция ошибок Audit. Сохранение проверяется повторным чтением backend.
- Логи показывают последние 200 сообщений каждого сервиса, новые сверху, с фильтром источника.
  Это SLF4J/Logback-сообщения текущего процесса: SQL, HTTP-попытки, Redis, Kafka и обработка Audit.

## Java и Swagger

PaymentStatus: NEW/CONFIRMED/REJECTED. AntifraudReason:
APPROVED/RISK_LIMIT/FORCED_REJECTION/TEMPORARY_UNAVAILABLE.
В Entity используется EnumType.STRING, в JDBC — name()/valueOf(); JSON остаётся строковым.
Исторические события с отсутствующими статусом/причиной и старыми полями читаются.

Удалены Timeline, TimelineEntryDto, их бины, зависимости, вызовы и технические events в admin DTO.
SqlCapture перенесён в payment.domain.db, конфигурация — DatabaseConfiguration.
Из read/list DTO удалён durationMs. SQL count/list и счётчики для N+1/Redis/load оставлены.
DemoDataInitializer по-прежнему заполняет пустую БД 12 примерами, без приватного DemoPayment.

eventCopies принимает 1–5, по умолчанию 1. Все копии и ручной replay используют один
eventId/payload. Копии отправляются до общего ожидания ACK в пределах пяти секунд;
producer max.block.ms=1000. Interrupt прекращает новые отправки. sent=true требует ACK всех копий.
Без Outbox commit БД и публикация Kafka по-прежнему не атомарны.

Тёмный Swagger размещён в
[common-libs/src/main/resources/META-INF/resources/swagger-dark](../common-libs/src/main/resources/META-INF/resources/swagger-dark/).
Он использует существующие официальные webjar-ассеты и `/v3/api-docs/swagger-config`.
Новых зависимостей для оформления нет. Все backend обслуживают `/swagger-dark/index.html`,
штатный `/swagger-ui/index.html` также доступен.

## Обнаруженные ошибки и проверка исправлений

| Ошибка | Исправление и проверка |
|---|---|
| Refresh запускал чтение, но не позволял дождаться свежего результата и не показывал ход/итог | Ожидание нового чтения, отдельные состояния progress/success/error; RED → GREEN UI-тесты |
| Старый polling-ответ мог затереть сохранённый Payment draft | Защита черновика до свежего readback; регрессионный тест |
| Логи с разной точностью ISO-времени сортировались неверно | Сравнение времени как instant, sequence при совпадении; регрессионный тест |
| UI не представлял все допустимые числа копий | Селектор содержит 1, 2, 3, 4, 5; тест внешнего значения 3 |
| Ожидание Kafka выполнялось отдельно для каждой копии | Все отправки до общего ACK deadline; три теста сначала упали, затем прошли |
| normal не сбрасывал eventCopies | Реальный тест получил 5 вместо 1; normal и smoke baseline исправлены, повтор прошёл |

## Автоматические результаты

| Проверка | Результат |
|---|---|
| make test | Успешен; 20 script + 54 Java + 39 UI = 113 тестов |
| make build | Java JAR и production UI собраны |
| make smoke | 13 технологических + 8 консоли + 6 новых = 27 успешных сценариев |
| N+1 / JOIN | 12 разных владельцев: 13 SQL против 1 |
| Redis stale / safe | Кэш NEW при БД CONFIRMED; safe после commit возвращает CONFIRMED |
| Kafka eventCopies=5 | 5 published, 5 received, 4 duplicate ignored, 1 saved |
| Логи и Swagger | Три настоящих log endpoint и dark entry доступны; недопустимый limit возвращает 400 |

В финальном make test Gradle использовал UP-TO-DATE результаты Java; XML полного успешного
прогона содержит 54 теста, failures/errors/skipped=0. Остальные наборы в make test выполнены.
Известные предупреждения — unchecked generic Mockito и JVM CDS.

Доказательства: .runtime/live-ui-make-test.log,
publisher RED,
publisher GREEN,
полный smoke,
новые интеграционные наблюдения.

N+1 возникает в [PaymentService.getPayments](../payment-service/src/main/java/school/sorokin/payment/domain/PaymentService.java)
→ обычный список [PaymentRepository](../payment-service/src/main/java/school/sorokin/payment/domain/db/PaymentRepository.java)
→ LAZY customer → [mapper.getName/getEmail](../payment-service/src/main/java/school/sorokin/payment/domain/PaymentEntityMapper.java).
Дополнительный SELECT приходится на различного владельца внутри persistence context,
а не обязательно на каждый платёж. JOIN FETCH получает владельцев одним запросом.

## Браузерная проверка и её граница

Во встроенном браузере форма создала и подтвердила PAY-0113.
Встроенный тёмный Swagger отрисован с группами и схемами.
CUA не смог нажать Execute внутри cross-origin iframe ни DOM-, ни native-действием.
Запросы выполнены через ту же Swagger-страницу в отдельной вкладке того же IAB:

- Payment POST: PAY-0115, INSTALLMENT, 150000, HTTP 201.
- Antifraud GET сохранённой проверки: HTTP 200, APPROVED.
- Audit GET по paymentId=115: HTTP 200, один сохранённый факт.

Этот прогон подтверждает работу настоящего Swagger и backend-запросов в отдельной вкладке.
Execute непосредственно внутри вложенного iframe автоматизацией не подтверждён.

В истории browser console также обнаружены старые ошибки Vite WebSocket и React Hooks
во время горячего редактирования UI. После полной загрузки актуальной версии и перехода
через все пять экранов новых error/warn нет. Постоянное падение текущей версии не воспроизвелось;
старые сообщения консоли не выдаются за свежую ошибку приложения.

После полного smoke вручную проверен новый QR-платёж PAY-0165 на 100000,01 ₽:
REJECTED / RISK_LIMIT, сохранённая проверка Antifraud и один факт PaymentRejected в Audit.
JSON раскрывается; enum передаётся текстом. Геометрия живого dialog: left=0, width=560
при viewport=996 — справа остаётся место под камеру. Обновление карточки и таблицы Audit
завершается видимым «Данные обновлены» после настоящих чтений.

В Audit строка открывает сохранённую запись; кнопка «Открыть платёж» ведёт в ту же левую
карточку трёх сервисов. Для события PAY-0165 в панели «Сервисы → Логи → Audit» видны
пять received, один saved и четыре duplicate ignored с одним eventId.

Скриншоты: [карточка слева](screenshots/payment-left-drawer.png),
[таблица Audit](screenshots/audit-aligned.png),
[тёмный Swagger с ответом 201](screenshots/swagger-dark-payment.png),
[Antifraud 200](screenshots/swagger-dark-antifraud.png),
[Audit 200](screenshots/swagger-dark-audit.png),
[реальные логи дублей](screenshots/service-logs-kafka.png).

По завершении оставлены join / safe / timeout / optimistic / eventCopies=1,
Antifraud normal, Audit подключён и без инъекции ошибок. PostgreSQL, Redis и Kafka healthy,
три Java-процесса и UI работают. Платежи, аудит и volumes не удалялись;
кэш сбрасывался штатным учебным пресетом.

Исходники до правки сохранены в
резервной копии.
Удалённые Timeline/config-файлы можно восстановить из неё; весь архив поверх текущего
проекта распаковывать не следует, если нужны только отдельные старые файлы.

## Предложения по дальнейшему упрощению

Только предложения, в эту правку не включены:

- Enum для типа событий и учебных режимов вместо строк.
- Переименовать UI-тип admin-health PaymentStatus в PaymentServiceStatus, чтобы отличать
  диагностику сервиса от бизнес-статуса платежа.
- Убирать оставшийся SqlCapture только вместе с заменой count/list-контракта smoke/N+1/load.

Initializer, данные, существующие режимы и нужные счётчики сохранены.
Сценарий показа: [scenarios.md](scenarios.md).
