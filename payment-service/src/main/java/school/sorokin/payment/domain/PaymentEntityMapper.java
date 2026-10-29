package school.sorokin.payment.domain;

import org.springframework.stereotype.Component;
import school.sorokin.payment.api.dto.PaymentDto;
import school.sorokin.payment.domain.db.CustomerEntity;
import school.sorokin.payment.domain.db.PaymentEntity;

/** Преобразует Entity в DTO. Именно здесь чтение customer.getName() делает N+1 видимым. */
@Component
public class PaymentEntityMapper {

    public PaymentDto toDto(PaymentEntity payment) {
        CustomerEntity customer = payment.getCustomer();

        return new PaymentDto(
                payment.getId(),
                "PAY-" + String.format("%04d", payment.getId()),
                customer.getName(),
                customer.getEmail(),
                customer.getId(),
                payment.getAmount(),
                "RUB",
                payment.getStatus(),
                payment.getDescription(),
                payment.getCreatedAt(),
                payment.getPaymentType(),
                payment.getRejectionReason(),
                payment.getAntifraudRequestId()
        );
    }
}
