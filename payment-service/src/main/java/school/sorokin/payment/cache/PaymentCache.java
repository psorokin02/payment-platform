package school.sorokin.payment.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import school.sorokin.payment.api.dto.PaymentDto;
import school.sorokin.payment.admin.PaymentSettings;

import java.time.Duration;
import java.util.Optional;

/** Cache-aside для карточки платежа: хранит копию DTO и удаляет её после изменения. */
@Component
public class PaymentCache {

    private static final Logger log = LoggerFactory.getLogger(PaymentCache.class);
    private static final String KEY_PREFIX = "payment:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentSettings runtimeState;
    // TTL — страховка на случай пропущенной инвалидации, но не замена eviction после commit.
    private final Duration timeToLive;

    public PaymentCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            PaymentSettings runtimeState,
            @Value("${app.cache.payment-ttl-seconds}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.runtimeState = runtimeState;
        this.timeToLive = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<PaymentDto> findPayment(long id) {
        if (!runtimeState.isCacheEnabled()) {
            log.info("Redis bypass paymentId={} cacheEnabled=false", id);
            return Optional.empty();
        }

        try {
            String value = redisTemplate.opsForValue().get(key(id));
            if (value != null) {
                PaymentDto payment = objectMapper.readValue(value, PaymentDto.class);
                log.info("Redis cache hit paymentId={} status={}", id, payment.status());
                return Optional.of(payment);
            }
        } catch (Exception exception) {
            // Cache-aside продолжает чтение из БД при недоступном Redis или старом JSON.
            log.warn("Redis read unavailable paymentId={}; reading PostgreSQL: {}", id, exception.toString());
        }

        log.info("Redis cache miss paymentId={}; reading PostgreSQL", id);
        return Optional.empty();
    }

    public void putPayment(PaymentDto payment) {
        if (!runtimeState.isCacheEnabled()) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(
                    key(payment.id()),
                    objectMapper.writeValueAsString(payment),
                    timeToLive
            );
            log.info("Redis cached paymentId={} status={} ttlSeconds={}", payment.id(), payment.status(), timeToLive.toSeconds());
        } catch (Exception exception) {
            // Платёж остаётся доступен из БД, даже если Redis временно недоступен.
            log.warn("Redis cache write failed paymentId={}: {}", payment.id(), exception.toString());
        }
    }

    public void invalidatePaymentCache(long id) {
        if (!runtimeState.isCacheInvalidationEnabled()) {
            log.warn("Redis invalidation skipped paymentId={} cacheInvalidationEnabled=false; cached status may be outdated", id);
            return;
        }
        try {
            redisTemplate.delete(key(id));
            log.info("Redis invalidated after commit paymentId={}", id);
        } catch (Exception exception) {
            // Commit платежа уже завершён; устаревшую копию ограничивает TTL.
            log.warn("Redis invalidation failed paymentId={}: {}", id, exception.toString());
        }
    }

    public void clearDemoCache() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(KEY_PREFIX + "*")
                .count(100)
                .build();

        // Только собственные ключи стенда. FLUSHDB затронул бы чужие данные.
        try (var cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                redisTemplate.delete(cursor.next());
            }
        }
        log.info("Redis payment cache cleared");
    }

    private String key(long id) {
        return KEY_PREFIX + id;
    }
}
