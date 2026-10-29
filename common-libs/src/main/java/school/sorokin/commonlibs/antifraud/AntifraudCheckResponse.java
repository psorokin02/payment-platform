package school.sorokin.commonlibs.antifraud;

/**
 * Результат проверки и пояснение решения соседнего сервиса
 */
public record AntifraudCheckResponse(
        // true — платёж допустим; false — отрицательное решение или временная ошибка.
        boolean approved,
        // Бизнес-решение или временная недоступность проверки.
        AntifraudReason reason,
        // ID завершённой проверки для отдельного чтения без повторного HTTP POST.
        String requestId
) {
}
