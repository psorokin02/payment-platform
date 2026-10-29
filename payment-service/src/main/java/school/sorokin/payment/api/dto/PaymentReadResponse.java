package school.sorokin.payment.api.dto;

/** Карточка платежа вместе с фактическими результатами её чтения. */
public record PaymentReadResponse(
        // Полученный снимок платежа.
        PaymentDto payment,
        // Источник ответа: POSTGRESQL или REDIS.
        String source
) {
}
