package school.sorokin.commonlibs.antifraud;

import school.sorokin.commonlibs.payments.PaymentType;

import java.math.BigDecimal;

/**
 * Запрос проверки. Сам по себе не создаёт платёж и не списывает деньги.
 */
public record AntifraudCheckRequest(
        // Сумма, которую проверяем по учебному правилу риска.
        BigDecimal amount,
        // ID логического HTTP-вызова; сохраняется между retry одной проверки.
        String requestId,
        // Пропущенный тип совместим со старыми клиентами: CARD.
        PaymentType paymentType
) {
    public AntifraudCheckRequest {
        paymentType = PaymentType.orDefault(paymentType);
    }
}
