package school.sorokin.payment.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import school.sorokin.payment.api.dto.CustomerDto;
import school.sorokin.payment.domain.db.CustomerRepository;
import java.util.List;

/** Готовит список вымышленных владельцев для формы создания платежа. */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public List<CustomerDto> getCustomers() {
        return customerRepository.findAll().stream()
                .map(customer -> new CustomerDto(customer.getId(), customer.getName(), customer.getEmail()))
                .toList();
    }
}
