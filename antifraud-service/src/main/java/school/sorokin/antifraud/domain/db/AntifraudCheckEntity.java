package school.sorokin.antifraud.domain.db;

import jakarta.persistence.*;
import school.sorokin.commonlibs.antifraud.AntifraudReason;
import school.sorokin.commonlibs.payments.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;

/** Hibernate-модель таблицы antifraud_checks. */
@Entity
@Table(name = "antifraud_checks")
public class AntifraudCheckEntity {

    // ID проверки и ключ идемпотентности.
    @Id
    @Column(name = "request_id", length = 128)
    private String requestId;

    // Сумма проверяемого платежа.
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    // Способ оплаты.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 32)
    private PaymentType paymentType;

    // Разрешён ли платёж.
    @Column(nullable = false)
    private boolean approved;

    // Причина принятого решения.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AntifraudReason reason;

    // Когда проверка завершилась.
    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    protected AntifraudCheckEntity() {
    }

    public String getRequestId() {
        return requestId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public boolean isApproved() {
        return approved;
    }

    public AntifraudReason getReason() {
        return reason;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

}
