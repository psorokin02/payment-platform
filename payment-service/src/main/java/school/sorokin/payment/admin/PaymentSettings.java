package school.sorokin.payment.admin;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import school.sorokin.payment.admin.dto.PaymentSettingsDto;

import java.util.Set;

/**
 * Хранит переключатели демонстрационных вариантов, но не состояние самих платежей.
 */
@Component
@Getter
public class PaymentSettings {

    private static final Logger log = LoggerFactory.getLogger(PaymentSettings.class);

    // Выбор варианта загрузки связей Hibernate.
    private volatile String fetchMode = "join";
    // Использовать Redis при чтении карточки платежа.
    private volatile boolean cacheEnabled = true;
    // Удалять кэш после изменения платежа; выключаем для показа устаревшей карточки.
    private volatile boolean cacheInvalidationEnabled = true;
    // Политика HTTP-клиента Antifraud.
    private volatile String httpMode = "timeout";
    // Копии одного Kafka-факта сохраняют одинаковые eventId и payload.
    private volatile int eventCopies = 1;

    public PaymentSettingsDto getSettings() {
        return new PaymentSettingsDto(fetchMode, cacheEnabled, cacheInvalidationEnabled, httpMode, eventCopies);
    }

    public synchronized void update(PaymentSettingsDto request) {
        validate(request);

        apply(request);
        log.info("Payment settings changed: {}", getSettings());
    }

    public void validate(PaymentSettingsDto request) {
        validate(request.fetchMode(), "fetchMode", Set.of("nplus1", "join"));
        validate(request.httpMode(), "httpMode", Set.of("timeout", "retry", "breaker"));
        if (request.eventCopies() != null && (request.eventCopies() < 1 || request.eventCopies() > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventCopies должен быть от 1 до 5");
        }
    }

    private void apply(PaymentSettingsDto request) {
        if (request.fetchMode() != null) {
            fetchMode = request.fetchMode();
        }
        if (request.cacheEnabled() != null) {
            cacheEnabled = request.cacheEnabled();
        }
        if (request.cacheInvalidationEnabled() != null) {
            cacheInvalidationEnabled = request.cacheInvalidationEnabled();
        }
        if (request.httpMode() != null) {
            httpMode = request.httpMode();
        }
        if (request.eventCopies() != null) {
            eventCopies = request.eventCopies();
        }
    }

    private void validate(
            String value,
            String field,
            Set<String> allowedValues
    ) {
        if (value != null && !allowedValues.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field);
        }
    }
}
