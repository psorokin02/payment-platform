package school.sorokin.payment.domain.db;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** Вымышленный владелец платежа. Отдельная таблица нужна для демонстрации LAZY и N+1. */
@Entity
@Table(name = "customers")
@Getter
public class CustomerEntity {

    // Первичный ключ владельца.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Имя для списка и карточки.
    private String name;

    // Демонстрационный адрес в домене example.test.
    private String email;

    protected CustomerEntity() {
    }

    public CustomerEntity(String name, String email) {
        this.name = name;
        this.email = email;
    }
}
