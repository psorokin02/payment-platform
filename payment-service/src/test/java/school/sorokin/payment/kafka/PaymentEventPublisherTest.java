package school.sorokin.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import school.sorokin.commonlibs.events.PaymentEvent;
import school.sorokin.commonlibs.payments.PaymentStatus;
import school.sorokin.commonlibs.payments.PaymentType;
import school.sorokin.payment.admin.PaymentSettings;
import school.sorokin.payment.admin.dto.PaymentSettingsDto;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentEventPublisherTest {

    @Test
    void sendsIdenticalCopiesWithoutWaitingForBrokerAcknowledgements() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenAnswer(call -> new CompletableFuture<SendResult<String, String>>());

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> publisher(kafka).publishEvent(event()));

        var payload = ArgumentCaptor.forClass(String.class);
        verify(kafka, times(5)).send(eq("payment.events"), eq("1"), payload.capture());
        assertThat(payload.getAllValues()).allMatch(payload.getValue()::equals);
        assertThat(payload.getValue()).contains("\"eventId\":\"event-1\"");
    }

    @Test
    void failedAcknowledgementDoesNotThrowAfterThePaymentWasCommitted() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Broker unavailable")));

        assertDoesNotThrow(() -> publisher(kafka).publishEvent(event()));
        verify(kafka, times(5)).send(eq("payment.events"), eq("1"), anyString());
    }

    @Test
    void synchronousSendFailureDoesNotPreventTryingOtherConfiguredCopies() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Metadata unavailable"))
                .thenReturn(CompletableFuture.completedFuture(acknowledgement()));

        assertDoesNotThrow(() -> publisher(kafka).publishEvent(event()));
        verify(kafka, times(5)).send(eq("payment.events"), eq("1"), anyString());
    }

    private PaymentEventPublisher publisher(KafkaTemplate<String, String> kafka) {
        var settings = new PaymentSettings();
        settings.update(new PaymentSettingsDto(null, null, null, null, 5));
        return new PaymentEventPublisher(
                kafka, new ObjectMapper().findAndRegisterModules(), settings, "payment.events"
        );
    }

    private PaymentEvent event() {
        return new PaymentEvent(
                "event-1", 1, BigDecimal.TEN, "Demo", "PaymentCreated",
                Instant.parse("2026-10-26T10:00:00Z"), PaymentType.CARD, PaymentStatus.NEW, null
        );
    }

    private SendResult<String, String> acknowledgement() {
        var record = new ProducerRecord<>("payment.events", "1", "{}");
        var metadata = new RecordMetadata(new TopicPartition("payment.events", 0), 0, 0, 0, 1, 2);
        return new SendResult<>(record, metadata);
    }
}
