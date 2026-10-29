package school.sorokin.payment.api.errors;

import org.junit.jupiter.api.Test;
import school.sorokin.payment.client.AntifraudUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unavailableAntifraudExplainsWhyPaymentWasNotCreated() {
        var response = handler.upstream(new AntifraudUnavailableException("Antifraud вернул 503"));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().message()).isEqualTo(
                "Невозможно создать платёж: система недоступна (Antifraud возвращает ошибку HTTP 503)."
        );
    }

    @Test
    void timeoutIsNotMisrepresentedAsAnActualAntifraud503Response() {
        var response = handler.upstream(new AntifraudUnavailableException("Antifraud не ответил за 900 ms"));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().message())
                .startsWith("Невозможно создать платёж: система недоступна")
                .contains("Antifraud не ответил за 900 мс")
                .doesNotContain("возвращает ошибку HTTP 503");
    }
}
