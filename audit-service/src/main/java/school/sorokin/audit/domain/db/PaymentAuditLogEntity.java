package school.sorokin.audit.domain.db;

import jakarta.persistence.*;
import school.sorokin.commonlibs.antifraud.AntifraudReason;
import school.sorokin.commonlibs.payments.PaymentType;
import school.sorokin.commonlibs.events.PaymentEvent;
import school.sorokin.commonlibs.payments.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Hibernate-модель таблицы audit_entries. */
@Entity
@Table(name = "audit_entries")
public class PaymentAuditLogEntity {

    // Первичный ключ факта.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Одинаковый у повторных доставок; уникальность хранится в отдельном журнале.
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    // ID платежа в Payment.
    @Column(name = "payment_id", nullable = false)
    private long paymentId;

    // Сумма на момент события.
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    // Владелец на момент события.
    @Column(nullable = false)
    private String customer;

    // Тип события Kafka.
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    // Когда событие произошло в Payment.
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    // Когда факт записывается в Audit.
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    // Способ оплаты; может отсутствовать в исторических событиях.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 32)
    private PaymentType paymentType;

    // Статус платежа на момент события.
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private PaymentStatus status;

    // Причина отказа, если она была.
    @Enumerated(EnumType.STRING)
    private AntifraudReason reason;

    protected PaymentAuditLogEntity() {
    }

    public PaymentAuditLogEntity(PaymentEvent event) {
        this.eventId = event.eventId();
        this.paymentId = event.paymentId();
        this.amount = event.amount();
        this.customer = event.customer();
        this.eventType = event.type();
        this.occurredAt = event.occurredAt();
        this.receivedAt = Instant.now();
        this.paymentType = event.paymentType();
        this.status = event.status();
        this.reason = event.reason();
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public long getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCustomer() {
        return customer;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public AntifraudReason getReason() {
        return reason;
    }

}
