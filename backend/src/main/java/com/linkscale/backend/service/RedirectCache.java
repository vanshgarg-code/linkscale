package com.linkscale.backend.service;

import com.linkscale.backend.domain.RedirectTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedirectCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedirectCache.class);
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    // A restart cannot pick up entries from a previous instance that failed to evict.
    private final String keyPrefix = "linkscale:redirect:" + UUID.randomUUID() + ":";
    private boolean enabled;

    public RedirectCache(StringRedisTemplate redis, @Value("${linkscale.redirect-cache.enabled:false}") boolean enabled) {
        this.redis = redis;
        this.enabled = enabled;
    }

    Optional<RedirectTarget> get(String code) {
        if (!enabled) return Optional.empty();

        try {
            String value = redis.opsForValue().get(keyPrefix + code);
            if (value == null) return Optional.empty();

            String[] fields = value.split("\\n", 3);
            if (fields.length != 3 || fields[2].isBlank()) return Optional.empty();
            OffsetDateTime expiresAt = fields[1].isEmpty() ? null : OffsetDateTime.parse(fields[1]);
            return Optional.of(new RedirectTarget(Long.valueOf(fields[0]), fields[2], expiresAt));
        } catch (DataAccessException | NumberFormatException | DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    void put(String code, RedirectTarget target) {
        if (!enabled) return;

        Duration ttl = TTL;
        if (target.expiresAt() != null) {
            Duration remaining = Duration.between(OffsetDateTime.now(), target.expiresAt());
            if (remaining.toMillis() < 1) return;
            if (remaining.compareTo(ttl) < 0) ttl = remaining;
        }

        String expiry = target.expiresAt() == null ? "" : target.expiresAt().toString();
        String value = target.linkId() + "\n" + expiry + "\n" + target.destinationUrl();
        try {
            redis.opsForValue().set(keyPrefix + code, value, ttl);
        } catch (DataAccessException exception) {
            // PostgreSQL already supplied the redirect.
        }
    }

    void evict(String code) {
        if (!enabled) return;

        try {
            redis.delete(keyPrefix + code);
        } catch (DataAccessException exception) {
            // Treat old entries as unsafe if Redis comes back after this mutation.
            enabled = false;
            LOGGER.warn("Redirect cache disabled until restart after a failed eviction", exception);
        }
    }
}
