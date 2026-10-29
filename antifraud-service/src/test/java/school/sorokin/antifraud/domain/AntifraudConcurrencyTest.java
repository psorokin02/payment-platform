package school.sorokin.antifraud.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import school.sorokin.antifraud.admin.AntifraudSettings;
import school.sorokin.antifraud.domain.db.AntifraudCheckRepository;
import school.sorokin.commonlibs.antifraud.AntifraudCheckRequest;
import school.sorokin.commonlibs.payments.PaymentType;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:antifraud-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class AntifraudConcurrencyTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AntifraudCheckRepository repository;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM antifraud_checks");
    }

    @Test
    void concurrentRetriesReturnThePersistedDecisionEvenWhenModeChanges() throws Exception {
        var settings = new ConcurrentSettings();
        var service = new AntifraudService(settings, new BigDecimal("100000"),
                repository);
        var request = new AntifraudCheckRequest(new BigDecimal("1500"), "same-request", PaymentType.CARD);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> service.checkPayment(request));
            var second = workers.submit(() -> service.checkPayment(request));
            var firstResponse = first.get(5, TimeUnit.SECONDS);
            var secondResponse = second.get(5, TimeUnit.SECONDS);
            var saved = service.getCheck("same-request");

            assertThat(firstResponse.requestId()).isEqualTo("same-request");
            assertThat(secondResponse.requestId()).isEqualTo("same-request");
            assertThat(firstResponse.approved()).isEqualTo(saved.approved());
            assertThat(secondResponse.approved()).isEqualTo(saved.approved());
            assertThat(firstResponse.reason()).isEqualTo(saved.reason());
            assertThat(secondResponse.reason()).isEqualTo(saved.reason());
        }
    }

    @ParameterizedTest
    @CsvSource({"2500,CARD", "1500,QR"})
    void concurrentDifferentPayloadsWithSameRequestIdReturnOneDecisionAndOneConflict(
            String amount, PaymentType paymentType
    ) throws Exception {
        var service = new AntifraudService(new ConcurrentSettings(), new BigDecimal("100000"),
                repository);
        var firstRequest = new AntifraudCheckRequest(new BigDecimal("1500"), "same-request", PaymentType.CARD);
        var secondRequest = new AntifraudCheckRequest(new BigDecimal(amount), "same-request", paymentType);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> service.checkPayment(firstRequest));
            var second = workers.submit(() -> service.checkPayment(secondRequest));
            int completed = 0;
            int conflicts = 0;
            for (var result : List.of(first, second)) {
                try {
                    var response = result.get(5, TimeUnit.SECONDS);
                    assertThat(response.reason()).isEqualTo(service.getCheck("same-request").reason());
                    completed++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOfSatisfying(ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
                    conflicts++;
                }
            }
            assertThat(completed).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
        }
    }

    private static class ConcurrentSettings extends AntifraudSettings {
        private final CyclicBarrier bothChecksStarted = new CyclicBarrier(2);
        private final AtomicInteger modeReads = new AtomicInteger();

        @Override
        public String getMode() {
            String mode = modeReads.incrementAndGet() == 1 ? "normal" : "reject";
            try {
                // Оба вызова прошли чтение до первой записи: воспроизводим гонку без sleep.
                bothChecksStarted.await(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            return mode;
        }
    }
}
