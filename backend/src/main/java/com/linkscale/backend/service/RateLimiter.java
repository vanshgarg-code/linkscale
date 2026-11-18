package com.linkscale.backend.service;

import com.linkscale.backend.exception.RateLimitException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RateLimiter {

    // Run the counter check and expiry together so parallel requests share one budget.
    private static final RedisScript<Long> ACQUIRE = RedisScript.of("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local remaining = redis.call('PTTL', KEYS[1])

            if remaining < 0 then
                redis.call('SET', KEYS[1], '1', 'PX', window)
                return 0
            end

            if count >= limit then
                return math.max(1, math.ceil(remaining / 1000))
            end

            redis.call('INCR', KEYS[1])
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;

    public RateLimiter(StringRedisTemplate redis, @Value("${linkscale.rate-limit.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.enabled = enabled;
    }

    public void checkLogin(String remoteAddress) {
        acquire("login-ip:" + remoteAddress, 10, 60_000);
    }

    public void checkRegistration(String remoteAddress) {
        acquire("register-ip:" + remoteAddress, 5, 60_000);
    }

    public void checkLinkCreation(Long ownerId) {
        acquire("create-owner:" + ownerId, 30, 60_000);
    }

    private void acquire(String bucket, int limit, int windowMillis) {
        if (!enabled) return;

        Long retryAfter;
        try {
            retryAfter = redis.execute(ACQUIRE, List.of("linkscale:rate-limit:" + bucket),
                    Integer.toString(limit), Integer.toString(windowMillis));
        } catch (DataAccessException exception) {
            throw unavailable();
        }

        if (retryAfter == null) throw unavailable();
        if (retryAfter > 0) {
            throw new RateLimitException(HttpStatus.TOO_MANY_REQUESTS, retryAfter,
                    "Too many requests. Try again in " + retryAfter + " seconds.");
        }
    }

    private RateLimitException unavailable() {
        return new RateLimitException(HttpStatus.SERVICE_UNAVAILABLE, 1,
                "This action is temporarily unavailable. Try again shortly.");
    }
}
