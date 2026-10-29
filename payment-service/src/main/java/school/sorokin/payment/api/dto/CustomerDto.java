package school.sorokin.payment.api.dto;

/** Владелец для списка выбора в форме платежа. */
public record CustomerDto(
        // ID, который форма передаст как customerId.
        long id,
        // Отображаемое имя.
        String name,
        // Вымышленный email для подробной карточки.
        String email
) {
}
