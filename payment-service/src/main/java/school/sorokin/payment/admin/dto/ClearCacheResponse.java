package school.sorokin.payment.admin.dto;

public record ClearCacheResponse(
        // Удалены только ключи кэша платежей payment:*.
        boolean cleared
) {
}
