package school.sorokin.payment.admin;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import school.sorokin.payment.admin.dto.ClearCacheResponse;
import school.sorokin.payment.admin.dto.PaymentSettingsDto;
import school.sorokin.payment.admin.dto.PaymentStatusDto;

@Tag(name = "Настройки", description = "Учебные режимы Payment")
@RestController
@RequestMapping("/api/admin")
public class PaymentAdminController {

    private final PaymentAdminService adminService;

    public PaymentAdminController(PaymentAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/status")
    public PaymentStatusDto status() {
        return adminService.getStatus();
    }

    @PutMapping("/settings")
    public PaymentStatusDto settings(@RequestBody PaymentSettingsDto request) {
        return adminService.updateSettings(request);
    }

    @PostMapping("/cache/clear")
    public ClearCacheResponse clearCache() {
        adminService.clearCache();
        return new ClearCacheResponse(true);
    }

    @PostMapping("/circuit/reset")
    public PaymentStatusDto resetCircuit() {
        return adminService.resetCircuit();
    }
}
