package school.sorokin.payment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import school.sorokin.commonlibs.payments.PaymentType;

import java.math.BigDecimal;

/** Входные данные создания платежа. Проверка суммы выполняется в PaymentService. */
public record CreatePaymentRequest(
        // Существующий владелец платежа.
        @Schema(example = "1") long customerId,
        // Положительная сумма с точностью до копеек.
        @Schema(example = "1500") BigDecimal amount,
        // Необязательное назначение платежа, до 160 символов.
        @Schema(example = "Оплата заказа") String description,
        // Пропущенный тип означает CARD для совместимости со старыми клиентами.
        @Schema(example = "CARD", defaultValue = "CARD") PaymentType paymentType
) {
    public CreatePaymentRequest {
        paymentType = PaymentType.orDefault(paymentType);
    }
}
