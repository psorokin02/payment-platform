package school.sorokin.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import school.sorokin.commonlibs.antifraud.AntifraudReason;
import school.sorokin.commonlibs.payments.PaymentType;
import school.sorokin.payment.admin.PaymentSettings;
import school.sorokin.payment.admin.dto.PaymentSettingsDto;
import school.sorokin.payment.config.AntifraudClientConfiguration;
import school.sorokin.payment.config.AntifraudClientProperties;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AntifraudHttpTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requests = new AtomicInteger();
    private final CopyOnWriteArrayList<String> requestIds = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {503, 599})
    void retryUsesOneRequestIdUntilTheTemporaryFailureRecovers(int failureStatus) throws Exception {
        var client = startServer("retry", attempt -> attempt < 3
                ? new Reply(failureStatus, false, "TEMPORARY_UNAVAILABLE")
                : new Reply(200, true, "APPROVED"));

        var result = client.checkPayment(new BigDecimal("1500"), PaymentType.CARD);

        assertThat(result.approved()).isTrue();
        assertThat(result.reason()).isEqualTo(AntifraudReason.APPROVED);
        assertThat(requests).hasValue(3);
        assertThat(requestIds).hasSize(3).containsOnly(result.requestId());
    }

    @Test
    void businessDeclineCompletesWithoutRetry() throws Exception {
        var client = startServer("retry", attempt -> new Reply(200, false, "RISK_LIMIT"));

        var result = client.checkPayment(new BigDecimal("120000"), PaymentType.CARD);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(AntifraudReason.RISK_LIMIT);
        assertThat(requests).hasValue(1);
    }

    @Test
    void openCircuitStopsRequestsAfterFailedLogicalCalls() throws Exception {
        var client = startServer("breaker", attempt -> new Reply(503, false, "TEMPORARY_UNAVAILABLE"));
        for (int call = 0; call < 4; call++) {
            assertThatThrownBy(() -> client.checkPayment(new BigDecimal("1500"), PaymentType.CARD))
                    .isInstanceOf(AntifraudUnavailableException.class);
        }

        assertThatThrownBy(() -> client.checkPayment(new BigDecimal("1500"), PaymentType.CARD))
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(client.getCircuitState()).isEqualTo("OPEN");
        assertThat(requests).hasValue(12);
    }

    @ParameterizedTest
    @CsvSource({"true,RISK_LIMIT", "false,APPROVED", "false,TEMPORARY_UNAVAILABLE"})
    void inconsistentBusinessDecisionIsRejectedWithoutRetry(boolean approved, String reason) throws Exception {
        var client = startServer("retry", attempt -> new Reply(200, approved, reason));

        assertThatThrownBy(() -> client.checkPayment(new BigDecimal("1500"), PaymentType.CARD))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(502));
        assertThat(requests).hasValue(1);
    }

    private AntifraudClient startServer(String mode, IntFunction<Reply> replies) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/checks", exchange -> {
            int attempt = requests.incrementAndGet();
            String requestId = mapper.readTree(exchange.getRequestBody()).path("requestId").asText();
            requestIds.add(requestId);
            Reply reply = replies.apply(attempt);
            byte[] body = mapper.writeValueAsBytes(Map.of(
                    "approved", reply.approved(), "reason", reply.reason(),
                    "requestId", requestId));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var properties = new AntifraudClientProperties(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Duration.ofSeconds(2), Duration.ofSeconds(2), 3, Duration.ZERO,
                4, 50, Duration.ofSeconds(8));
        var settings = new PaymentSettings();
        settings.update(new PaymentSettingsDto(null, null, null, mode, null));
        var configuration = new AntifraudClientConfiguration();
        return new AntifraudClient(HttpClient.newHttpClient(), mapper, properties,
                configuration.antifraudRetry(properties), configuration.antifraudCircuitBreaker(properties), settings);
    }

    private record Reply(int status, boolean approved, String reason) {
    }
}
