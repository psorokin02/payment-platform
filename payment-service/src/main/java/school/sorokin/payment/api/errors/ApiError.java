package school.sorokin.payment.api.errors;

public record ApiError(
        // Понятное пояснение, которое UI покажет пользователю.
        String message
) {
}
