package school.sorokin.payment.api.errors;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import school.sorokin.payment.client.AntifraudUnavailableException;

/**
 * Переводит ожидаемые ошибки сценариев в понятные HTTP-ответы для UI.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(Exception exception) {
        return ResponseEntity.status(409).body(new ApiError(
                exception.getMessage()
        ));
    }

    @ExceptionHandler({
            AntifraudUnavailableException.class,
            CallNotPermittedException.class
    })
    public ResponseEntity<ApiError> upstream(Exception exception) {
        String reason = exception instanceof CallNotPermittedException
                ? "Circuit OPEN: запрос в Antifraud не отправлен. Повторите через 8 секунд"
                : exception.getMessage();
        reason = reason.replace("Antifraud вернул ", "Antifraud возвращает ошибку HTTP ")
                .replace(" ms", " мс");
        String message = "Невозможно создать платёж: система недоступна (" + reason + ").";

        return ResponseEntity.status(503).body(new ApiError(message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> expected(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ApiError(String.valueOf(exception.getReason())));
    }
}
