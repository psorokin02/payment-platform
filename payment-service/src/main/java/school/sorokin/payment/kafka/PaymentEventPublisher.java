package school.sorokin.payment.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import school.sorokin.commonlibs.events.PaymentEvent;
import school.sorokin.payment.admin.PaymentSettings;

/**
 * Отправляет JSON через обычный KafkaTemplate; копии нужны для демо идемпотентности Audit.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentSettings settings;
    private final String topic;

    public PaymentEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            PaymentSettings settings,
            @Value("${app.kafka.confirmed-topic}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.settings = settings;
        this.topic = topic;
    }

    public void publishEvent(PaymentEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            log.error(
                    "Kafka serialization failed paymentId={} eventId={}; payment remains committed",
                    event.paymentId(), event.eventId(), exception);
            return;
        }

        int copies = settings.getEventCopies();
        for (int copy = 1; copy <= copies; copy++) {
            sendCopy(event, payload, copy, copies);
        }
    }

    private void sendCopy(PaymentEvent event, String payload, int copy, int copies) {
        log.info(
                "Kafka sending {} paymentId={} eventId={} copy={}/{} topic={}",
                event.type(), event.paymentId(), event.eventId(), copy, copies, topic);
        try {
            kafkaTemplate.send(topic, Long.toString(event.paymentId()), payload)
                    .whenComplete(
                            (result, error) ->
                                    logSendResult(event, copy, copies, result, error)
                    );
        } catch (RuntimeException exception) {
            // БД уже закоммичена. Ошибка одной отправки не отменяет остальные копии.
            log.error(
                    "Kafka dispatch failed paymentId={} eventId={} copy={}/{}; payment remains committed",
                    event.paymentId(), event.eventId(), copy, copies, exception
            );
        }
    }

    private void logSendResult(
            PaymentEvent event,
            int copy,
            int copies,
            SendResult<String, String> result,
            Throwable error
    ) {
        if (error != null) {
            log.error("Kafka publish failed paymentId={} eventId={} copy={}/{}; payment remains committed",
                    event.paymentId(), event.eventId(), copy, copies, error);
            return;
        }
        log.info("Kafka published paymentId={} eventId={} copy={}/{} offset={}",
                event.paymentId(), event.eventId(), copy, copies, result.getRecordMetadata().offset());
    }
}
