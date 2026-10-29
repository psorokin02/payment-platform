package school.sorokin.audit.api.dto;

import school.sorokin.commonlibs.antifraud.AntifraudReason;
import school.sorokin.commonlibs.payments.PaymentStatus;
import school.sorokin.commonlibs.payments.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;

/** Долговечная запись о бизнес-факте платежа. */
public record PaymentAuditDto(
        // Первичный ключ записи аудита.
        long id,
        // Уникальный ID входящего события; защищает от повторного бизнес-эффекта.
        String eventId,
        // Идентификатор платежа.
        long paymentId,
        // Сумма из события.
        BigDecimal amount,
        // Имя владельца из события.
        String customer,
        // Тип факта: PaymentCreated, PaymentRejected или PaymentConfirmed.
        String type,
        // Когда факт возник в Payment.
        Instant occurredAt,
        // Когда Audit записал его в свою таблицу.
        Instant receivedAt,
        // Способ оплаты; null для исторических записей.
        PaymentType paymentType,
        // Состояние в момент факта; null для исторических записей.
        PaymentStatus status,
        // Причина бизнес-отказа, если она была.
        AntifraudReason reason
) {
}
