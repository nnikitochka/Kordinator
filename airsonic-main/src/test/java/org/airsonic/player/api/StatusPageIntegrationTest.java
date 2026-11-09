package org.airsonic.player.api;

import org.airsonic.player.domain.User;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.HomeRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class StatusPageIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private ApplicationContext context;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void statusPageWorksWithoutSpringControllerForBothAliases() throws Exception {
        User member = new User("status-page-member", null);
        member.setRoles(Set.of(User.Role.STREAM));
        security.createUser(member, "secret", "test");
        assertFalse(context.containsBean("statusController"));
        HttpClient admin = login("admin", "admin");
        HttpClient browser = login(member.getUsername(), "secret");
        for (String path : new String[]{"/status", "/status.view"}) {
            assertEquals(401, get(client, path).statusCode());
            HttpResponse<String> anonymous = client.send(HttpRequest.newBuilder(URI.create(base() + path))
                    .header("Accept", "text/html").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(302, anonymous.statusCode());
            assertTrue(anonymous.headers().firstValue("Location").orElse("").endsWith("/airsonic/login"));
            for (HttpClient authorized : new HttpClient[]{admin, browser}) {
                HttpResponse<String> page = get(authorized, path);
                assertEquals(page.body(), 200, page.statusCode());
                assertTrue(page.headers().firstValue("Content-Type").orElse("").contains("text/html"));
                assertTrue(page.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
                for (String id : new String[]{"transfersTable", "userChart", "cachesTable", "scheduledTasksTable",
                        "pathWatcherTable", "sessionsTable", "healthTable"}) {
                    assertTrue(id, page.body().contains("id=\"" + id + "\""));
                }
                assertTrue(page.body().contains("/airsonic/script/chart-3.5.0.min.js"));
                assertTrue(page.body().contains("/airsonic/script/status-statistics.js"));
                assertTrue(page.body().contains("/airsonic/rest/"));
                assertFalse(page.body().contains("/statistics/"));
                assertTrue(page.body().contains("/airsonic/actuator/health"));
                assertFalse(page.body().contains("<spring:theme"));
                HttpResponse<String> head = authorized.send(HttpRequest.newBuilder(URI.create(base() + path))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, head.statusCode());
                assertEquals("", head.body());
                Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(authorized, "/top.view").body());
                assertTrue(token.find());
                assertEquals(403, post(authorized, path, "").statusCode());
                for (String method : new String[]{"POST", "PUT", "DELETE", "PATCH"}) {
                    HttpResponse<String> response = authorized.send(HttpRequest.newBuilder(URI.create(base() + path))
                            .header("X-CSRF-TOKEN", token.group(1))
                            .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                    assertEquals(method + " " + response.body(), 405, response.statusCode());
                    assertEquals("GET, HEAD", response.headers().firstValue("Allow").orElse(""));
                }
            }
        }
    }

    private HttpClient login(String username, String password) throws Exception {
        HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(get(browser, "/login").body());
        assertTrue(token.find());
        assertEquals(302, post(browser, "/login", "j_username=" + username + "&j_password=" + password + "&_csrf=" + encode(token.group(1))).statusCode());
        return browser;
    }
    private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient browser, String path, String form) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(base() + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String base() { return "http://localhost:" + port + "/airsonic"; }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
