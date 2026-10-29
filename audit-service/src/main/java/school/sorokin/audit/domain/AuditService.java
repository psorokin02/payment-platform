package school.sorokin.audit.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import school.sorokin.audit.api.dto.PaymentAuditDto;
import school.sorokin.audit.domain.db.PaymentAuditLogRepository;
import school.sorokin.audit.domain.db.PaymentAuditLogEntity;
import school.sorokin.audit.domain.db.ProcessedPaymentEventRepository;
import school.sorokin.audit.admin.AuditRuntimeState;
import school.sorokin.commonlibs.events.PaymentEvent;

import java.util.List;

/** Записывает факт и eventId атомарно; учебная настройка позволяет показать последствия дублей. */
@Service
public class AuditService {

    private final PaymentAuditLogRepository auditRepository;
    private final ProcessedPaymentEventRepository processedEvents;
    private final AuditEntityMapper mapper;
    private final AuditRuntimeState runtimeState;

    public AuditService(
            PaymentAuditLogRepository auditRepository,
            ProcessedPaymentEventRepository processedEvents,
            AuditEntityMapper mapper,
            AuditRuntimeState runtimeState
    ) {
        this.auditRepository = auditRepository;
        this.processedEvents = processedEvents;
        this.mapper = mapper;
        this.runtimeState = runtimeState;
    }

    @Transactional
    public boolean processPaymentEvent(PaymentEvent event) {
        boolean idempotencyEnabled = runtimeState.isIdempotencyEnabled();

        if (!idempotencyEnabled) {
            auditRepository.save(new PaymentAuditLogEntity(event));
            return true;
        }

        boolean isFirstDelivery = processedEvents.markProcessed(event.eventId()) == 1;
        if (!isFirstDelivery) {
            return false;
        }

        auditRepository.save(new PaymentAuditLogEntity(event));
        return true;
    }

    @Transactional(readOnly = true)
    public List<PaymentAuditDto> getHistory() {
        return getHistory(null);
    }

    @Transactional(readOnly = true)
    public List<PaymentAuditDto> getHistory(Long paymentId) {
        var entries = paymentId == null ? auditRepository.findLatest() : auditRepository.findByPaymentId(paymentId);
        return entries.stream()
                .map(mapper::toDto)
                .toList();
    }
}
