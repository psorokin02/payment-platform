package school.sorokin.payment.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import school.sorokin.payment.client.AntifraudUnavailableException;

import java.net.http.HttpClient;

/** Собирает HTTP-клиент и политики устойчивости из параметров YAML. */
@Configuration
@EnableConfigurationProperties(AntifraudClientProperties.class)
public class AntifraudClientConfiguration {

    /** Переиспользуем один пул HTTP-соединений вместо нового клиента на каждый запрос. */
    @Bean
    public HttpClient antifraudHttpClient(AntifraudClientProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
    }

    /** Повторяем только временные сетевые ошибки и HTTP 5xx, а не бизнес-отказ. */
    @Bean
    public Retry antifraudRetry(AntifraudClientProperties properties) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .waitDuration(properties.retryDelay())
                .retryExceptions(AntifraudUnavailableException.class)
                .build();

        return Retry.of("antifraud", config);
    }

    /** Общий breaker считает результат логической операции после её retry. */
    @Bean
    public CircuitBreaker antifraudCircuitBreaker(AntifraudClientProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(properties.circuitWindowSize())
                .minimumNumberOfCalls(properties.circuitWindowSize())
                .failureRateThreshold(properties.circuitFailureRate())
                .waitDurationInOpenState(properties.circuitOpenDuration())
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        return CircuitBreaker.of("antifraud", config);
    }
}
