package school.sorokin.audit.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import school.sorokin.audit.api.dto.PaymentAuditDto;
import school.sorokin.audit.domain.AuditService;

import java.util.List;

@Tag(name = "Аудит", description = "Фактически сохранённые события платежей")
@RestController
@RequestMapping("/entries")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<PaymentAuditDto> history(@RequestParam(required = false) Long paymentId) {
        return auditService.getHistory(paymentId);
    }
}
