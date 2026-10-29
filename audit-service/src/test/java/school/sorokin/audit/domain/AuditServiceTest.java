package school.sorokin.audit.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import school.sorokin.audit.admin.AuditRuntimeState;
import school.sorokin.commonlibs.events.PaymentEvent;
import school.sorokin.commonlibs.antifraud.AntifraudReason;
import school.sorokin.commonlibs.payments.PaymentStatus;
import school.sorokin.commonlibs.payments.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:audit-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false"
})
class AuditServiceTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuditRuntimeState runtime;
    @Autowired private AuditService audit;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM audit_entries");
        jdbc.update("DELETE FROM audit_processed_events");
        runtime.setIdempotencyEnabled(true);
    }

    @Test
    void paymentHistoryIncludesAllFactsEvenWhenOutsideLatestHundred() {
        for (int i = 0; i < 105; i++) audit.processPaymentEvent(event("a-" + i, 41));
        audit.processPaymentEvent(event("other", 42));
        assertThat(audit.getHistory()).hasSize(100);
        assertThat(audit.getHistory(41L)).hasSize(105).allSatisfy(entry -> {
            assertThat(entry.paymentId()).isEqualTo(41);
            assertThat(entry.paymentType()).isEqualTo(PaymentType.QR);
            assertThat(entry.status()).isEqualTo(PaymentStatus.REJECTED);
            assertThat(entry.reason()).isEqualTo(AntifraudReason.RISK_LIMIT);
        });
        assertThat(audit.getHistory(99L)).isEmpty();
    }

    @Test
    void duplicateEventIdHasOneDurableEffect() {
        var event = event("same", 41);
        assertThat(audit.processPaymentEvent(event)).isTrue();
        assertThat(audit.processPaymentEvent(event)).isFalse();
        assertThat(audit.getHistory(41L)).hasSize(1);
    }

    @Test
    void disabledProtectionStoresAllFiveDeliveriesWithTheirOriginalEventId() {
        runtime.setIdempotencyEnabled(false);
        var event = event("five-off", 41);
        for (int copy = 0; copy < 5; copy++) assertThat(audit.processPaymentEvent(event)).isTrue();

        assertThat(audit.getHistory(41L)).hasSize(5)
                .allSatisfy(entry -> assertThat(entry.eventId()).isEqualTo("five-off"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_processed_events", Integer.class))
                .isZero();
    }

    @Test
    void reenableProtectsOnlyDeliveriesMarkedAfterEnabling() {
        runtime.setIdempotencyEnabled(false);
        var oldEvent = event("seen-while-off", 41);
        for (int copy = 0; copy < 5; copy++) audit.processPaymentEvent(oldEvent);

        runtime.setIdempotencyEnabled(true);

        assertThat(audit.processPaymentEvent(oldEvent)).isTrue();
        assertThat(audit.processPaymentEvent(oldEvent)).isFalse();
        assertThat(audit.getHistory(41L)).hasSize(6);
        var newEvent = event("new-after-on", 41);
        assertThat(audit.processPaymentEvent(newEvent)).isTrue();
        assertThat(audit.processPaymentEvent(newEvent)).isFalse();
        assertThat(audit.getHistory(41L)).hasSize(7);
    }

    @Test
    void failedWriteRollsBackMarkerSoRetryCanSucceedWithProtection() {
        assertRollbackAndRetry(true);
    }

    @Test
    void failedWriteRollsBackMarkerEvenWithoutProtection() {
        assertRollbackAndRetry(false);
    }

    private void assertRollbackAndRetry(boolean enabled) {
        runtime.setIdempotencyEnabled(enabled);
        var valid = event("retry-after-failure", 41);
        var invalid = new PaymentEvent(valid.eventId(), valid.paymentId(), valid.amount(), null,
                valid.type(), valid.occurredAt(), valid.paymentType(), valid.status(), valid.reason());

        assertThatThrownBy(() -> audit.processPaymentEvent(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(audit.getHistory(41L)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_processed_events", Integer.class))
                .isZero();
        assertThat(audit.processPaymentEvent(valid)).isTrue();
        assertThat(audit.getHistory(41L)).hasSize(1);
    }

    @Test
    void concurrentCopiesHaveOneEffectWhenProtectionEnabled() throws Exception {
        assertConcurrentCopies(true, 1);
    }

    @Test
    void concurrentCopiesAllPersistWhenProtectionDisabled() throws Exception {
        assertConcurrentCopies(false, 5);
    }

    private void assertConcurrentCopies(boolean enabled, int expectedRows) throws Exception {
        runtime.setIdempotencyEnabled(enabled);
        var event = event("concurrent", 41);
        var start = new CountDownLatch(1);
        var results = new ArrayList<Future<Boolean>>();
        try (var executor = Executors.newFixedThreadPool(5)) {
            for (int i = 0; i < 5; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return audit.processPaymentEvent(event);
                }));
            }
            start.countDown();
            int inserted = 0;
            for (var result : results) if (result.get(10, TimeUnit.SECONDS)) inserted++;
            assertThat(inserted).isEqualTo(expectedRows);
        }
        assertThat(audit.getHistory(41L)).hasSize(expectedRows);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_processed_events", Integer.class))
                .isEqualTo(enabled ? 1 : 0);
    }

    @Test
    void historicRowsRemainReadableWithNullableNewFields() {
        jdbc.update("""
                INSERT INTO audit_entries(event_id,payment_id,amount,customer,event_type,occurred_at)
                VALUES ('old', 1, 10, 'Анна', 'PaymentConfirmed', CURRENT_TIMESTAMP)
                """);
        assertThat(audit.getHistory()).singleElement().satisfies(entry -> {
            assertThat(entry.paymentType()).isNull();
            assertThat(entry.status()).isNull();
            assertThat(entry.reason()).isNull();
        });
    }

    @Test
    void historicalKafkaEventRemainsReadableWithoutOptionalFields() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var event = mapper.readValue("""
                {"eventId":"legacy","paymentId":1,"amount":10,"customer":"Анна",
                 "type":"PaymentConfirmed","occurredAt":"2026-10-26T10:00:00Z"}
                """, PaymentEvent.class);
        assertThat(audit.processPaymentEvent(event)).isTrue();
        assertThat(audit.getHistory(1L)).singleElement().satisfies(entry -> {
            assertThat(entry.paymentType()).isNull();
            assertThat(entry.status()).isNull();
            assertThat(entry.reason()).isNull();
        });
    }

    @Test
    void unknownStatusCannotEnterAKafkaFact() {
        var mapper = new ObjectMapper();
        assertThatThrownBy(() -> mapper.readValue("{\"status\":\"UNKNOWN\"}", PaymentEvent.class))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void auditKeepsExistingJsonAndDatabaseTextValues() throws Exception {
        audit.processPaymentEvent(event("wire", 41));
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(audit.getHistory(41L).getFirst());
        assertThat(json.get("status").asText()).isEqualTo("REJECTED");
        assertThat(json.get("reason").asText()).isEqualTo("RISK_LIMIT");
        assertThat(jdbc.queryForObject("select status from audit_entries where event_id = 'wire'", String.class))
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select reason from audit_entries where event_id = 'wire'", String.class))
                .isEqualTo("RISK_LIMIT");
    }

    private PaymentEvent event(String eventId, long paymentId) {
        return new PaymentEvent(
                eventId,
                paymentId,
                new BigDecimal("120000"),
                "Анна",
                "PaymentRejected",
                Instant.parse("2026-10-26T10:00:00Z"),
                PaymentType.QR,
                PaymentStatus.REJECTED,
                AntifraudReason.RISK_LIMIT
        );
    }
}
