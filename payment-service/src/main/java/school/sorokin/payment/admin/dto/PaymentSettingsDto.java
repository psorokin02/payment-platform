package school.sorokin.payment.admin.dto;

/** Подготовленные режимы. В PUT можно передать только изменяемые поля. */
public record PaymentSettingsDto(
        // nplus1 — отдельные LAZY-запросы, join — один JOIN FETCH.
        String fetchMode,
        // Читать и заполнять Redis-кэш карточки платежа.
        Boolean cacheEnabled,
        // Удалять сохранённую карточку после изменения платежа.
        Boolean cacheInvalidationEnabled,
        // timeout, retry или breaker.
        String httpMode,
        // От 1 до 5 доставок одного eventId для демонстрации идемпотентности Audit.
        Integer eventCopies
) {
}
