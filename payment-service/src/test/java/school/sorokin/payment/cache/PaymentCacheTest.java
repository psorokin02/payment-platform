package school.sorokin.payment.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import school.sorokin.payment.admin.PaymentSettings;
import school.sorokin.payment.admin.dto.PaymentSettingsDto;

import static org.mockito.Mockito.*;

class PaymentCacheTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void invalidationFlagControlsDeletionOfThePaymentKey(boolean enabled) {
        var settings = new PaymentSettings();
        settings.update(new PaymentSettingsDto(null, null, enabled, null, null));
        var redis = mock(StringRedisTemplate.class);
        var cache = new PaymentCache(redis, new ObjectMapper(), settings, 300);

        cache.invalidatePaymentCache(7);

        verify(redis, times(enabled ? 1 : 0)).delete("payment:7");
    }
}
