package school.sorokin.payment.api.dto;

import school.sorokin.commonlibs.antifraud.AntifraudReason;
import school.sorokin.commonlibs.payments.PaymentStatus;
import school.sorokin.commonlibs.payments.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;

/** Представление платежа для UI и Redis. Hibernate Entity наружу не возвращается. */
public record PaymentDto(
        // Идентификатор строки платежа.
        long id,
        // Читаемый номер для экрана, например PAY-0041.
        String reference,
        // Имя владельца, для чтения которого в N+1-сцене загружается LAZY-связь.
        String customer,
        // Вымышленный email владельца.
        String email,
        // Идентификатор владельца.
        long customerId,
        // Сумма платежа.
        BigDecimal amount,
        // Код валюты; в стенде используется RUB.
        String currency,
        // Текущее состояние: NEW, CONFIRMED или REJECTED.
        PaymentStatus status,
        // Назначение платежа.
        String description,
        // Время создания платежа.
        Instant createdAt,
        // Способ оплаты, CARD по умолчанию для старых JSON в Redis.
        PaymentType paymentType,
        // Бизнес-причина отказа; для NEW и CONFIRMED отсутствует.
        AntifraudReason rejectionReason,
        // ID завершённой проверки в собственном хранилище Antifraud.
        String antifraudRequestId
) {
    public PaymentDto {
        paymentType = PaymentType.orDefault(paymentType);
    }
}
