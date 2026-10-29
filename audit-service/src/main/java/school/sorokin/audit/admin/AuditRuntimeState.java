package school.sorokin.audit.admin;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime-переключатель защиты от дублей; журнал eventId хранится в PostgreSQL. */
@Component
public class AuditRuntimeState {

    // Проверять ли eventId перед сохранением факта. По умолчанию защита включена.
    private final AtomicBoolean idempotencyEnabled;

    public AuditRuntimeState(@Value("${app.audit.idempotency-enabled:true}") boolean protectionEnabled) {
        this.idempotencyEnabled = new AtomicBoolean(protectionEnabled);
    }

    public boolean isIdempotencyEnabled() {
        return idempotencyEnabled.get();
    }

    public void setIdempotencyEnabled(boolean enabled) {
        idempotencyEnabled.set(enabled);
    }


}
