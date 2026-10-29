package school.sorokin.antifraud.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/** Хранит выбранный режим ответа Antifraud. */
@Component
public class AntifraudSettings {

    private static final Logger log = LoggerFactory.getLogger(AntifraudSettings.class);
    private static final Set<String> ALLOWED_MODES = Set.of(
            "normal", "slow", "error", "reject"
    );

    // Режим ответа меняется через административный HTTP API.
    private volatile String mode = "normal";

    public String getMode() {
        return mode;
    }

    public void changeMode(String requestedMode) {
        String nextMode = requestedMode == null ? "normal" : requestedMode;
        if (!ALLOWED_MODES.contains(nextMode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown fault mode");
        }
        mode = nextMode;
        log.info("Antifraud mode changed mode={}", mode);
    }
}
