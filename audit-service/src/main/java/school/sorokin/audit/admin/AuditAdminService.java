package school.sorokin.audit.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;
import school.sorokin.audit.admin.dto.AuditStatusDto;

/** Управляет защитой от дублей и паузой; собирает текущее состояние Audit. */
@Service
public class AuditAdminService {

    private static final Logger log = LoggerFactory.getLogger(AuditAdminService.class);

    private final KafkaListenerEndpointRegistry listenerRegistry;
    private final AuditRuntimeState runtimeState;

    public AuditAdminService(
            KafkaListenerEndpointRegistry listenerRegistry,
            AuditRuntimeState runtimeState
    ) {
        this.listenerRegistry = listenerRegistry;
        this.runtimeState = runtimeState;
    }

    public AuditStatusDto getStatus() {
        MessageListenerContainer container = listenerRegistry.getListenerContainer("audit");
        int assignedPartitions = container == null || container.getAssignedPartitions() == null
                ? 0 : container.getAssignedPartitions().size();

        return new AuditStatusDto(
                "audit",
                container != null && container.isPauseRequested(),
                container != null && container.isContainerPaused(),
                assignedPartitions,
                runtimeState.isIdempotencyEnabled()
        );
    }

    public AuditStatusDto updateIdempotency(boolean enabled) {
        runtimeState.setIdempotencyEnabled(enabled);
        log.info("Audit settings changed idempotencyEnabled={}", enabled);
        return getStatus();
    }

    public AuditStatusDto pause() {
        listenerRegistry.getListenerContainer("audit").pause();
        log.info("Audit consumer pause requested");
        return getStatus();
    }

    public AuditStatusDto resume() {
        listenerRegistry.getListenerContainer("audit").resume();
        log.info("Audit consumer resume requested");
        return getStatus();
    }

}
