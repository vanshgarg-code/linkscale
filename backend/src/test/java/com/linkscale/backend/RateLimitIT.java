package com.linkscale.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "linkscale.rate-limit.enabled=true")
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, RateLimitIT.RedisConfiguration.class})
class RateLimitIT {

    private static final String CLIENT_ADDRESS = "127.0.0.1";
    private static final String LOGIN_KEY = "linkscale:rate-limit:login-ip:" + CLIENT_ADDRESS;

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void shouldThrottleRepeatedInvalidLoginsUntilRedisWindowExpires() throws Exception {
        String baseUrl = "http://" + CLIENT_ADDRESS + ":" + port;
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

        try (HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build()) {
            HttpRequest csrfRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/auth/csrf"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> csrf = client.send(csrfRequest, HttpResponse.BodyHandlers.ofString());
            assertThat(csrf.statusCode()).isEqualTo(200);
            String token = JsonPath.read(csrf.body(), "$.token");
            assertThat(token).isNotBlank();

            HttpRequest login = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/auth/login"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", token)
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"email":"missing-user@example.com","password":"invalid-password-123"}
                            """))
                    .build();

            for (int attempt = 1; attempt <= 10; attempt++) {
                assertThat(client.send(login, HttpResponse.BodyHandlers.discarding()).statusCode())
                        .as("invalid login attempt %s", attempt)
                        .isEqualTo(401);
            }

            HttpResponse<Void> blocked = client.send(login, HttpResponse.BodyHandlers.discarding());
            assertThat(blocked.statusCode()).isEqualTo(429);
            assertThat(blocked.headers().firstValueAsLong("Retry-After").orElse(0))
                    .as("Retry-After in seconds")
                    .isBetween(1L, 60L);
            assertThat(redis.getExpire(LOGIN_KEY, TimeUnit.MILLISECONDS)).isBetween(1L, 60_000L);

            // Let Redis expire the real counter without waiting the full production minute.
            assertThat(redis.expire(LOGIN_KEY, Duration.ofMillis(200))).isTrue();
            await().atMost(Duration.ofSeconds(5)).until(() -> Boolean.FALSE.equals(redis.hasKey(LOGIN_KEY)));

            assertThat(client.send(login, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisConfiguration {

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redisContainer() {
            return new GenericContainer<>(DockerImageName.parse("redis:8.2.11-alpine"))
                    .withExposedPorts(6379);
        }
    }
}
