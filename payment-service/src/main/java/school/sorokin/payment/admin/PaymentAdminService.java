package school.sorokin.payment.admin;

import org.springframework.stereotype.Service;
import school.sorokin.payment.admin.dto.PaymentSettingsDto;
import school.sorokin.payment.admin.dto.PaymentStatusDto;
import school.sorokin.payment.cache.PaymentCache;
import school.sorokin.payment.client.AntifraudClient;

/** Управляет учебными состояниями и собирает подробную диагностику Payment для UI. */
@Service
public class PaymentAdminService {

    private final PaymentSettings runtimeState;
    private final PaymentCache paymentCache;
    private final AntifraudClient antifraudClient;

    public PaymentAdminService(
            PaymentSettings runtimeState,
            PaymentCache paymentCache,
            AntifraudClient antifraudClient
    ) {
        this.runtimeState = runtimeState;
        this.paymentCache = paymentCache;
        this.antifraudClient = antifraudClient;
    }

    public PaymentStatusDto getStatus() {
        return new PaymentStatusDto(
                "payment",
                runtimeState.getSettings(),
                antifraudClient.getCircuitState(),
                antifraudClient.getReadTimeoutMs()
        );
    }

    public synchronized PaymentStatusDto updateSettings(PaymentSettingsDto request) {
        runtimeState.validate(request);
        boolean cacheChanged = request.cacheEnabled() != null
                && request.cacheEnabled() != runtimeState.isCacheEnabled();
        boolean invalidationChanged = request.cacheInvalidationEnabled() != null
                && request.cacheInvalidationEnabled() != runtimeState.isCacheInvalidationEnabled();
        if (cacheChanged || invalidationChanged) {
            // При ошибке Redis старый режим остаётся действующим: запрос можно повторить.
            paymentCache.clearDemoCache();
        }
        runtimeState.update(request);
        return getStatus();
    }

    public void clearCache() {
        paymentCache.clearDemoCache();
    }

    public PaymentStatusDto resetCircuit() {
        antifraudClient.resetCircuit();
        return getStatus();
    }
}
