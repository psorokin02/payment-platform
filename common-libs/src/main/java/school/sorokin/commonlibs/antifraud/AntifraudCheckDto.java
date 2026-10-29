package school.sorokin.commonlibs.antifraud;

import school.sorokin.commonlibs.payments.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Сохранённая завершённая проверка, а не новый расчёт риска.
 */
public record AntifraudCheckDto(
        // Уникальный идентификатор логического запроса.
        String requestId,
        // Проверенная сумма в рублях.
        BigDecimal amount,
        // Проверенный способ оплаты.
        PaymentType paymentType,
        // Бизнес-решение проверки.
        boolean approved,
        // APPROVED, RISK_LIMIT или FORCED_REJECTION для учебного режима.
        AntifraudReason reason,
        // Время завершения проверки.
        Instant checkedAt
) {
}
