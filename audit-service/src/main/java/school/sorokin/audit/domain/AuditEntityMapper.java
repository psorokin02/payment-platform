package school.sorokin.audit.domain;

import org.springframework.stereotype.Component;
import school.sorokin.audit.api.dto.PaymentAuditDto;
import school.sorokin.audit.domain.db.PaymentAuditLogEntity;

/** Отделяет Hibernate-модель таблицы от представления аудита в HTTP API. */
@Component
public class AuditEntityMapper {

    public PaymentAuditDto toDto(PaymentAuditLogEntity entity) {
        return new PaymentAuditDto(
                entity.getId(),
                entity.getEventId(),
                entity.getPaymentId(),
                entity.getAmount(),
                entity.getCustomer(),
                entity.getEventType(),
                entity.getOccurredAt(),
                entity.getReceivedAt(),
                entity.getPaymentType(),
                entity.getStatus(),
                entity.getReason()
        );
    }
}
