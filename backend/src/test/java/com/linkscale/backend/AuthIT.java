package com.linkscale.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM links").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    private HttpClient createSessionHttpClient(CookieManager cookieManager) {
        return HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private String fetchCsrfToken(HttpClient client) throws Exception {
        HttpRequest csrfReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/csrf"))
                .GET()
                .build();

        HttpResponse<String> csrfRes = client.send(csrfReq, HttpResponse.BodyHandlers.ofString());
        assertThat(csrfRes.statusCode()).isEqualTo(200);
        return JsonPath.read(csrfRes.body(), "$.token");
    }

    @Test
    void shouldProvideCsrfTokenEndpointAndCookieBeforeLogin() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createSessionHttpClient(cookieManager);

        HttpRequest csrfReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/csrf"))
                .GET()
                .build();

        HttpResponse<String> csrfRes = client.send(csrfReq, HttpResponse.BodyHandlers.ofString());
        assertThat(csrfRes.statusCode()).isEqualTo(200);

        String token = JsonPath.read(csrfRes.body(), "$.token");
        String headerName = JsonPath.read(csrfRes.body(), "$.header_name");
        String parameterName = JsonPath.read(csrfRes.body(), "$.parameter_name");

        assertThat(token).isNotBlank();
        assertThat(headerName).isEqualTo("X-XSRF-TOKEN");
        assertThat(parameterName).isEqualTo("_csrf");

        // Verify cookie is set
        List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
        assertThat(cookies).anyMatch(c -> c.getName().equals("XSRF-TOKEN") && c.getValue().equals(token));
    }

    @Test
    void shouldRejectRegistrationWhenCsrfTokenIsMissingWith403Forbidden() throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

        String payload = """
                {"email":"nocsrf@example.com","password":"password123"}
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(403);
    }

    @Test
    void shouldRejectRegistrationWhenCsrfTokenIsInvalidWith403Forbidden() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createSessionHttpClient(cookieManager);
        fetchCsrfToken(client);

        String payload = """
                {"email":"badcsrf@example.com","password":"password123"}
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", "invalid-token-value")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(403);
    }

    @Test
    void shouldRegisterNewUserSuccessfullyWithValidCsrfTokenAndNotExposeCredentials() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createSessionHttpClient(cookieManager);
        String csrfToken = fetchCsrfToken(client);

        String payload = """
                {"email":"User1@Example.COM","password":"password123"}
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(201);

        Number id = JsonPath.read(res.body(), "$.id");
        String email = JsonPath.read(res.body(), "$.email");
        String createdAt = JsonPath.read(res.body(), "$.created_at");

        assertThat(id).isNotNull();
        assertThat(email).isEqualTo("user1@example.com");
        assertThat(createdAt).isNotNull();
        assertThat(res.body()).doesNotContain("password");
        assertThat(res.body()).doesNotContain("password_hash");

        // Verify password hash in PostgreSQL is securely hashed and not plain text
        String passwordHash = jdbcClient.sql("SELECT password_hash FROM users WHERE id = :id")
                .param("id", id.longValue())
                .query(String.class)
                .single();

        assertThat(passwordHash).isNotBlank();
        assertThat(passwordHash).isNotEqualTo("password123");
        assertThat(passwordHash).contains("$");
    }

    @Test
    void shouldRejectDuplicateRegistrationWith409Conflict() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createSessionHttpClient(cookieManager);
        String csrfToken = fetchCsrfToken(client);

        String payload1 = """
                {"email":"duplicate@example.com","password":"password123"}
                """;

        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(payload1))
                .build();

        HttpResponse<String> res1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
        assertThat(res1.statusCode()).isEqualTo(201);

        // Attempting to register again with same email must return 409
        String payload2 = """
                {"email":"DUPLICATE@EXAMPLE.COM","password":"differentPassword"}
                """;

        HttpRequest req2 = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(payload2))
                .build();

        HttpResponse<String> res2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
        assertThat(res2.statusCode()).isEqualTo(409);

        Integer status = JsonPath.read(res2.body(), "$.status");
        String message = JsonPath.read(res2.body(), "$.message");
        assertThat(status).isEqualTo(409);
        assertThat(message).contains("already registered");
    }

    @Test
    void shouldRejectInvalidRegistrationDataWith400BadRequest() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createSessionHttpClient(cookieManager);
        String csrfToken = fetchCsrfToken(client);

        List<String> invalidPayloads = List.of(
                "{\"email\":\"not-an-email\",\"password\":\"password123\"}",
                "{\"email\":\"valid@example.com\",\"password\":\"short\"}",
                "{\"email\":\"\",\"password\":\"password123\"}",
                "{\"email\":\"valid@example.com\",\"password\":\"\"}"
        );

        for (String payload : invalidPayloads) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", csrfToken)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertThat(res.statusCode()).isEqualTo(400);
        }
    }

    @Test
    void shouldRejectLoginWhenCsrfTokenIsMissingWith403Forbidden() throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

        String payload = """
                {"email":"someone@example.com","password":"password123"}
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(403);
    }

    @Test
    void shouldRejectLoginWhenCsrfTokenIsInvalidWith403Forbidden() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createSessionHttpClient(cookieManager);
        fetchCsrfToken(client);

        String payload = """
                {"email":"someone@example.com","password":"password123"}
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", "completely-bogus-token")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(403);
    }

    @Test
    void shouldLoginSuccessfullyWithValidCsrfTokenAndEstablishSession() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createSessionHttpClient(cookieManager);
        String preLoginCsrf = fetchCsrfToken(client);

        // 1. Register
        String regPayload = """
                {"email":"loginuser@example.com","password":"mypassword123"}
                """;
        HttpRequest regReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", preLoginCsrf)
                .POST(HttpRequest.BodyPublishers.ofString(regPayload))
                .build();
        HttpResponse<String> regRes = client.send(regReq, HttpResponse.BodyHandlers.ofString());
        assertThat(regRes.statusCode()).isEqualTo(201);

        // 2. Login with valid CSRF token
        String loginPayload = """
                {"email":"LOGINUSER@EXAMPLE.COM","password":"mypassword123"}
                """;
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", preLoginCsrf)
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .build();

        HttpResponse<String> loginRes = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        assertThat(loginRes.statusCode()).isEqualTo(200);

        String email = JsonPath.read(loginRes.body(), "$.email");
        assertThat(email).isEqualTo("loginuser@example.com");

        // Verify session cookie was established
        List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
        assertThat(cookies).anyMatch(c -> c.getName().equals("JSESSIONID"));

        // Verify fresh CSRF token was issued on successful login and differs from pre-login token
        String freshCsrfToken = loginRes.headers().firstValue("X-XSRF-TOKEN").orElse(null);
        assertThat(freshCsrfToken).isNotNull();
        assertThat(freshCsrfToken).isNotEqualTo(preLoginCsrf);
    }

    @Test
    void shouldFailLoginWithInvalidPasswordReturning401WhenCsrfTokenIsValid() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createSessionHttpClient(cookieManager);
        String csrfToken = fetchCsrfToken(client);

        // 1. Register
        String regPayload = """
                {"email":"wrongpass@example.com","password":"correctpassword"}
                """;
        HttpRequest regReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(regPayload))
                .build();
        client.send(regReq, HttpResponse.BodyHandlers.discarding());

        // 2. Login with wrong password but valid CSRF token -> 401
        String loginPayload = """
                {"email":"wrongpass@example.com","password":"incorrectpassword"}
                """;
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .build();

        HttpResponse<String> loginRes = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        assertThat(loginRes.statusCode()).isEqualTo(401);
    }

    @Test
    void shouldFailLoginWithNonExistentEmailReturning401WhenCsrfTokenIsValid() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createSessionHttpClient(cookieManager);
        String csrfToken = fetchCsrfToken(client);

        String loginPayload = """
                {"email":"nonexistent@example.com","password":"somepassword"}
                """;
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .build();

        HttpResponse<String> loginRes = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        assertThat(loginRes.statusCode()).isEqualTo(401);
    }

    @Test
    void shouldRejectCurrentAccountEndpointWhenUnauthenticatedWith401() throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .GET()
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(401);

        Integer status = JsonPath.read(res.body(), "$.status");
        assertThat(status).isEqualTo(401);
    }

    @Test
    void shouldReturnCurrentAccountWhenAuthenticatedViaSession() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createSessionHttpClient(cookieManager);
        String preLoginCsrf = fetchCsrfToken(client);

        // 1. Register & Login
        client.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", preLoginCsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"accountuser@example.com\",\"password\":\"password123\"}"))
                .build(), HttpResponse.BodyHandlers.discarding());

        client.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", preLoginCsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"accountuser@example.com\",\"password\":\"password123\"}"))
                .build(), HttpResponse.BodyHandlers.discarding());

        // 2. Access current account endpoint
        HttpRequest meReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .GET()
                .build();

        HttpResponse<String> meRes = client.send(meReq, HttpResponse.BodyHandlers.ofString());
        assertThat(meRes.statusCode()).isEqualTo(200);

        String email = JsonPath.read(meRes.body(), "$.email");
        Number id = JsonPath.read(meRes.body(), "$.id");
        assertThat(email).isEqualTo("accountuser@example.com");
        assertThat(id).isNotNull();
    }

    @Test
    void shouldClearPreviousCsrfTokenOnSuccessfulLoginAndRequireFreshTokenForLogout() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createSessionHttpClient(cookieManager);
        String preLoginCsrf = fetchCsrfToken(client);

        // 1. Register
        client.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", preLoginCsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"freshtok@example.com\",\"password\":\"password123\"}"))
                .build(), HttpResponse.BodyHandlers.discarding());

        // 2. Login
        HttpResponse<String> loginRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", preLoginCsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"freshtok@example.com\",\"password\":\"password123\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(loginRes.statusCode()).isEqualTo(200);

        String freshSessionCsrf = loginRes.headers().firstValue("X-XSRF-TOKEN").orElseThrow();
        assertThat(freshSessionCsrf).isNotEqualTo(preLoginCsrf);

        // 3. Attempt logout using the STALE pre-login CSRF token -> MUST return 403 Forbidden
        HttpRequest logoutStaleCsrf = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/logout"))
                .header("X-XSRF-TOKEN", preLoginCsrf)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> staleRes = client.send(logoutStaleCsrf, HttpResponse.BodyHandlers.ofString());
        assertThat(staleRes.statusCode()).isEqualTo(403);

        // 4. Logout using the FRESH session CSRF token -> MUST return 200 OK
        HttpRequest logoutFreshCsrf = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/logout"))
                .header("X-XSRF-TOKEN", freshSessionCsrf)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> freshRes = client.send(logoutFreshCsrf, HttpResponse.BodyHandlers.ofString());
        assertThat(freshRes.statusCode()).isEqualTo(200);

        // 5. Verify user is now signed out
        HttpResponse<String> meRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(meRes.statusCode()).isEqualTo(401);
    }

    @Test
    void shouldClearPreviousCsrfTokenOnLogoutAndRequireFreshTokenForSubsequentRequests() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createSessionHttpClient(cookieManager);
        String preLoginCsrf = fetchCsrfToken(client);

        // 1. Register & Login
        client.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", preLoginCsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"postlogout@example.com\",\"password\":\"password123\"}"))
                .build(), HttpResponse.BodyHandlers.discarding());

        HttpResponse<String> loginRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", preLoginCsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"postlogout@example.com\",\"password\":\"password123\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());

        String sessionCsrf = loginRes.headers().firstValue("X-XSRF-TOKEN").orElseThrow();

        // 2. Logout with session CSRF token
        HttpResponse<String> logoutRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/logout"))
                .header("X-XSRF-TOKEN", sessionCsrf)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(logoutRes.statusCode()).isEqualTo(200);

        String postLogoutCsrf = logoutRes.headers().firstValue("X-XSRF-TOKEN").orElseThrow();
        assertThat(postLogoutCsrf).isNotEqualTo(sessionCsrf);

        // 3. Attempting to login with the OLD session CSRF token -> MUST return 403
        HttpRequest loginWithOldCsrf = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", sessionCsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"postlogout@example.com\",\"password\":\"password123\"}"))
                .build();
        HttpResponse<String> oldCsrfRes = client.send(loginWithOldCsrf, HttpResponse.BodyHandlers.ofString());
        assertThat(oldCsrfRes.statusCode()).isEqualTo(403);

        // 4. Logging in with the FRESH post-logout CSRF token -> MUST return 200 OK
        HttpRequest loginWithFreshCsrf = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", postLogoutCsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"postlogout@example.com\",\"password\":\"password123\"}"))
                .build();
        HttpResponse<String> newLoginRes = client.send(loginWithFreshCsrf, HttpResponse.BodyHandlers.ofString());
        assertThat(newLoginRes.statusCode()).isEqualTo(200);
    }

    @Test
    void shouldMaintainPublicRedirectsBeforeDuringAndAfterAuth() throws Exception {
        Long devOwnerId = jdbcClient.sql("INSERT INTO users (email, password_hash) VALUES ('dev@linkscale.local', '{noop}hash') RETURNING id")
                .query(Long.class).single();

        jdbcClient.sql("""
                INSERT INTO links (code, destination_url, owner_id, active, created_at, updated_at)
                VALUES ('pubredir', 'https://example.com/target', :ownerId, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).param("ownerId", devOwnerId).update();

        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createSessionHttpClient(cookieManager);

        // 1. Before login: public redirect returns 302
        HttpRequest getReq1 = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/r/pubredir"))
                .GET()
                .build();
        HttpResponse<Void> res1 = client.send(getReq1, HttpResponse.BodyHandlers.discarding());
        assertThat(res1.statusCode()).isEqualTo(302);
        assertThat(res1.headers().firstValue("Location")).contains("https://example.com/target");

        // 2. Register & Login
        String csrfToken = fetchCsrfToken(client);
        client.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"rediruser@example.com\",\"password\":\"password123\"}"))
                .build(), HttpResponse.BodyHandlers.discarding());

        HttpResponse<String> loginRes = client.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"rediruser@example.com\",\"password\":\"password123\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());

        // 3. During authenticated session: public redirect returns 302
        HttpResponse<Void> res2 = client.send(getReq1, HttpResponse.BodyHandlers.discarding());
        assertThat(res2.statusCode()).isEqualTo(302);
        assertThat(res2.headers().firstValue("Location")).contains("https://example.com/target");

        // 4. Logout with fresh session CSRF token
        String sessionCsrf = loginRes.headers().firstValue("X-XSRF-TOKEN").orElseThrow();
        client.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/logout"))
                .header("X-XSRF-TOKEN", sessionCsrf)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.discarding());

        // 5. After logout: public redirect returns 302
        HttpResponse<Void> res3 = client.send(getReq1, HttpResponse.BodyHandlers.discarding());
        assertThat(res3.statusCode()).isEqualTo(302);
        assertThat(res3.headers().firstValue("Location")).contains("https://example.com/target");
    }

    @Test
    void shouldMaintainPhase1bDevLinkCreationFlowWithoutAuthOrCsrf() throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

        String payload = """
                {"destination_url":"https://spring.io/projects/spring-boot"}
                """;

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/links"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> postRes = client.send(postReq, HttpResponse.BodyHandlers.ofString());
        assertThat(postRes.statusCode()).isEqualTo(201);

        String code = JsonPath.read(postRes.body(), "$.code");
        assertThat(code).isNotBlank();

        // Redirect works
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/r/" + code))
                .GET()
                .build();
        HttpResponse<Void> getRes = client.send(getReq, HttpResponse.BodyHandlers.discarding());
        assertThat(getRes.statusCode()).isEqualTo(302);
        assertThat(getRes.headers().firstValue("Location")).contains("https://spring.io/projects/spring-boot");
    }
}
