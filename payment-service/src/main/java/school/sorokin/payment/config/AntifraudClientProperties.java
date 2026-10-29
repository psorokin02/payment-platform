package school.sorokin.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.time.Duration;

/** Параметры HTTP-клиента из app.antifraud в application.yml. */
@ConfigurationProperties("app.antifraud")
public record AntifraudClientProperties(
        // Адрес соседнего сервиса.
        URI baseUrl,
        // Предел установления TCP-соединения.
        Duration connectTimeout,
        // Максимальное ожидание одного HTTP-ответа.
        Duration readTimeout,
        // Общее число попыток, включая первую.
        int maxAttempts,
        // Пауза между временными ошибками.
        Duration retryDelay,
        // Число завершённых операций в окне circuit breaker.
        int circuitWindowSize,
        // Процент ошибок для перехода в OPEN.
        float circuitFailureRate,
        // Продолжительность OPEN до пробных запросов HALF_OPEN.
        Duration circuitOpenDuration
) {
}
