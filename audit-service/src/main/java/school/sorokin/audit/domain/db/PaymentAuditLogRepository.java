package school.sorokin.audit.domain.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Hibernate сохраняет факты через save; история платежа читается без общего LIMIT 100. */
public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLogEntity, Long> {

    @Query(value = "SELECT * FROM audit_entries ORDER BY id DESC LIMIT 100", nativeQuery = true)
    List<PaymentAuditLogEntity> findLatest();

    @Query(value = "SELECT * FROM audit_entries WHERE payment_id = :paymentId ORDER BY id DESC", nativeQuery = true)
    List<PaymentAuditLogEntity> findByPaymentId(@Param("paymentId") long paymentId);
}
