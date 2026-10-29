package school.sorokin.payment.admin.dto;

/** Настройки Payment и состояние HTTP-клиента для вкладки «Сервисы». */
public record PaymentStatusDto(
        // Имя источника.
        String service,
        // Текущие учебные режимы.
        PaymentSettingsDto settings,
        // CLOSED, OPEN или HALF_OPEN для Antifraud.
        String circuitState,
        // Сколько Payment ждёт ответ Antifraud; миллисекунды из application.yml.
        long antifraudTimeoutMs
) {
}
