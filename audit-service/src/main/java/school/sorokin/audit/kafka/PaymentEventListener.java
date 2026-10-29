package school.sorokin.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import school.sorokin.audit.domain.AuditService;
import school.sorokin.commonlibs.events.PaymentEvent;

/** Читает событие Kafka и передаёт его транзакционному сервису аудита. */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public PaymentEventListener(ObjectMapper objectMapper, AuditService auditService) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @KafkaListener(id = "audit", idIsGroup = false, topics = "${app.kafka.confirmed-topic}")
    public void onPaymentEvent(ConsumerRecord<String, String> record) throws Exception {

        PaymentEvent event = objectMapper.readValue(record.value(), PaymentEvent.class);

        log.info(
                "Audit received paymentId={} eventId={} type={} offset={}",
                event.paymentId(), event.eventId(), event.type(), record.offset());

        var processed = auditService.processPaymentEvent(event);

        if (!processed) {
            log.info(
                    "Audit duplicate ignored paymentId={} eventId={} offset={}",
                    event.paymentId(), event.eventId(), record.offset());
            return;
        }
        log.info(
                "Audit saved paymentId={} eventId={} status={} reason={}",
                event.paymentId(), event.eventId(), event.status(), event.reason()
        );
    }
}
