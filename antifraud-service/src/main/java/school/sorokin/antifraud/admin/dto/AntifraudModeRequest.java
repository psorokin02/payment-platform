package school.sorokin.antifraud.admin.dto;

public record AntifraudModeRequest(
        // normal, reject, error или slow (задержка, затем HTTP 503).
        String mode
) {
}
