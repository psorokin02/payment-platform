package school.sorokin.audit.domain.db;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Участвует в той же транзакции, что и запись факта: 1 — новая отметка, 0 — уже была. */
public interface ProcessedPaymentEventRepository extends Repository<ProcessedPaymentEventEntity, String> {

    @Modifying
    @Query(value = """
            INSERT INTO audit_processed_events (event_id)
            VALUES (:eventId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int markProcessed(@Param("eventId") String eventId);
}
