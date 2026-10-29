package school.sorokin.payment.domain.db;

import org.springframework.data.jpa.repository.JpaRepository;

/** Чтение и сохранение владельцев платежей в PostgreSQL. */
public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {
}
