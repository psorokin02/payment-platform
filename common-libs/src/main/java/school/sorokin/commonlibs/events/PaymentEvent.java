package school.sorokin.commonlibs.events;

import school.sorokin.commonlibs.antifraud.AntifraudReason;
import school.sorokin.commonlibs.payments.PaymentStatus;
import school.sorokin.commonlibs.payments.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;

/** Факт состояния платежа, который Payment передаёт Audit через Kafka. */
public record PaymentEvent(
        // Уникальный ID события. При повторной публикации остаётся тем же.
        String eventId,
        // Платёж, к которому относится событие; также используется как Kafka key.
        long paymentId,
        // Сумма в рублях с точностью до копеек.
        BigDecimal amount,
        // Имя вымышленного владельца для наглядного аудита.
        String customer,
        // Тип бизнес-факта: PaymentCreated, PaymentRejected или PaymentConfirmed.
        String type,
        // Время возникновения факта, а не время его обработки consumer-ом.
        Instant occurredAt,
        // Способ оплаты; null допустим у событий прежнего формата.
        PaymentType paymentType,
        // Состояние платежа в момент факта; null у исторических событий.
        PaymentStatus status,
        // Причина отказа; null для успешных и исторических событий.
        AntifraudReason reason
) {
}
