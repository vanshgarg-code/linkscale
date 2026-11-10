package com.linkscale.backend;

import com.jayway.jsonpath.JsonPath;
import com.linkscale.backend.repository.LinkRepository;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LinkManagementIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private LinkRepository linkRepository;

    private HttpClient anonymousClient;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM links").update();
        jdbcClient.sql("DELETE FROM users").update();

        anonymousClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    private static class UserSession {
        HttpClient client;
        CookieManager cookieManager;
        String csrfToken;
        String email;
        Long userId;
    }

    private UserSession createUserSession(String email, String password) throws Exception {
        UserSession session = new UserSession();
        session.email = email;
        session.cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        session.client = HttpClient.newBuilder()
                .cookieHandler(session.cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        // 1. Fetch pre-login CSRF token
        HttpRequest csrfReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/csrf"))
                .GET()
                .build();
        HttpResponse<String> csrfRes = session.client.send(csrfReq, HttpResponse.BodyHandlers.ofString());
        String preLoginCsrf = JsonPath.read(csrfRes.body(), "$.token");

        // 2. Register
        HttpRequest regReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", preLoginCsrf)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .build();
        HttpResponse<String> regRes = session.client.send(regReq, HttpResponse.BodyHandlers.ofString());
        assertThat(regRes.statusCode()).isEqualTo(201);

        // 3. Login
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", preLoginCsrf)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .build();
        HttpResponse<String> loginRes = session.client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        assertThat(loginRes.statusCode()).isEqualTo(200);

        session.csrfToken = loginRes.headers().firstValue("X-XSRF-TOKEN").orElse(preLoginCsrf);
        session.userId = ((Number) JsonPath.read(loginRes.body(), "$.id")).longValue();

        return session;
    }

    private HttpResponse<String> postLink(UserSession session, String body, String csrfHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/links"))
                .header("Content-Type", "application/json");

        if (csrfHeader != null) {
            builder.header("X-XSRF-TOKEN", csrfHeader);
        }

        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpClient client = (session != null) ? session.client : anonymousClient;
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getLinks(UserSession session, String queryParams) throws Exception {
        String uri = getBaseUrl() + "/api/v1/links" + (queryParams != null && !queryParams.isEmpty() ? "?" + queryParams : "");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .GET()
                .build();
        HttpClient client = (session != null) ? session.client : anonymousClient;
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getLink(UserSession session, Long id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/links/" + id))
                .GET()
                .build();
        HttpClient client = (session != null) ? session.client : anonymousClient;
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patchLink(UserSession session, Long id, String body, String csrfHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/links/" + id))
                .header("Content-Type", "application/json");

        if (csrfHeader != null) {
            builder.header("X-XSRF-TOKEN", csrfHeader);
        }

        HttpRequest request = builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build();
        HttpClient client = (session != null) ? session.client : anonymousClient;
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> deleteLink(UserSession session, Long id, String csrfHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/links/" + id));

        if (csrfHeader != null) {
            builder.header("X-XSRF-TOKEN", csrfHeader);
        }

        HttpRequest request = builder.DELETE().build();
        HttpClient client = (session != null) ? session.client : anonymousClient;
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void shouldRejectUnauthenticatedAccessToLinkEndpointsWith401() throws Exception {
        HttpResponse<String> listRes = getLinks(null, null);
        assertThat(listRes.statusCode()).isEqualTo(401);

        HttpResponse<String> getRes = getLink(null, 1L);
        assertThat(getRes.statusCode()).isEqualTo(401);

        // For mutating endpoints, provide valid CSRF token so request reaches authentication filter
        HttpRequest csrfReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/csrf"))
                .GET()
                .build();
        HttpResponse<String> csrfRes = anonymousClient.send(csrfReq, HttpResponse.BodyHandlers.ofString());
        String preLoginCsrf = JsonPath.read(csrfRes.body(), "$.token");

        HttpResponse<String> postRes = postLink(null, "{\"destination_url\":\"https://example.com\"}", preLoginCsrf);
        assertThat(postRes.statusCode()).isEqualTo(401);

        HttpResponse<String> patchRes = patchLink(null, 1L, "{\"title\":\"New\"}", preLoginCsrf);
        assertThat(patchRes.statusCode()).isEqualTo(401);

        HttpResponse<String> deleteRes = deleteLink(null, 1L, preLoginCsrf);
        assertThat(deleteRes.statusCode()).isEqualTo(401);
    }

    @Test
    void shouldRejectMutatingLinkRequestsWithoutCsrfWith403() throws Exception {
        UserSession user = createUserSession("csrf-user@linkscale.local", "password123");

        // POST without CSRF
        HttpResponse<String> postRes = postLink(user, "{\"destination_url\":\"https://example.com\"}", null);
        assertThat(postRes.statusCode()).isEqualTo(403);

        // POST with valid CSRF to create a link for patch/delete testing
        HttpResponse<String> createRes = postLink(user, "{\"destination_url\":\"https://example.com\"}", user.csrfToken);
        assertThat(createRes.statusCode()).isEqualTo(201);
        Long linkId = ((Number) JsonPath.read(createRes.body(), "$.id")).longValue();

        // PATCH without CSRF
        HttpResponse<String> patchRes = patchLink(user, linkId, "{\"title\":\"New\"}", null);
        assertThat(patchRes.statusCode()).isEqualTo(403);

        // DELETE without CSRF
        HttpResponse<String> deleteRes = deleteLink(user, linkId, null);
        assertThat(deleteRes.statusCode()).isEqualTo(403);
    }

    @Test
    void shouldCreateLinkAssignedToAuthenticatedUser() throws Exception {
        UserSession user = createUserSession("owner-user@linkscale.local", "password123");

        String body = """
                {
                    "destination_url": "https://example.com/target",
                    "custom_alias": "owner-link",
                    "title": "Owner Test Link"
                }
                """;

        HttpResponse<String> res = postLink(user, body, user.csrfToken);
        assertThat(res.statusCode()).isEqualTo(201);

        Long linkId = ((Number) JsonPath.read(res.body(), "$.id")).longValue();
        String code = JsonPath.read(res.body(), "$.code");
        String destinationUrl = JsonPath.read(res.body(), "$.destination_url");
        String title = JsonPath.read(res.body(), "$.title");
        String shortUrl = JsonPath.read(res.body(), "$.short_url");
        String createdAt = JsonPath.read(res.body(), "$.created_at");
        String updatedAt = JsonPath.read(res.body(), "$.updated_at");
        Boolean active = JsonPath.read(res.body(), "$.active");

        assertThat(code).isEqualTo("owner-link");
        assertThat(destinationUrl).isEqualTo("https://example.com/target");
        assertThat(title).isEqualTo("Owner Test Link");
        assertThat(shortUrl).isEqualTo("http://localhost:8080/r/owner-link");
        assertThat(createdAt).isNotNull();
        assertThat(updatedAt).isNotNull();
        assertThat(active).isTrue();

        // Verify in DB that owner_id matches user.userId
        Long dbOwnerId = jdbcClient.sql("SELECT owner_id FROM links WHERE id = ?")
                .param(linkId)
                .query(Long.class)
                .single();
        assertThat(dbOwnerId).isEqualTo(user.userId);
    }

    @Test
    void shouldIsolateLinksBetweenDifferentAccountsInListAndDetail() throws Exception {
        UserSession userA = createUserSession("user-a@linkscale.local", "password123");
        UserSession userB = createUserSession("user-b@linkscale.local", "password123");

        // User A creates 2 links
        HttpResponse<String> createA1 = postLink(userA, "{\"destination_url\":\"https://example.com/a1\",\"custom_alias\":\"user-a-1\"}", userA.csrfToken);
        assertThat(createA1.statusCode()).isEqualTo(201);
        Long idA1 = ((Number) JsonPath.read(createA1.body(), "$.id")).longValue();

        HttpResponse<String> createA2 = postLink(userA, "{\"destination_url\":\"https://example.com/a2\",\"custom_alias\":\"user-a-2\"}", userA.csrfToken);
        assertThat(createA2.statusCode()).isEqualTo(201);

        // User B creates 1 link
        HttpResponse<String> createB1 = postLink(userB, "{\"destination_url\":\"https://example.com/b1\",\"custom_alias\":\"user-b-1\"}", userB.csrfToken);
        assertThat(createB1.statusCode()).isEqualTo(201);
        Long idB1 = ((Number) JsonPath.read(createB1.body(), "$.id")).longValue();

        // User A list: only 2 links
        HttpResponse<String> listA = getLinks(userA, null);
        assertThat(listA.statusCode()).isEqualTo(200);
        int totalA = JsonPath.read(listA.body(), "$.total_elements");
        List<String> codesA = JsonPath.read(listA.body(), "$.content[*].code");
        assertThat(totalA).isEqualTo(2);
        assertThat(codesA).containsExactlyInAnyOrder("user-a-1", "user-a-2");

        // User B list: only 1 link
        HttpResponse<String> listB = getLinks(userB, null);
        assertThat(listB.statusCode()).isEqualTo(200);
        int totalB = JsonPath.read(listB.body(), "$.total_elements");
        List<String> codesB = JsonPath.read(listB.body(), "$.content[*].code");
        assertThat(totalB).isEqualTo(1);
        assertThat(codesB).containsExactly("user-b-1");

        // User B attempts to access, patch, or delete User A's link -> 404
        HttpResponse<String> getForeignRes = getLink(userB, idA1);
        assertThat(getForeignRes.statusCode()).isEqualTo(404);

        HttpResponse<String> patchForeignRes = patchLink(userB, idA1, "{\"title\":\"Hacked\"}", userB.csrfToken);
        assertThat(patchForeignRes.statusCode()).isEqualTo(404);

        HttpResponse<String> deleteForeignRes = deleteLink(userB, idA1, userB.csrfToken);
        assertThat(deleteForeignRes.statusCode()).isEqualTo(404);

        // User A attempts to access User B's link -> 404
        HttpResponse<String> getForeignResFromA = getLink(userA, idB1);
        assertThat(getForeignResFromA.statusCode()).isEqualTo(404);
    }

    @Test
    void shouldSearchLinksByDestinationUrlOrShortCode() throws Exception {
        UserSession user = createUserSession("search-user@linkscale.local", "password123");

        postLink(user, "{\"destination_url\":\"https://spring.io/projects/spring-boot\",\"custom_alias\":\"sb-docs\"}", user.csrfToken);
        postLink(user, "{\"destination_url\":\"https://github.com/spring-projects\",\"custom_alias\":\"gh-spring\"}", user.csrfToken);
        postLink(user, "{\"destination_url\":\"https://hub.docker.com\",\"custom_alias\":\"docker-hub\"}", user.csrfToken);

        // Search destination with "search"
        HttpResponse<String> searchDest = getLinks(user, "search=boot");
        assertThat(searchDest.statusCode()).isEqualTo(200);
        List<String> destCodes = JsonPath.read(searchDest.body(), "$.content[*].code");
        assertThat(destCodes).containsExactly("sb-docs");

        // Search code with "q"
        HttpResponse<String> searchCode = getLinks(user, "q=docker");
        assertThat(searchCode.statusCode()).isEqualTo(200);
        List<String> qCodes = JsonPath.read(searchCode.body(), "$.content[*].code");
        assertThat(qCodes).containsExactly("docker-hub");

        // Search substring matching multiple
        HttpResponse<String> searchMulti = getLinks(user, "search=spring");
        assertThat(searchMulti.statusCode()).isEqualTo(200);
        int totalMulti = JsonPath.read(searchMulti.body(), "$.total_elements");
        assertThat(totalMulti).isEqualTo(2);

        // Search nonexistent
        HttpResponse<String> searchNone = getLinks(user, "search=nomatchhere");
        assertThat(searchNone.statusCode()).isEqualTo(200);
        int totalNone = JsonPath.read(searchNone.body(), "$.total_elements");
        assertThat(totalNone).isEqualTo(0);
    }

    @Test
    void shouldFilterLinksByStatus() throws Exception {
        UserSession user = createUserSession("status-user@linkscale.local", "password123");

        // 1. Active link (active = true, no expiry)
        postLink(user, "{\"destination_url\":\"https://example.com/active\",\"custom_alias\":\"status-active\"}", user.csrfToken);

        // 2. Inactive link (create, then patch active=false)
        HttpResponse<String> inactRes = postLink(user, "{\"destination_url\":\"https://example.com/inactive\",\"custom_alias\":\"status-inactive\"}", user.csrfToken);
        Long inactId = ((Number) JsonPath.read(inactRes.body(), "$.id")).longValue();
        patchLink(user, inactId, "{\"active\":false}", user.csrfToken);

        // 3. Expired link (active = true, expires_at in past)
        HttpResponse<String> expRes = postLink(user, "{\"destination_url\":\"https://example.com/expired\",\"custom_alias\":\"status-expired\",\"expires_at\":\"2020-01-01T00:00:00Z\"}", user.csrfToken);
        Long expId = ((Number) JsonPath.read(expRes.body(), "$.id")).longValue();

        // 4. Soft-deleted link (create, then delete)
        HttpResponse<String> delRes = postLink(user, "{\"destination_url\":\"https://example.com/deleted\",\"custom_alias\":\"status-deleted\"}", user.csrfToken);
        Long delId = ((Number) JsonPath.read(delRes.body(), "$.id")).longValue();
        deleteLink(user, delId, user.csrfToken);

        // Filter: all (default) -> should return 3 links (active, inactive, expired; deleted excluded)
        HttpResponse<String> resAll = getLinks(user, "status=all");
        assertThat(resAll.statusCode()).isEqualTo(200);
        List<String> allCodes = JsonPath.read(resAll.body(), "$.content[*].code");
        assertThat(allCodes).containsExactlyInAnyOrder("status-active", "status-inactive", "status-expired");

        // Filter: active -> should return status-active
        HttpResponse<String> resActive = getLinks(user, "status=active");
        assertThat(resActive.statusCode()).isEqualTo(200);
        List<String> activeCodes = JsonPath.read(resActive.body(), "$.content[*].code");
        assertThat(activeCodes).containsExactly("status-active");

        // Filter: inactive -> should return status-inactive
        HttpResponse<String> resInactive = getLinks(user, "status=inactive");
        assertThat(resInactive.statusCode()).isEqualTo(200);
        List<String> inactiveCodes = JsonPath.read(resInactive.body(), "$.content[*].code");
        assertThat(inactiveCodes).containsExactly("status-inactive");

        // Filter: expired -> should return status-expired
        HttpResponse<String> resExpired = getLinks(user, "status=expired");
        assertThat(resExpired.statusCode()).isEqualTo(200);
        List<String> expiredCodes = JsonPath.read(resExpired.body(), "$.content[*].code");
        assertThat(expiredCodes).containsExactly("status-expired");

        // Filter: invalid status -> 400 Bad Request
        HttpResponse<String> resInvalid = getLinks(user, "status=unknown");
        assertThat(resInvalid.statusCode()).isEqualTo(400);
    }

    @Test
    void shouldSupportBoundedPaginationWithStableSort() throws Exception {
        UserSession user = createUserSession("page-user@linkscale.local", "password123");

        for (int i = 1; i <= 5; i++) {
            postLink(user, "{\"destination_url\":\"https://example.com/" + i + "\",\"custom_alias\":\"alias-0" + i + "\",\"title\":\"Title " + i + "\"}", user.csrfToken);
        }

        // Page 0, Size 2 -> Expect newest first: alias-05, alias-04
        HttpResponse<String> p0 = getLinks(user, "page=0&size=2");
        assertThat(p0.statusCode()).isEqualTo(200);
        assertThat((int) JsonPath.read(p0.body(), "$.page")).isEqualTo(0);
        assertThat((int) JsonPath.read(p0.body(), "$.size")).isEqualTo(2);
        assertThat((int) JsonPath.read(p0.body(), "$.total_elements")).isEqualTo(5);
        assertThat((int) JsonPath.read(p0.body(), "$.total_pages")).isEqualTo(3);
        List<String> p0Codes = JsonPath.read(p0.body(), "$.content[*].code");
        assertThat(p0Codes).containsExactly("alias-05", "alias-04");

        // Page 1, Size 2 -> Expect alias-03, alias-02
        HttpResponse<String> p1 = getLinks(user, "page=1&size=2");
        assertThat(p1.statusCode()).isEqualTo(200);
        List<String> p1Codes = JsonPath.read(p1.body(), "$.content[*].code");
        assertThat(p1Codes).containsExactly("alias-03", "alias-02");

        // Page 2, Size 2 -> Expect alias-01
        HttpResponse<String> p2 = getLinks(user, "page=2&size=2");
        assertThat(p2.statusCode()).isEqualTo(200);
        List<String> p2Codes = JsonPath.read(p2.body(), "$.content[*].code");
        assertThat(p2Codes).containsExactly("alias-01");

        // Bounds validation: page < 0 -> 400
        HttpResponse<String> badPage = getLinks(user, "page=-1&size=10");
        assertThat(badPage.statusCode()).isEqualTo(400);

        // Bounds validation: size < 1 -> 400
        HttpResponse<String> badSizeLow = getLinks(user, "page=0&size=0");
        assertThat(badSizeLow.statusCode()).isEqualTo(400);

        // Bounds validation: size > 100 -> 400
        HttpResponse<String> badSizeHigh = getLinks(user, "page=0&size=101");
        assertThat(badSizeHigh.statusCode()).isEqualTo(400);
    }

    @Test
    void shouldPartiallyUpdateTitleExpiryAndActiveStatus() throws Exception {
        UserSession user = createUserSession("patch-user@linkscale.local", "password123");

        String initial = """
                {
                    "destination_url": "https://example.com/patch-test",
                    "custom_alias": "patch-test",
                    "title": "Initial Title",
                    "expires_at": "2030-01-01T00:00:00Z"
                }
                """;
        HttpResponse<String> createRes = postLink(user, initial, user.csrfToken);
        assertThat(createRes.statusCode()).isEqualTo(201);
        Long linkId = ((Number) JsonPath.read(createRes.body(), "$.id")).longValue();
        String initialCreatedAt = JsonPath.read(createRes.body(), "$.created_at");

        // 1. Update title only (omitting expires_at and active)
        HttpResponse<String> patchTitleRes = patchLink(user, linkId, "{\"title\":\"Updated Title\"}", user.csrfToken);
        assertThat(patchTitleRes.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(patchTitleRes.body(), "$.title")).isEqualTo("Updated Title");
        assertThat((String) JsonPath.read(patchTitleRes.body(), "$.expires_at")).isNotNull();
        assertThat((Boolean) JsonPath.read(patchTitleRes.body(), "$.active")).isTrue();
        assertThat((String) JsonPath.read(patchTitleRes.body(), "$.destination_url")).isEqualTo("https://example.com/patch-test");
        assertThat((String) JsonPath.read(patchTitleRes.body(), "$.code")).isEqualTo("patch-test");

        // 2. Update active only (omitting title and expires_at)
        HttpResponse<String> patchActiveRes = patchLink(user, linkId, "{\"active\":false}", user.csrfToken);
        assertThat(patchActiveRes.statusCode()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(patchActiveRes.body(), "$.active")).isFalse();
        assertThat((String) JsonPath.read(patchActiveRes.body(), "$.title")).isEqualTo("Updated Title");
        assertThat((String) JsonPath.read(patchActiveRes.body(), "$.expires_at")).isNotNull();

        // 3. Clear title and expires_at with explicit nulls
        HttpResponse<String> patchClearRes = patchLink(user, linkId, "{\"title\":null,\"expires_at\":null}", user.csrfToken);
        assertThat(patchClearRes.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(patchClearRes.body(), "$.title")).isNull();
        assertThat((String) JsonPath.read(patchClearRes.body(), "$.expires_at")).isNull();
        assertThat((Boolean) JsonPath.read(patchClearRes.body(), "$.active")).isFalse();

        // 4. Verify in DB
        var row = jdbcClient.sql("SELECT destination_url, code, title, expires_at, active, created_at, updated_at FROM links WHERE id = ?")
                .param(linkId)
                .query()
                .singleRow();
        assertThat(row.get("destination_url")).isEqualTo("https://example.com/patch-test");
        assertThat(row.get("code")).isEqualTo("patch-test");
        assertThat(row.get("title")).isNull();
        assertThat(row.get("expires_at")).isNull();
        assertThat(row.get("active")).isEqualTo(false);
    }

    @Test
    void shouldSoftDeleteLinkPreserveCodeReservationAndStopRedirects() throws Exception {
        UserSession user = createUserSession("delete-user@linkscale.local", "password123");

        HttpResponse<String> createRes = postLink(user, "{\"destination_url\":\"https://example.com/delete-test\",\"custom_alias\":\"del-alias\"}", user.csrfToken);
        assertThat(createRes.statusCode()).isEqualTo(201);
        Long linkId = ((Number) JsonPath.read(createRes.body(), "$.id")).longValue();

        // Confirm public redirect works initially
        HttpRequest initialRedirectReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/r/del-alias"))
                .GET()
                .build();
        HttpResponse<Void> initialRedirectRes = anonymousClient.send(initialRedirectReq, HttpResponse.BodyHandlers.discarding());
        assertThat(initialRedirectRes.statusCode()).isEqualTo(302);
        assertThat(initialRedirectRes.headers().firstValue("Location")).contains("https://example.com/delete-test");
        assertThat(initialRedirectRes.headers().firstValue("Cache-Control")).contains("no-store");

        // Soft-delete the link
        HttpResponse<String> deleteRes = deleteLink(user, linkId, user.csrfToken);
        assertThat(deleteRes.statusCode()).isEqualTo(204);

        // Verify DB row has deleted_at set
        var deletedAt = jdbcClient.sql("SELECT deleted_at FROM links WHERE id = ?")
                .param(linkId)
                .query(Object.class)
                .single();
        assertThat(deletedAt).isNotNull();

        // Detail endpoint returns 404
        HttpResponse<String> getRes = getLink(user, linkId);
        assertThat(getRes.statusCode()).isEqualTo(404);

        // List endpoint does not include it
        HttpResponse<String> listRes = getLinks(user, null);
        assertThat(listRes.statusCode()).isEqualTo(200);
        List<String> listCodes = JsonPath.read(listRes.body(), "$.content[*].code");
        assertThat(listCodes).doesNotContain("del-alias");

        // Public redirect now returns 404 with Cache-Control: no-store
        HttpResponse<Void> deletedRedirectRes = anonymousClient.send(initialRedirectReq, HttpResponse.BodyHandlers.discarding());
        assertThat(deletedRedirectRes.statusCode()).isEqualTo(404);
        assertThat(deletedRedirectRes.headers().firstValue("Cache-Control")).contains("no-store");

        // Attempting to reuse the alias returns 409 Conflict (code uniqueness preserved across soft delete)
        HttpResponse<String> reuseRes = postLink(user, "{\"destination_url\":\"https://example.com/new-dest\",\"custom_alias\":\"del-alias\"}", user.csrfToken);
        assertThat(reuseRes.statusCode()).isEqualTo(409);
    }
}

