package school.sorokin.audit.admin;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import school.sorokin.audit.admin.dto.AuditStatusDto;
import school.sorokin.audit.admin.dto.AuditSettingsRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Настройки", description = "Идемпотентность и пауза Audit")
@RestController
@RequestMapping("/admin")
public class AuditAdminController {

    private final AuditAdminService adminService;

    public AuditAdminController(AuditAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/status")
    public AuditStatusDto status() {
        return adminService.getStatus();
    }

    @PutMapping("/settings")
    public AuditStatusDto settings(@RequestBody AuditSettingsRequest request) {
        if (request.idempotencyEnabled() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите idempotencyEnabled: true или false");
        }
        return adminService.updateIdempotency(request.idempotencyEnabled());
    }

    @PostMapping("/pause")
    public AuditStatusDto pause() {
        return adminService.pause();
    }

    @PostMapping("/resume")
    public AuditStatusDto resume() {
        return adminService.resume();
    }

}
