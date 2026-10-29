package school.sorokin.antifraud.admin;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import school.sorokin.antifraud.admin.dto.AntifraudModeRequest;
import school.sorokin.antifraud.admin.dto.AntifraudStatusDto;

@Tag(name = "Настройки", description = "Учебные режимы Antifraud")
@RestController
@RequestMapping("/admin")
public class AntifraudAdminController {

    private final AntifraudSettings runtimeState;
    // Отображаем реальные задержки конфигурации, а не отдельные константы frontend.
    private final long slowDelayMs;

    public AntifraudAdminController(
            AntifraudSettings runtimeState,
            @Value("${app.simulation.slow-delay-ms}") long slowDelayMs
    ) {
        this.runtimeState = runtimeState;
        this.slowDelayMs = slowDelayMs;
    }

    @GetMapping("/status")
    public AntifraudStatusDto status() {
        return new AntifraudStatusDto(
                "antifraud", runtimeState.getMode(), slowDelayMs
        );
    }

    @PutMapping("/mode")
    public AntifraudStatusDto mode(@RequestBody AntifraudModeRequest request) {
        runtimeState.changeMode(request.mode());
        return status();
    }
}
