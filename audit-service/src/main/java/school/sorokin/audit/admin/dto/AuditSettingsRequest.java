package school.sorokin.audit.admin.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import school.sorokin.audit.admin.json.StrictBooleanDeserializer;

/** Настройка учебного режима; отсутствие значения не должно выключать защиту. */
public record AuditSettingsRequest(
        // true — пропускать повторный eventId, false — сохранять каждую доставку.
        @JsonDeserialize(using = StrictBooleanDeserializer.class)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean idempotencyEnabled
) {
}
