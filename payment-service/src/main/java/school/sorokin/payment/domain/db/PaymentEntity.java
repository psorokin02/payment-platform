package school.sorokin.payment.domain.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.ColumnDefault;
import school.sorokin.commonlibs.antifraud.AntifraudReason;
import school.sorokin.commonlibs.payments.PaymentStatus;
import school.sorokin.commonlibs.payments.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;

/** Состояние платежа в PostgreSQL. Отвечает за допустимый переход статуса, но не за HTTP/Kafka. */
@Entity
@Table(name = "payments")
@Getter
public class PaymentEntity {

    // Первичный ключ платежа.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY-связь: чтение имени владельца без JOIN FETCH может породить отдельный SELECT.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CustomerEntity customer;

    // Сумма хранится как decimal, без ошибок округления double.
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    // NEW, CONFIRMED или REJECTED. Сохраняем существующий строковый формат таблицы.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    // SQL DEFAULT задаётся отдельно от типа, чтобы Hibernate генерировал корректный DDL.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @ColumnDefault("'CARD'")
    private PaymentType paymentType = PaymentType.CARD;

    // Сохраняем бизнес-причину вместе с отклонённой записью.
    @Enumerated(EnumType.STRING)
    private AntifraudReason rejectionReason;

    // Ссылка на реальную завершённую проверку в Antifraud.
    private String antifraudRequestId;

    // Назначение платежа.
    private String description;

    // Момент создания, который не меняется при подтверждении.
    @Column(nullable = false)
    private Instant createdAt;

    protected PaymentEntity() {
    }

    public PaymentEntity(
            CustomerEntity customer,
            BigDecimal amount,
            String description,
            PaymentStatus status
    ) {
        this.customer = customer;
        this.amount = amount;
        this.description = description;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public PaymentEntity(
            CustomerEntity customer,
            BigDecimal amount,
            String description,
            PaymentStatus status,
            PaymentType paymentType,
            AntifraudReason rejectionReason,
            String antifraudRequestId
    ) {
        this(customer, amount, description, status);
        this.paymentType = PaymentType.orDefault(paymentType);
        this.rejectionReason = rejectionReason;
        this.antifraudRequestId = antifraudRequestId;
    }

    public PaymentType getPaymentType() {
        return PaymentType.orDefault(paymentType);
    }

    public void confirm() {
        if (status != PaymentStatus.NEW) {
            throw new IllegalStateException("Подтвердить можно только платёж в статусе NEW");
        }
        status = PaymentStatus.CONFIRMED;
    }
}
