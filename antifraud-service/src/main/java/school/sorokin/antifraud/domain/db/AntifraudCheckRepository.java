package school.sorokin.antifraud.domain.db;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** Нативные SQL-запросы выполняет Hibernate; конфликт requestId не перезаписывает решение. */
public interface AntifraudCheckRepository extends Repository<AntifraudCheckEntity, String> {

    @Query(value = "SELECT * FROM antifraud_checks WHERE request_id = :requestId", nativeQuery = true)
    Optional<AntifraudCheckEntity> findByRequestId(@Param("requestId") String requestId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO antifraud_checks (request_id, amount, payment_type, approved, reason, checked_at)
            VALUES (:requestId, :amount, :paymentType, :approved, :reason, :checkedAt)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("requestId") String requestId,
            @Param("amount") BigDecimal amount,
            @Param("paymentType") String paymentType,
            @Param("approved") boolean approved,
            @Param("reason") String reason,
            @Param("checkedAt") Instant checkedAt
    );
}
