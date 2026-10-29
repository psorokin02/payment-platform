package school.sorokin.payment.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import school.sorokin.commonlibs.antifraud.AntifraudCheckResponse;
import school.sorokin.commonlibs.antifraud.AntifraudReason;
import school.sorokin.commonlibs.events.PaymentEvent;
import school.sorokin.commonlibs.payments.PaymentStatus;
import school.sorokin.commonlibs.payments.PaymentType;
import school.sorokin.payment.admin.PaymentSettings;
import school.sorokin.payment.api.dto.CreatePaymentRequest;
import school.sorokin.payment.api.dto.PaymentDto;
import school.sorokin.payment.cache.PaymentCache;
import school.sorokin.payment.client.AntifraudClient;
import school.sorokin.payment.admin.DemoDataInitializer;
import school.sorokin.payment.domain.db.CustomerEntity;
import school.sorokin.payment.domain.db.CustomerRepository;
import school.sorokin.payment.domain.db.PaymentRepository;
import school.sorokin.payment.kafka.PaymentEventPublisher;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "app.kafka.confirmed-topic=payment.events",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Import({PaymentEntityMapper.class, PaymentService.class,
        PaymentEventPublisher.class, PaymentSettings.class,
        PaymentPersistenceTest.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentPersistenceTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class TestBeans {
        @org.springframework.context.annotation.Bean
        ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }
    @Autowired PaymentService payments;
    @Autowired CustomerRepository customers;
    @Autowired PaymentRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper mapper;
    @Autowired PaymentSettings settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean AntifraudClient antifraud;
    @MockitoBean PaymentCache cache;
    @MockitoBean KafkaTemplate<String, String> kafka;
    long customerId;

    @BeforeEach
    void prepare() {
        settings.update(new school.sorokin.payment.admin.dto.PaymentSettingsDto("join", true, true, "timeout", 1));
        repository.deleteAll();
        customerId = customers.save(new CustomerEntity("Анна", "anna@example.test")).getId();
        var metadata = new RecordMetadata(new TopicPartition("payment.events", 0), 0, 0, 0, 0, 0);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, metadata)));
    }

    @Test
    void createsRejectedFactOnlyAfterCommitWithSavedCheckReference() throws Exception {
        when(kafka.send(anyString(), anyString(), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            long paymentId = Long.parseLong(call.getArgument(1));
            assertThat(repository.findById(paymentId).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.REJECTED);
            return CompletableFuture.completedFuture(acknowledgement());
        });
        PaymentDto payment = createPayment(new AntifraudCheckResponse(false, AntifraudReason.RISK_LIMIT, "check-17"));
        assertThat(payment.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.rejectionReason()).isEqualTo(AntifraudReason.RISK_LIMIT);
        assertThat(payment.antifraudRequestId()).isEqualTo("check-17");
        var payload = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("payment.events"), eq(Long.toString(payment.id())), payload.capture());
        PaymentEvent event = mapper.readValue(payload.getValue(), PaymentEvent.class);
        assertThat(event.type()).isEqualTo("PaymentRejected");
        assertThat(event.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(event.paymentType()).isEqualTo(PaymentType.QR);
        assertThat(event.reason()).isEqualTo(AntifraudReason.RISK_LIMIT);
    }

    @Test
    void rolledBackCreationDoesNotPublishFact() {
        jdbc.execute("alter table payments add constraint reject_test_amount check (amount <> 120000)");
        try {
            assertThatThrownBy(() -> createPayment(new AntifraudCheckResponse(true, AntifraudReason.APPROVED, "check-18")))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("alter table payments drop constraint reject_test_amount");
        }
        assertThat(repository.count()).isZero();
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void creationAndConfirmationHaveDifferentEventIdsAndStatuses() throws Exception {
        var payment = createPayment(new AntifraudCheckResponse(true, AntifraudReason.APPROVED, "check-19"));
        assertThat(payment.status()).isEqualTo(PaymentStatus.NEW);
        var confirmed = payments.confirmPayment(payment.id());
        assertThat(confirmed.status()).isEqualTo(PaymentStatus.CONFIRMED);
        var payload = ArgumentCaptor.forClass(String.class);
        verify(kafka, times(2)).send(eq("payment.events"), eq(Long.toString(payment.id())), payload.capture());
        var createdEvent = mapper.readValue(payload.getAllValues().get(0), PaymentEvent.class);
        var confirmedEvent = mapper.readValue(payload.getAllValues().get(1), PaymentEvent.class);
        assertThat(createdEvent.type()).isEqualTo("PaymentCreated");
        assertThat(createdEvent.status()).isEqualTo(PaymentStatus.NEW);
        assertThat(confirmedEvent.type()).isEqualTo("PaymentConfirmed");
        assertThat(confirmedEvent.status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(createdEvent.eventId()).isNotEqualTo(confirmedEvent.eventId());
    }

    @Test
    void listIncludesPaymentsBeyondTwentyInBothFetchModes() {
        for (int i = 0; i < 23; i++) {
            createPayment(new AntifraudCheckResponse(true, AntifraudReason.APPROVED, "check-" + i));
        }
        assertThat(payments.getPayments()).hasSize(23);
        settings.update(new school.sorokin.payment.admin.dto.PaymentSettingsDto("nplus1", null, null, null, null));
        assertThat(payments.getPayments()).hasSize(23);
    }

    @Test
    void oldCachedDtoDefaultsToCard() throws Exception {
        var legacy = mapper.readValue("{\"id\":1,\"status\":\"NEW\",\"amount\":1500}", PaymentDto.class);
        assertThat(legacy.paymentType()).isEqualTo(PaymentType.CARD);
        assertThat(legacy.rejectionReason()).isNull();
        assertThat(legacy.antifraudRequestId()).isNull();
    }

    @Test
    void unknownStatusCannotEnterPaymentFromCachedJson() {
        assertThatThrownBy(() -> mapper.readValue("{\"id\":1,\"status\":\"UNKNOWN\"}", PaymentDto.class))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void unknownReasonCannotEnterPaymentFromCachedJson() {
        assertThatThrownBy(() -> mapper.readValue("{\"id\":1,\"rejectionReason\":\"UNKNOWN\"}", PaymentDto.class))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void storedStatusAndReasonKeepExistingTextFormat() {
        var payment = createPayment(new AntifraudCheckResponse(false, AntifraudReason.RISK_LIMIT, "check-db"));
        assertThat(jdbc.queryForObject("select status from payments where id = ?", String.class, payment.id()))
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select rejection_reason from payments where id = ?", String.class, payment.id()))
                .isEqualTo("RISK_LIMIT");
        var reloaded = payments.getPayment(payment.id());
        assertThat(reloaded.status()).isEqualTo(payment.status());
        assertThat(reloaded.rejectionReason()).isEqualTo(payment.rejectionReason());
        assertThat(reloaded.amount()).isEqualByComparingTo(payment.amount());
    }

    @Test
    void joinLoadsPaymentsAndOwnersInOneQueryInsteadOfNPlusOne() {
        var initializer = new DemoDataInitializer(customers, repository);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> initializer.run(null));
        // Стандартная статистика Hibernate нужна только тесту, не API приложения.
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(payments.getPayments()).hasSize(12);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

        settings.update(new school.sorokin.payment.admin.dto.PaymentSettingsDto("nplus1", null, null, null, null));
        statistics.clear();
        assertThat(payments.getPayments()).hasSize(12);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(13);
    }

    @Test
    void demoDataKeepsAllTwelveExamplesAndDoesNotSeedTwice() {
        var initializer = new DemoDataInitializer(customers, repository);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> initializer.run(null));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> initializer.run(null));
        var examples = payments.getPayments();
        assertThat(examples).hasSize(12);
        assertThat(examples.getFirst().customer()).isEqualTo("Полина Белова");
        assertThat(examples.getFirst().amount()).isEqualByComparingTo("2450.00");
        assertThat(examples.getFirst().email()).isEqualTo("developer12@example.test");
        assertThat(examples.getLast().customer()).isEqualTo("Алексей Воронов");
        assertThat(examples.getLast().amount()).isEqualByComparingTo("12990.00");
        assertThat(examples.getLast().email()).isEqualTo("developer1@example.test");
    }

    @Test
    void confirmationRollbackKeepsNewStateAndPublishesNothing() {
        var payment = createPayment(new AntifraudCheckResponse(true, AntifraudReason.APPROVED, "check-rb"));
        clearInvocations(kafka, cache);
        jdbc.execute("alter table payments add constraint reject_test_confirmation check (status <> 'CONFIRMED')");
        try {
            assertThatThrownBy(() -> payments.confirmPayment(payment.id()))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("alter table payments drop constraint reject_test_confirmation");
        }
        assertThat(payments.getPayment(payment.id()).status()).isEqualTo(PaymentStatus.NEW);
        verify(kafka, never()).send(anyString(), anyString(), anyString());
        verify(cache, never()).invalidatePaymentCache(anyLong());
    }

    @Test
    void cacheInvalidationRunsAfterConfirmationCommit() {
        var payment = createPayment(new AntifraudCheckResponse(true, AntifraudReason.APPROVED, "check-cache"));
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(repository.findById(payment.id()).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.CONFIRMED);
            return null;
        }).when(cache).invalidatePaymentCache(payment.id());
        payments.confirmPayment(payment.id());
        verify(cache).invalidatePaymentCache(payment.id());
    }

    @Test
    void configuredCopiesKeepExactlyTheSameFact() throws Exception {
        settings.update(mapper.readValue("{\"eventCopies\":5}", school.sorokin.payment.admin.dto.PaymentSettingsDto.class));
        var payment = createPayment(new AntifraudCheckResponse(true, AntifraudReason.APPROVED, "check-copies"));
        var payload = ArgumentCaptor.forClass(String.class);
        verify(kafka, times(5)).send(eq("payment.events"), eq(Long.toString(payment.id())), payload.capture());
        assertThat(payload.getAllValues()).allSatisfy(value -> assertThat(value).isEqualTo(payload.getAllValues().getFirst()));
        assertThat(mapper.readValue(payload.getValue(), PaymentEvent.class).eventId()).isNotBlank();
    }

    @Test
    void brokerFailureKeepsTheCommittedPayment() {
        settings.update(new school.sorokin.payment.admin.dto.PaymentSettingsDto(null, null, null, null, 3));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Broker unavailable")));
        var payment = createPayment(new AntifraudCheckResponse(true, AntifraudReason.APPROVED, "check-broker-failure"));
        assertThat(repository.count()).isEqualTo(1);
        verify(kafka, times(3)).send(eq("payment.events"), eq(Long.toString(payment.id())), anyString());
    }

    @Test
    void failedAntifraudHttpDoesNotCreateRejectedPayment() {
        when(antifraud.checkPayment(any(), any())).thenThrow(new school.sorokin.payment.client.AntifraudUnavailableException("HTTP 503"));
        assertThatThrownBy(() -> payments.createPayment(request()))
                .isInstanceOf(school.sorokin.payment.client.AntifraudUnavailableException.class);
        assertThat(repository.count()).isZero();
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void omittedRequestTypeDefaultsToCard() throws Exception {
        var request = mapper.readValue("{\"customerId\":1,\"amount\":1500}", CreatePaymentRequest.class);
        assertThat(request.paymentType()).isEqualTo(PaymentType.CARD);
    }

    private CreatePaymentRequest request() {
        return new CreatePaymentRequest(customerId, new BigDecimal("120000"), "Заказ", PaymentType.QR);
    }

    private PaymentDto createPayment(AntifraudCheckResponse decision) {
        when(antifraud.checkPayment(any(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return decision;
        });
        return payments.createPayment(request());
    }

    private SendResult<String, String> acknowledgement() {
        var metadata = new RecordMetadata(new TopicPartition("payment.events", 0), 0, 0, 0, 0, 0);
        return new SendResult<>(null, metadata);
    }
}
