package school.sorokin.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import school.sorokin.payment.admin.dto.PaymentSettingsDto;

import java.util.concurrent.atomic.AtomicInteger;
import school.sorokin.commonlibs.payments.PaymentType;
import school.sorokin.payment.admin.PaymentSettings;
import school.sorokin.payment.config.AntifraudClientConfiguration;
import school.sorokin.payment.config.AntifraudClientProperties;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AntifraudClientTest {
    private final HttpClient http = mock(HttpClient.class);
    private final PaymentSettings settings = new PaymentSettings();
    private final AntifraudClientProperties properties = new AntifraudClientProperties(
            URI.create("http://localhost:8081"), Duration.ofMillis(100), Duration.ofMillis(250),
            3, Duration.ZERO, 4, 50, Duration.ofSeconds(8));
    private final AntifraudClientConfiguration configuration = new AntifraudClientConfiguration();
    private final AntifraudClient client = new AntifraudClient(http, new ObjectMapper(), properties,
            configuration.antifraudRetry(properties), configuration.antifraudCircuitBreaker(properties), settings);

    @SuppressWarnings("unchecked")
    @Test
    void connectionFailureIsNotReportedAsAReadTimeoutAndKeepsItsCause() throws Exception {
        var failure = new ConnectException("Connection refused");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(failure);

        assertThatThrownBy(() -> client.checkPayment(new BigDecimal("1500"), PaymentType.CARD))
                .isInstanceOf(AntifraudUnavailableException.class)
                .hasMessageContaining("соедин")
                .hasMessageNotContaining("250")
                .hasCause(failure);
    }
    @SuppressWarnings("unchecked")
    @Test
    void malformedJsonIsAnUpstreamContractFailureWithoutRetry() throws Exception {
        settings.update(new PaymentSettingsDto(null, null, null, "retry", null));
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("not JSON");
        var attempts = new AtomicInteger();
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            attempts.incrementAndGet();
            return response;
        });

        assertThatThrownBy(() -> client.checkPayment(new BigDecimal("1500"), PaymentType.CARD))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(502);
                    assertThat(error.getCause()).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
                });
        assertThat(attempts).hasValue(1);
    }
    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"approved\":true,\"reason\":\"APPROVED\",\"requestId\":\"other-request\"}"})
    void invalidResponseContractIsRejected(String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThatThrownBy(() -> client.checkPayment(new BigDecimal("1500"), PaymentType.CARD))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(502));
    }

    @SuppressWarnings("unchecked")
    @Test
    void unexpectedUpstreamHttpStatusDoesNotBecomeALocalServerError() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(400);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertThatThrownBy(() -> client.checkPayment(new BigDecimal("1500"), PaymentType.CARD))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(502));
    }

    @SuppressWarnings("unchecked")
    @Test
    void readTimeoutReportsItsBudgetAndKeepsItsCause() throws Exception {
        var failure = new HttpTimeoutException("request timed out");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(failure);

        assertThatThrownBy(() -> client.checkPayment(new BigDecimal("1500"), PaymentType.CARD))
                .isInstanceOf(AntifraudUnavailableException.class)
                .hasMessageContaining("250 ms")
                .hasCause(failure);
    }

}
