package school.sorokin.payment.domain;

import org.springframework.stereotype.Service;
import school.sorokin.payment.api.dto.PaymentDto;
import school.sorokin.payment.api.dto.PaymentListResponse;
import school.sorokin.payment.api.dto.PaymentReadResponse;
import school.sorokin.payment.cache.PaymentCache;

import java.util.Optional;

/** Читает карточку через cache-aside; список всегда идёт в БД для демонстрации N+1. */
@Service
public class PaymentReadService {

    private final PaymentService paymentService;
    private final PaymentCache paymentCache;

    public PaymentReadService(
            PaymentService paymentService,
            PaymentCache paymentCache
    ) {
        this.paymentService = paymentService;
        this.paymentCache = paymentCache;
    }

    public PaymentListResponse getPayments() {
        return new PaymentListResponse(paymentService.getPayments(), "POSTGRESQL");
    }

    public PaymentReadResponse getPayment(long id) {
        Optional<PaymentDto> cachedPayment = paymentCache.findPayment(id);

        if (cachedPayment.isPresent()) {
            return new PaymentReadResponse(cachedPayment.get(), "REDIS");
        }

        PaymentDto payment = paymentService.getPayment(id);
        paymentCache.putPayment(payment);
        return new PaymentReadResponse(payment, "POSTGRESQL");
    }
}
