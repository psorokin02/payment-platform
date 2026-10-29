package school.sorokin.payment.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import school.sorokin.commonlibs.antifraud.AntifraudCheckRequest;
import school.sorokin.commonlibs.antifraud.AntifraudCheckResponse;
import school.sorokin.commonlibs.antifraud.AntifraudReason;
import school.sorokin.commonlibs.payments.PaymentType;
import school.sorokin.payment.admin.PaymentSettings;
import school.sorokin.payment.config.AntifraudClientProperties;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Вызывает Antifraud по HTTP; применяет timeout, а в выбранных сценах — retry и breaker.
 */
@Component
public class AntifraudClient {

    private static final Logger log = LoggerFactory.getLogger(AntifraudClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AntifraudClientProperties properties;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final PaymentSettings runtimeState;

    public AntifraudClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            AntifraudClientProperties properties,
            Retry retry,
            CircuitBreaker circuitBreaker,
            PaymentSettings runtimeState
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        this.runtimeState = runtimeState;
    }

    public AntifraudCheckResponse checkPayment(
            BigDecimal amount,
            PaymentType paymentType
    ) {
        // Один requestId сохраняется во всех повторах одной проверки.
        AntifraudCheckRequest request = new AntifraudCheckRequest(
                amount,
                UUID.randomUUID().toString(),
                paymentType
        );
        Supplier<AntifraudCheckResponse> call = () -> sendRequest(request);

        if ("timeout".equals(runtimeState.getHttpMode())) {
            return call.get();
        }

        return callWithRetryAndBreaker(call);
    }

    private AntifraudCheckResponse callWithRetryAndBreaker(
            Supplier<AntifraudCheckResponse> call
    ) {
        Supplier<AntifraudCheckResponse> callWithRetry = Retry.decorateSupplier(retry, call);
        if ("breaker".equals(runtimeState.getHttpMode())) {
            return circuitBreaker.executeSupplier(callWithRetry);
        }
        return callWithRetry.get();
    }

    private AntifraudCheckResponse sendRequest(AntifraudCheckRequest body) {
        log.info(
                "HTTP POST Antifraud /checks requestId={} amount={} paymentType={} mode={}",
                body.requestId(), body.amount(), body.paymentType(), runtimeState.getHttpMode()
        );
        try {
            HttpRequest request = createRequest(body);
            HttpResponse<String> response = executeRequest(request, body.requestId());
            return readDecision(response, body.requestId());

        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Некорректный ответ Antifraud", exception
            );
        }
    }

    private HttpRequest createRequest(AntifraudCheckRequest body) throws JsonProcessingException {
        return HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/checks"))
                .timeout(properties.readTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
    }

    /** Сетевые ошибки переводим в один тип, который понимает политика retry. */
    private HttpResponse<String> executeRequest(HttpRequest request, String requestId) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            log.info("HTTP Antifraud response requestId={} status={}", requestId, response.statusCode());
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("HTTP Antifraud interrupted requestId={}", requestId);
            throw new AntifraudUnavailableException("HTTP interrupted", exception);
        } catch (IOException exception) {
            String message = exception instanceof HttpTimeoutException
                    ? "Antifraud не ответил за " + properties.readTimeout().toMillis() + " ms"
                    : "Ошибка соединения с Antifraud";
            log.warn("HTTP Antifraud failed requestId={}: {}", requestId, exception.toString());
            throw new AntifraudUnavailableException(message, exception);
        }
    }

    private AntifraudCheckResponse readDecision(HttpResponse<String> response, String requestId)
            throws JsonProcessingException {
        int status = response.statusCode();
        if (status >= 500) {
            log.warn("HTTP Antifraud unavailable requestId={} status={}", requestId, status);
            throw new AntifraudUnavailableException("Antifraud вернул " + status);
        }
        if (status != 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Antifraud вернул неожиданный HTTP " + status
            );
        }

        AntifraudCheckResponse decision = objectMapper.readerFor(AntifraudCheckResponse.class)
                .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .readValue(response.body());

        // валидируем ответ
        if (decision == null
                || decision.reason() == null
                || !requestId.equals(decision.requestId())
                || decision.reason() == AntifraudReason.TEMPORARY_UNAVAILABLE
                || decision.approved() != (decision.reason() == AntifraudReason.APPROVED)
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Некорректный ответ Antifraud");
        }
        return decision;
    }

    public String getCircuitState() {
        return circuitBreaker.getState().name();
    }

    /**
     * Фактический таймаут из application.yml для пояснения режима Antifraud в UI.
     */
    public long getReadTimeoutMs() {
        return properties.readTimeout().toMillis();
    }

    public void resetCircuit() {
        circuitBreaker.reset();
        log.info("Antifraud circuit breaker reset state={}", circuitBreaker.getState());
    }
}
