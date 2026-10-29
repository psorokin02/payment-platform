package school.sorokin.audit.admin.dto;

/** Текущее состояние consumer и переключатель идемпотентности. */
public record AuditStatusDto(
        // Источник данных.
        String service,
        // Пауза запрошена ведущим.
        boolean paused,
        // Listener уже применил паузу между poll/обработками.
        boolean pauseApplied,
        // Число назначенных partitions; больше нуля означает готовность consumer.
        int assigned,
        // Включена ли проверка eventId перед сохранением факта Audit.
        boolean idempotencyEnabled
) {
}
