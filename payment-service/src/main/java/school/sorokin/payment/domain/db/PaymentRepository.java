package school.sorokin.payment.domain.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Запросы к таблице платежей: обычная загрузка и JOIN FETCH для исправления N+1. */
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    // Учебный вариант: владельцы будут загружаться по одному во время преобразования в DTO.
    List<PaymentEntity> findAllByOrderByIdDesc();

    // Исправление N+1 для to-one связи: нужные владельцы загружаются тем же запросом.
    @Query("""
            select payment
            from PaymentEntity payment
            join fetch payment.customer
            order by payment.id desc
            """)
    List<PaymentEntity> findLatestWithCustomers();

}
