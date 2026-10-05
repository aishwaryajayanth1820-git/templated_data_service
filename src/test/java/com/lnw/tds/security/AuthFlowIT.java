package com.lnw.tds.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.lnw.tds.IntegrationTestBase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The real browser flow: CSRF cookie → login → session cookie → forced password change → logout. */
class AuthFlowIT extends IntegrationTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String PASSWORD = "first-password-1";

    @LocalServerPort
    int port;

    @Autowired
    AccountService accounts;

    @Autowired
    JsonMapper json;

    private Browser browser;
    private String username;

    @BeforeEach
    void setUp() {
        browser = new Browser();
        username = "user" + SEQ.incrementAndGet();
        accounts.createUser(username, "Test User", PASSWORD, Set.of(), true);
    }

    @Test
    void anonymousApiCallsGet401ProblemDetail() throws Exception {
        Res r = browser.get("/api/auth/me");

        assertThat(r.status).isEqualTo(401);
        assertThat(r.contentType).startsWith("application/problem+json");
        assertThat(r.body.path("code").asString()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        Res r = browser.post("/api/auth/login", login(username, PASSWORD), false);

        assertThat(r.status).isEqualTo(403);
        assertThat(r.body.path("detail").asString()).contains("CSRF");
    }

    @Test
    void wrongPasswordGivesGeneric401() throws Exception {
        browser.get("/api/auth/csrf");

        Res r = browser.post("/api/auth/login", login(username, "nope-nope-nope"), true);

        assertThat(r.status).isEqualTo(401);
        assertThat(r.body.path("detail").asString()).isEqualTo("Invalid username or password");
    }

    @Test
    void fullFlowWithForcedPasswordChange() throws Exception {
        browser.get("/api/auth/csrf");
        String csrfBefore = browser.cookie("XSRF-TOKEN");

        Res login = browser.post("/api/auth/login", login(username, PASSWORD), true);
        assertThat(login.status).isEqualTo(200);
        assertThat(login.body.path("username").asString()).isEqualTo(username);
        assertThat(login.body.path("roles").toString()).contains("viewer");
        assertThat(login.body.path("mustChangePassword").asBoolean()).isTrue();
        assertThat(login.setCookies).as("login response sets a fresh CSRF cookie").anyMatch(c -> c.startsWith("XSRF-TOKEN=") && !c.startsWith("XSRF-TOKEN=;"));
        assertThat(browser.cookie("XSRF-TOKEN")).as("CSRF token rotates on login").isNotNull().isNotEqualTo(csrfBefore);

        assertThat(browser.get("/api/auth/me").status).isEqualTo(200);
        Res blocked = browser.get("/api/anything");
        assertThat(blocked.status).isEqualTo(403);
        assertThat(blocked.body.path("code").asString()).isEqualTo("PASSWORD_CHANGE_REQUIRED");

        Res weak = browser.post("/api/auth/password", change(PASSWORD, "short"), true);
        assertThat(weak.status).isEqualTo(422);
        assertThat(weak.body.path("errors").path("newPassword").asString()).contains("at least 10");

        Res wrongCurrent = browser.post("/api/auth/password", change("not-it-at-all", "second-password-2"), true);
        assertThat(wrongCurrent.status).isEqualTo(422);
        assertThat(wrongCurrent.body.path("errors").has("currentPassword")).isTrue();

        Res changed = browser.post("/api/auth/password", change(PASSWORD, "second-password-2"), true);
        assertThat(changed.status).isEqualTo(200);
        assertThat(changed.body.path("mustChangePassword").asBoolean()).isFalse();

        Res unknown = browser.get("/api/anything");
        assertThat(unknown.status).as("no longer blocked; endpoint simply does not exist").isEqualTo(404);
        assertThat(unknown.body.path("code").asString()).isEqualTo("NOT_FOUND");

        assertThat(browser.post("/api/auth/logout", "", true).status).isEqualTo(204);
        assertThat(browser.get("/api/auth/me").status).isEqualTo(401);

        browser.get("/api/auth/csrf");
        assertThat(browser.post("/api/auth/login", login(username, "second-password-2"), true).status).isEqualTo(200);
    }

    @Test
    void nonAdminCannotReachAdminApi() throws Exception {
        String user = "plain" + SEQ.incrementAndGet();
        accounts.createUser(user, null, PASSWORD, Set.of(), false);
        browser.get("/api/auth/csrf");
        browser.post("/api/auth/login", login(user, PASSWORD), true);

        Res r = browser.get("/api/admin/templates");

        assertThat(r.status).isEqualTo(403);
        assertThat(r.body.path("code").asString()).isEqualTo("FORBIDDEN");
    }

    private static String login(String u, String p) {
        return "{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}";
    }

    private static String change(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }

    record Res(int status, String contentType, JsonNode body, java.util.List<String> setCookies) {}

    /** Minimal cookie-keeping client that behaves like the SPA (echoes XSRF-TOKEN as X-XSRF-TOKEN). */
    final class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

        Res get(String path) throws IOException, InterruptedException {
            return send(HttpRequest.newBuilder(uri(path)).GET());
        }

        Res post(String path, String body, boolean withCsrf) throws IOException, InterruptedException {
            HttpRequest.Builder b = HttpRequest.newBuilder(uri(path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            String token = cookie("XSRF-TOKEN");
            if (withCsrf && token != null) {
                b.header("X-XSRF-TOKEN", token);
            }
            return send(b);
        }

        String cookie(String name) {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(c -> c.getName().equals(name)).map(HttpCookie::getValue).findFirst().orElse(null);
        }

        private Res send(HttpRequest.Builder b) throws IOException, InterruptedException {
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = r.body().isBlank() ? json.createObjectNode() : json.readTree(r.body());
            return new Res(r.statusCode(), r.headers().firstValue("Content-Type").orElse(""), body, r.headers().allValues("Set-Cookie"));
        }

        private URI uri(String path) {
            return URI.create("http://localhost:" + port + path);
        }
    }
}
