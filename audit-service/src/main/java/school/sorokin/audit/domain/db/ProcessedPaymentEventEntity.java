package school.sorokin.audit.domain.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Одна отметка на eventId; вставка выполняется атомарным нативным запросом репозитория. */
@Entity
@Table(name = "audit_processed_events")
public class ProcessedPaymentEventEntity {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    protected ProcessedPaymentEventEntity() {
    }
}
