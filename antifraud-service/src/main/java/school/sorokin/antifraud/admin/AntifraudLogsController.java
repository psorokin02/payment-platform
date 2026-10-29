package school.sorokin.antifraud.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import school.sorokin.commonlibs.admin.logging.ServiceLogCapture;
import school.sorokin.commonlibs.admin.logging.ServiceLogs;

/** Читает ограниченный снимок Logback из памяти текущего процесса. */
@Tag(name = "Логи", description = "Обычные логи Antifraud из текущего процесса")
@RestController
@RequestMapping("/admin/logs")
public class AntifraudLogsController {

    // Буфер общий для процесса, чтение не создаёт новых записей.
    private final ServiceLogCapture logCapture;

    public AntifraudLogsController(ServiceLogCapture logCapture) {
        this.logCapture = logCapture;
    }

    @Operation(summary = "Последние записи логов", description = "Записи идут от старых к новым; по умолчанию возвращается не более 200.")
    @GetMapping
    public ServiceLogs logs(@Parameter(description = "Количество записей, от 1 до 500")
                            @RequestParam(defaultValue = "200") int limit) {
        if (limit < 1 || limit > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit должен быть от 1 до 500");
        }
        return logCapture.snapshot(limit);
    }
}
