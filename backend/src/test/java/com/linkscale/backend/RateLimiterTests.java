package com.linkscale.backend;

import com.linkscale.backend.exception.GlobalExceptionHandler;
import com.linkscale.backend.exception.RateLimitException;
import com.linkscale.backend.service.RateLimiter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimiterTests {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RateLimiter limiter = new RateLimiter(redis, true);

    @Test
    void rateLimitsEachActionByItsOwnKeyAndBudget() {
        stubBucket("login-ip:127.0.0.1", "10", 13L);
        stubBucket("register-ip:127.0.0.1", "5", 0L);
        stubBucket("create-owner:42", "30", 27L);
        stubBucket("create-owner:43", "30", 0L);

        assertThatThrownBy(() -> limiter.checkLogin("127.0.0.1"))
                .isInstanceOfSatisfying(RateLimitException.class, exception -> {
                    var response = new GlobalExceptionHandler().handleRateLimit(exception);
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("13");
                });
        limiter.checkRegistration("127.0.0.1");
        assertThatThrownBy(() -> limiter.checkLinkCreation(42L))
                .isInstanceOfSatisfying(RateLimitException.class,
                        exception -> assertThat(exception.retryAfterSeconds()).isEqualTo(27));
        limiter.checkLinkCreation(43L);
    }

    @Test
    void redisFailureReturnsServiceUnavailable() {
        when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of("linkscale:rate-limit:login-ip:127.0.0.1")),
                eq("10"), eq("60000"))).thenThrow(new RedisConnectionFailureException("offline"));

        assertThatThrownBy(() -> limiter.checkLogin("127.0.0.1"))
                .isInstanceOfSatisfying(RateLimitException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void disabledLimiterSkipsRedis() {
        RateLimiter disabled = new RateLimiter(redis, false);
        disabled.checkLogin("127.0.0.1");
        disabled.checkRegistration("127.0.0.1");
        disabled.checkLinkCreation(42L);
        verifyNoInteractions(redis);
    }

    private void stubBucket(String bucket, String limit, long retryAfter) {
        when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of("linkscale:rate-limit:" + bucket)),
                eq(limit), eq("60000"))).thenReturn(retryAfter);
    }
}
