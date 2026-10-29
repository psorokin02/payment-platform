package school.sorokin.antifraud.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import school.sorokin.antifraud.domain.AntifraudService;
import school.sorokin.antifraud.admin.AntifraudSettings;
import school.sorokin.commonlibs.antifraud.AntifraudCheckDto;
import school.sorokin.commonlibs.antifraud.AntifraudCheckRequest;
import school.sorokin.commonlibs.antifraud.AntifraudCheckResponse;
import school.sorokin.commonlibs.antifraud.AntifraudReason;

@Tag(name = "Проверки", description = "Бизнес-проверка и чтение сохранённого результата")
@RestController
@RequestMapping("/checks")
public class AntifraudController {

    private static final Logger log = LoggerFactory.getLogger(AntifraudController.class);

    private final AntifraudService antifraudService;
    private final AntifraudSettings settings;
    private final long slowDelayMs;

    public AntifraudController(
            AntifraudService antifraudService,
            AntifraudSettings settings,
            @Value("${app.simulation.slow-delay-ms}") long slowDelayMs
    ) {
        this.antifraudService = antifraudService;
        this.settings = settings;
        this.slowDelayMs = slowDelayMs;
    }

    @GetMapping("/{requestId}")
    public AntifraudCheckDto get(@PathVariable String requestId) {
        return antifraudService.getCheck(requestId);
    }

    @PostMapping
    public ResponseEntity<AntifraudCheckResponse> check(
            @RequestBody AntifraudCheckRequest request
    ) throws InterruptedException {
        String mode = settings.getMode();
        log.info("Antifraud HTTP check requestId={} amount={} paymentType={} mode={}",
                request.requestId(), request.amount(), request.paymentType(), mode);

        // Только учебный HTTP-сбой: бизнес-проверка и запись в БД ещё не начинались.
        if ("slow".equals(mode)) {
            log.warn("Antifraud delaying requestId={} delayMs={}; will return 503", request.requestId(), slowDelayMs);
            Thread.sleep(slowDelayMs);
        }

        if ("error".equals(mode) || "slow".equals(mode)) {
            log.warn("Antifraud HTTP 503 requestId={} mode={}", request.requestId(), mode);
            return ResponseEntity.status(503).body(new AntifraudCheckResponse(
                    false, AntifraudReason.TEMPORARY_UNAVAILABLE, request.requestId()
            ));
        }

        return ResponseEntity.ok(antifraudService.checkPayment(request));
    }
}
