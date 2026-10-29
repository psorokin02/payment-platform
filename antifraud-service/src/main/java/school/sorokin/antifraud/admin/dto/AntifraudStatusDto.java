package school.sorokin.antifraud.admin.dto;

public record AntifraudStatusDto(
        // Источник диагностических данных.
        String service,
        // Активный учебный режим.
        String mode,
        // Задержка режима slow из application.yml, миллисекунды.
        long slowDelayMs
) {
}
