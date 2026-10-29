package school.sorokin.commonlibs.admin.logging;

import java.util.List;

/**
 * Неизменяемый снимок логов для панели одного сервиса.
 */
public record ServiceLogs(
        String service,
        List<ServiceLogEntry> entries
) {
    public ServiceLogs {
        entries = List.copyOf(entries);
    }
}
