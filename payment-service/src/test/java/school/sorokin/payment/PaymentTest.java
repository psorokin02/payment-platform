package school.sorokin.payment;

import org.junit.jupiter.api.Test;
import school.sorokin.commonlibs.payments.PaymentStatus;
import school.sorokin.payment.domain.db.CustomerEntity;
import school.sorokin.payment.domain.db.PaymentEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void confirmationIsASingleTransition() {
        var payment = paymentWithStatus(PaymentStatus.NEW);

        payment.confirm();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThatThrownBy(payment::confirm)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectedPaymentCannotBeConfirmed() {
        var payment = paymentWithStatus(PaymentStatus.REJECTED);

        assertThatThrownBy(payment::confirm)
                .isInstanceOf(IllegalStateException.class);
    }

    private PaymentEntity paymentWithStatus(PaymentStatus status) {
        return new PaymentEntity(
                new CustomerEntity("Demo", "demo@example.test"),
                new BigDecimal("10.00"),
                "Test",
                status
        );
    }
}
