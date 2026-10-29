package school.sorokin.payment.admin;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import school.sorokin.commonlibs.payments.PaymentStatus;
import school.sorokin.payment.domain.db.CustomerEntity;
import school.sorokin.payment.domain.db.CustomerRepository;
import school.sorokin.payment.domain.db.PaymentEntity;
import school.sorokin.payment.domain.db.PaymentRepository;

import java.math.BigDecimal;

/**
 * Заполняет только пустую БД вымышленными платежами с разными владельцами для сцены N+1.
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;

    public DemoDataInitializer(
            CustomerRepository customerRepository,
            PaymentRepository paymentRepository
    ) {
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (paymentRepository.count() > 0) {
            return;
        }

        createPayment(1, "Алексей Воронов", "12990.00", "Mechanical keyboard");
        createPayment(2, "Мария Волкова", "2490.00", "Cloud infrastructure");
        createPayment(3, "Иван Соколов", "4990.00", "Java Backend · подписка");
        createPayment(4, "Анна Морозова", "8990.00", "IDE лицензия");
        createPayment(5, "Дмитрий Орлов", "34990.00", "Монитор 27 дюймов");
        createPayment(6, "София Лебедева", "1590.00", "Server hosting");
        createPayment(7, "Максим Козлов", "6900.00", "Developer toolkit");
        createPayment(8, "Дарья Новикова", "10990.00", "SSD накопитель");
        createPayment(9, "Никита Павлов", "3200.00", "Книга System Design");
        createPayment(10, "Елена Фёдорова", "500.00", "Open source support");
        createPayment(11, "Артём Смирнов", "7900.00", "Workshop ticket");
        createPayment(12, "Полина Белова", "2450.00", "Кофе для команды");
    }

    private void createPayment(int number, String name, String amount, String description) {
        CustomerEntity customer = customerRepository.save(new CustomerEntity(
                name,
                "developer" + number + "@example.test"
        ));
        paymentRepository.save(new PaymentEntity(
                customer,
                new BigDecimal(amount),
                description,
                PaymentStatus.NEW
        ));
    }
}
