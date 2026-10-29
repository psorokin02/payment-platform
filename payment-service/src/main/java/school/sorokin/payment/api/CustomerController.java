package school.sorokin.payment.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import school.sorokin.payment.api.dto.CustomerDto;
import school.sorokin.payment.domain.CustomerService;

import java.util.List;

@Tag(name = "Владельцы", description = "Вымышленные владельцы для формы платежа")
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<CustomerDto> list() {
        return customerService.getCustomers();
    }
}
