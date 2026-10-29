package school.sorokin.payment.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import school.sorokin.payment.api.dto.CreatePaymentRequest;
import school.sorokin.payment.api.dto.PaymentDto;
import school.sorokin.payment.api.dto.PaymentListResponse;
import school.sorokin.payment.api.dto.PaymentReadResponse;
import school.sorokin.payment.domain.PaymentReadService;
import school.sorokin.payment.domain.PaymentService;

@Tag(name = "Платежи", description = "Создание, чтение и подтверждение платежей")
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentReadService readService;

    public PaymentController(
            PaymentService paymentService,
            PaymentReadService readService
    ) {
        this.paymentService = paymentService;
        this.readService = readService;
    }

    @GetMapping
    public PaymentListResponse list() {
        return readService.getPayments();
    }

    @GetMapping("/{id}")
    public PaymentReadResponse get(@PathVariable long id) {
        return readService.getPayment(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDto create(@RequestBody CreatePaymentRequest request) {
        return paymentService.createPayment(request);
    }

    @PostMapping("/{id}/confirm")
    public PaymentDto confirm(@PathVariable long id) {
        return paymentService.confirmPayment(id);
    }
}
