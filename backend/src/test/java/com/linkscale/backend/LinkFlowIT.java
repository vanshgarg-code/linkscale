package com.linkscale.backend;

import com.jayway.jsonpath.JsonPath;
import com.linkscale.backend.config.LinkScaleProperties;
import com.linkscale.backend.dto.CreateLinkRequest;
import com.linkscale.backend.dto.LinkResponse;
import com.linkscale.backend.exception.CodeGenerationExhaustedException;
import com.linkscale.backend.exception.UnauthenticatedCreationDisabledException;
import com.linkscale.backend.repository.LinkRepository;
import com.linkscale.backend.service.CodeGenerator;
import com.linkscale.backend.service.CustomAliasValidator;
import com.linkscale.backend.service.DevFixtureService;
import com.linkscale.backend.service.LinkService;
import com.linkscale.backend.service.UrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LinkFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private DevFixtureService devFixtureService;

    @Autowired
    private UrlValidator urlValidator;

    @Autowired
    private CustomAliasValidator customAliasValidator;

    @Autowired
    private LinkScaleProperties properties;

    private HttpClient httpClient;
    private CookieManager cookieManager;
    private String csrfToken;

    @BeforeEach
    void setUp() throws Exception {
        jdbcClient.sql("DELETE FROM links").update();
        jdbcClient.sql("DELETE FROM users").update();

        cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        // 1. Fetch pre-login CSRF token
        HttpRequest csrfReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/csrf"))
                .GET()
                .build();
        HttpResponse<String> csrfRes = httpClient.send(csrfReq, HttpResponse.BodyHandlers.ofString());
        String preLoginCsrf = JsonPath.read(csrfRes.body(), "$.token");

        // 2. Register test account
        HttpRequest regReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", preLoginCsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"linkflow@linkscale.local\",\"password\":\"password123\"}"))
                .build();
        httpClient.send(regReq, HttpResponse.BodyHandlers.discarding());

        // 3. Login to establish session
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", preLoginCsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"linkflow@linkscale.local\",\"password\":\"password123\"}"))
                .build();
        HttpResponse<String> loginRes = httpClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
        csrfToken = loginRes.headers().firstValue("X-XSRF-TOKEN").orElse(preLoginCsrf);
    }

    private long clickCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM click_events").query(Long.class).single();
    }

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    private String buildJson(String destinationUrl, String customAlias, String title) {
        StringBuilder sb = new StringBuilder("{");
        if (destinationUrl != null) {
            sb.append("\"destination_url\":\"").append(destinationUrl).append("\"");
        }
        if (customAlias != null) {
            if (sb.length() > 1)
                sb.append(",");
            sb.append("\"custom_alias\":\"").append(customAlias).append("\"");
        }
        if (title != null) {
            if (sb.length() > 1)
                sb.append(",");
            sb.append("\"title\":\"").append(title).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    @Test
    void shouldCreateLinkWithGeneratedEightCharBase36CodeAndRedirect302() throws Exception {
        String requestJson = buildJson("https://example.com/target", null, "Example Target");

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/links"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> postRes = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());
        assertThat(postRes.statusCode()).isEqualTo(201);

        Number linkId = JsonPath.read(postRes.body(), "$.id");
        String code = JsonPath.read(postRes.body(), "$.code");
        String destUrl = JsonPath.read(postRes.body(), "$.destination_url");
        String shortUrl = JsonPath.read(postRes.body(), "$.short_url");

        assertThat(code).matches("^[a-z0-9]{8}$");
        assertThat(postRes.headers().firstValue("Location")).contains("/r/" + code);
        assertThat(destUrl).isEqualTo("https://example.com/target");
        assertThat(shortUrl).endsWith("/r/" + code);

        // Use a client without cookies to exercise the public route.
        HttpClient visitor = HttpClient.newHttpClient();
        URI redirectUri = URI.create(getBaseUrl() + "/r/" + code);
        assertThat(clickCount()).isZero();

        HttpRequest headRequest = HttpRequest.newBuilder(redirectUri)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        assertThat(visitor.send(headRequest, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(302);
        assertThat(clickCount()).isZero();

        HttpRequest getRequest = HttpRequest.newBuilder(redirectUri).GET().build();
        HttpResponse<Void> getRes = visitor.send(getRequest, HttpResponse.BodyHandlers.discarding());
        assertThat(getRes.statusCode()).isEqualTo(302);
        assertThat(getRes.headers().firstValue("Location")).contains("https://example.com/target");
        assertThat(getRes.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(clickCount()).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT link_id FROM click_events WHERE clicked_at IS NOT NULL")
                .query(Long.class).single()).isEqualTo(linkId.longValue());

        assertThat(visitor.send(getRequest, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(302);
        assertThat(clickCount()).isEqualTo(2);
    }

    @Test
    void shouldCreateLinkWithValidCustomAliasAndRedirect302() throws Exception {
        String requestJson = buildJson("https://spring.io/projects", "spring-boot", "Spring Boot");

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/links"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> postRes = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());
        assertThat(postRes.statusCode()).isEqualTo(201);

        String code = JsonPath.read(postRes.body(), "$.code");
        assertThat(code).isEqualTo("spring-boot");

        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/r/spring-boot"))
                .GET()
                .build();

        HttpResponse<Void> getRes = httpClient.send(getReq, HttpResponse.BodyHandlers.discarding());
        assertThat(getRes.statusCode()).isEqualTo(302);
        assertThat(getRes.headers().firstValue("Location")).contains("https://spring.io/projects");
        assertThat(getRes.headers().firstValue("Cache-Control")).contains("no-store");
    }

    @Test
    void shouldRejectDuplicateCustomAliasWith409Conflict() throws Exception {
        String firstJson = buildJson("https://example.com/first", "my-alias", "First");

        HttpRequest postReq1 = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/links"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(firstJson))
                .build();

        HttpResponse<String> postRes1 = httpClient.send(postReq1, HttpResponse.BodyHandlers.ofString());
        assertThat(postRes1.statusCode()).isEqualTo(201);

        String secondJson = buildJson("https://example.com/second", "my-alias", "Second");

        HttpRequest postReq2 = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/links"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(secondJson))
                .build();

        HttpResponse<String> postRes2 = httpClient.send(postReq2, HttpResponse.BodyHandlers.ofString());
        assertThat(postRes2.statusCode()).isEqualTo(409);

        Integer status = JsonPath.read(postRes2.body(), "$.status");
        String message = JsonPath.read(postRes2.body(), "$.message");
        assertThat(status).isEqualTo(409);
        assertThat(message).contains("already in use");
    }

    @Test
    void shouldHandleConcurrentRequestsForSameCustomAliasYieldingOne201AndOne409() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        String requestJson = buildJson("https://example.com/race", "race-alias", "Race");

        Callable<Integer> task = () -> {
            barrier.await();
            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/links"))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", csrfToken)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();
            HttpResponse<String> res = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());
            return res.statusCode();
        };

        Future<Integer> f1 = executor.submit(task);
        Future<Integer> f2 = executor.submit(task);

        List<Integer> statuses = List.of(f1.get(5, TimeUnit.SECONDS), f2.get(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }

    @Test
    void shouldForceGeneratedCodeCollisionDeterministicallyAndSucceedOnNextAttempt() {
        Long ownerId = devFixtureService.getOrCreateDevFixtureUserId();
        linkRepository.insertOnConflictDoNothing("collid01", "https://existing.com", ownerId, "Existing", null);

        AtomicInteger counter = new AtomicInteger(0);
        CodeGenerator deterministicGenerator = new CodeGenerator() {
            @Override
            public String generateCode(int length) {
                return counter.getAndIncrement() == 0 ? "collid01" : "collid02";
            }
        };

        LinkService testLinkService = new LinkService(
                linkRepository, urlValidator, customAliasValidator,
                deterministicGenerator, properties);

        CreateLinkRequest req = new CreateLinkRequest("https://fresh-target.com", null, "Retry Test", null);
        LinkResponse response = testLinkService.createLink(req, ownerId);

        assertThat(response.code()).isEqualTo("collid02");
        assertThat(response.destinationUrl()).isEqualTo("https://fresh-target.com");
    }

    @Test
    void shouldReturnControlled503WhenGeneratedCodeAttemptsAreExhausted() {
        Long ownerId = devFixtureService.getOrCreateDevFixtureUserId();
        linkRepository.insertOnConflictDoNothing("exhausted", "https://existing.com", ownerId, "Existing", null);

        CodeGenerator alwaysCollidingGenerator = new CodeGenerator() {
            @Override
            public String generateCode(int length) {
                return "exhausted";
            }
        };

        LinkService testLinkService = new LinkService(
                linkRepository, urlValidator, customAliasValidator,
                alwaysCollidingGenerator, properties);

        CreateLinkRequest req = new CreateLinkRequest("https://fresh-target.com", null, "Exhaust Test", null);
        assertThatThrownBy(() -> testLinkService.createLink(req, ownerId))
                .isInstanceOf(CodeGenerationExhaustedException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    void shouldRejectReservedCustomAliasWith400BadRequest() throws Exception {
        List<String> reservedAliases = List.of("admin", "api", "actuator", "login", "swagger");

        for (String reserved : reservedAliases) {
            String requestJson = buildJson("https://example.com/test", reserved, "Title");

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/links"))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", csrfToken)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> postRes = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());
            assertThat(postRes.statusCode()).isEqualTo(400);

            String message = JsonPath.read(postRes.body(), "$.message");
            assertThat(message).contains("reserved keyword");
        }
    }

    @Test
    void shouldRejectInvalidCustomAliasPatternWith400BadRequest() throws Exception {
        List<String> invalidAliases = List.of(
                "-leading-hyphen",
                "trailing-hyphen-",
                "r",
                "ab", // too short (< 3 chars)
                "a".repeat(33), // too long (> 32 chars)
                "has_underscore", // only lowercase alphanumeric and internal hyphens allowed
                "has.dot",
                "has spaces");

        for (String invalid : invalidAliases) {
            String requestJson = buildJson("https://example.com/test", invalid, "Title");

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/links"))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", csrfToken)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> postRes = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());
            assertThat(postRes.statusCode()).isEqualTo(400);
        }
    }

    @Test
    void shouldRejectInvalidDestinationUrlsWith400BadRequest() throws Exception {
        List<String> invalidUrls = List.of(
                "ftp://files.example.com",
                "javascript:alert(1)",
                "file:///etc/passwd",
                "http://", // missing host
                "https://user:pass@example.com", // embedded credentials
                "not-a-valid-url");

        for (String invalidUrl : invalidUrls) {
            String requestJson = buildJson(invalidUrl, null, "Invalid URL Test");

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/links"))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", csrfToken)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> postRes = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());
            assertThat(postRes.statusCode()).isEqualTo(400);
        }
    }

    @Test
    void shouldRejectRedirectLoopsPointedAtLinkScaleWith400BadRequest() throws Exception {
        List<String> loopUrls = List.of(
                "http://localhost:8080/r/infinite-loop",
                "http://127.0.0.1:8080/r/infinite-loop",
                "http://localhost:8080/api/v1/links");

        for (String loopUrl : loopUrls) {
            String requestJson = buildJson(loopUrl, null, "Loop Test");

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/links"))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", csrfToken)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> postRes = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());
            assertThat(postRes.statusCode()).isEqualTo(400);

            String message = JsonPath.read(postRes.body(), "$.message");
            assertThat(message).contains("redirect or API endpoints");
        }
    }

    @Test
    void shouldReturn404WithNoStoreForMissingLink() throws Exception {
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/r/missing123"))
                .GET()
                .build();

        HttpResponse<Void> getRes = httpClient.send(getReq, HttpResponse.BodyHandlers.discarding());
        assertThat(getRes.statusCode()).isEqualTo(404);
        assertThat(getRes.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(clickCount()).isZero();
    }

    @Test
    void shouldReturn404WithNoStoreForInactiveLink() throws Exception {
        Long ownerId = devFixtureService.getOrCreateDevFixtureUserId();
        jdbcClient.sql("""
                INSERT INTO links (code, destination_url, owner_id, active, created_at, updated_at)
                VALUES ('inactive1', 'https://example.com/inactive', ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).param(ownerId).update();

        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/r/inactive1"))
                .GET()
                .build();

        HttpResponse<Void> getRes = httpClient.send(getReq, HttpResponse.BodyHandlers.discarding());
        assertThat(getRes.statusCode()).isEqualTo(404);
        assertThat(getRes.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(clickCount()).isZero();
    }

    @Test
    void shouldReturn404WithNoStoreForDeletedLink() throws Exception {
        Long ownerId = devFixtureService.getOrCreateDevFixtureUserId();
        jdbcClient
                .sql("""
                        INSERT INTO links (code, destination_url, owner_id, active, deleted_at, created_at, updated_at)
                        VALUES ('deleted1', 'https://example.com/deleted', ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .param(ownerId).update();

        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/r/deleted1"))
                .GET()
                .build();

        HttpResponse<Void> getRes = httpClient.send(getReq, HttpResponse.BodyHandlers.discarding());
        assertThat(getRes.statusCode()).isEqualTo(404);
        assertThat(getRes.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(clickCount()).isZero();
    }

    @Test
    void shouldReturn410WithNoStoreForOtherwiseActiveExpiredLink() throws Exception {
        Long ownerId = devFixtureService.getOrCreateDevFixtureUserId();
        jdbcClient
                .sql("""
                        INSERT INTO links (code, destination_url, owner_id, active, expires_at, created_at, updated_at)
                        VALUES ('expired1', 'https://example.com/expired', ?, true, CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .param(ownerId).update();

        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/r/expired1"))
                .GET()
                .build();

        HttpResponse<Void> getRes = httpClient.send(getReq, HttpResponse.BodyHandlers.discarding());
        assertThat(getRes.statusCode()).isEqualTo(410);
        assertThat(getRes.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(clickCount()).isZero();
    }

    @Test
    void shouldMakeFixtureCreationIdempotentUnderConcurrentRequests() throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                barrier.await();
                return devFixtureService.getOrCreateDevFixtureUserId();
            });
        }

        List<Future<Long>> futures = executor.invokeAll(tasks);
        List<Long> userIds = new ArrayList<>();
        for (Future<Long> future : futures) {
            userIds.add(future.get(5, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertThat(userIds).hasSize(threads);
        Long firstId = userIds.getFirst();
        assertThat(userIds).allMatch(id -> id.equals(firstId));
    }

    @Test
    void shouldRejectCreationWhenUnauthenticatedCreationDisabledByDefault() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        LinkScaleProperties defaultProps = new LinkScaleProperties(
                "https://linkscale.io",
                new LinkScaleProperties.DevMode(false, "dev@linkscale.local"),
                new LinkScaleProperties.Generator(8, 5));

        DevFixtureService prodFixtureService = new DevFixtureService(null, defaultProps, prodEnv);

        assertThatThrownBy(prodFixtureService::getOrCreateDevFixtureUserId)
                .isInstanceOf(UnauthenticatedCreationDisabledException.class)
                .hasMessageContaining("disabled");
    }
}
