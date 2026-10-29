package school.sorokin.commonlibs.admin.logging;

import java.time.Instant;

/** Одна обычная запись Logback; sequence задаёт порядок поступления в процесс. */
public record ServiceLogEntry(
        long sequence,
        Instant timestamp,
        String service,
        String level,
        String logger,
        String message
) {
}
