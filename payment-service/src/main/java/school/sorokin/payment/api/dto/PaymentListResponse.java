package school.sorokin.payment.api.dto;

import java.util.List;

/** Один и тот же список для сравнения N+1 и JOIN FETCH. */
public record PaymentListResponse(
        // Все платежи стенда.
        List<PaymentDto> payments,
        // Список всегда читается из PostgreSQL, чтобы Redis не скрывал N+1.
        String source
) {
}
