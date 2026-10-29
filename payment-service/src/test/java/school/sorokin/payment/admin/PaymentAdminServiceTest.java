package school.sorokin.payment.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import school.sorokin.payment.admin.dto.PaymentSettingsDto;
import school.sorokin.payment.cache.PaymentCache;
import school.sorokin.payment.client.AntifraudClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentAdminServiceTest {

    @Test
    void failedCacheCleanupDoesNotApplyAnyRequestedSettings() {
        PaymentSettings settings = new PaymentSettings();
        PaymentSettingsDto before = settings.getSettings();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.scan(any())).thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        PaymentCache cache = new PaymentCache(redis, new ObjectMapper(), settings, 300);
        PaymentAdminService admin = new PaymentAdminService(
                settings, cache, mock(AntifraudClient.class)
        );

        assertThatThrownBy(() -> admin.updateSettings(
                new PaymentSettingsDto("nplus1", true, false, null, 5)
        )).isInstanceOf(RedisConnectionFailureException.class);

        assertThat(settings.getSettings()).isEqualTo(before);
    }

    @Test
    void changingOnlyFetchModeDoesNotRequireAvailableRedis() {
        PaymentSettings settings = new PaymentSettings();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.scan(any())).thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        PaymentCache cache = new PaymentCache(redis, new ObjectMapper(), settings, 300);
        PaymentAdminService admin = new PaymentAdminService(
                settings, cache, mock(AntifraudClient.class)
        );

        var result = admin.updateSettings(new PaymentSettingsDto("nplus1", null, null, null, null));

        assertThat(result.settings().fetchMode()).isEqualTo("nplus1");
        assertThat(result.settings().cacheEnabled()).isTrue();
        assertThat(result.settings().cacheInvalidationEnabled()).isTrue();
    }
}
