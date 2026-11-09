package org.airsonic.player.api;

import org.airsonic.player.domain.User;
import org.airsonic.player.service.PathWatcherService;
import org.airsonic.player.TestCaseUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class PathWatchersIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private ApplicationContext context;
    @Autowired private PathWatcherService watchers;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void diagnosticsDoNotInterfereWithDirectoryWatching() throws Exception {
        User member = new User("watcher-member", null);
        member.setRoles(Set.of(User.Role.STREAM));
        security.createUser(member, "secret", "test");
        assertFalse(java.util.Arrays.stream(context.getBeanDefinitionNames()).anyMatch(name -> name.contains("PathWatcherEndpoint")));
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("watcher & test");
        String name = "<watcher & test>";
        var events = new java.util.concurrent.LinkedBlockingQueue<String>();
        watchers.setWatcher(name, directory, (path, event) -> events.add(event.context().toString()), null, null, null);
        try {
            assertEquals("failed", body(get(client, "/rest/getPathWatchers?f=json")).path("status").asText());
            for (HttpClient browser : new HttpClient[]{login("admin", "admin"), login(member.getUsername(), "secret")}) {
                HttpResponse<String> response = get(browser, "/rest/getPathWatchers?f=json");
                assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
                JsonNode result = body(response);
                assertEquals("ok", result.path("status").asText());
                JsonNode rows = result.path("pathWatchers").path("watcher");
                boolean found = false;
                String previous = null;
                for (JsonNode row : rows) {
                    String id = row.path("name").asText();
                    if (previous != null) assertTrue(previous.compareTo(id) <= 0);
                    previous = id;
                    if (name.equals(id)) { found = true; assertEquals(directory.toString(), row.path("path").asText()); }
                    assertEquals(2, row.size());
                }
                assertTrue(found);
                String page = get(browser, "/status.view").body();
                assertTrue(page.contains("statisticsApi.pathWatchers()"));
                assertFalse(page.contains("/actuator/pathwatcher"));
                assertEquals(404, get(browser, "/actuator/pathwatcher").statusCode());
                Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body());
                assertTrue(token.find());
                assertEquals(403, post(browser, "/rest/getPathWatchers", "").statusCode());
                for (String method : new String[]{"POST", "PUT", "DELETE", "PATCH"}) {
                    assertEquals(405, browser.send(HttpRequest.newBuilder(URI.create(base() + "/rest/getPathWatchers"))
                        .header("X-CSRF-TOKEN", token.group(1)).method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
                }
            }
            String auth = "?u=admin&p=admin&c=test&v=" + TestCaseUtils.restApiVersion();
            var xml = get(client, "/rest/getPathWatchers" + auth);
            var factory = javax.xml.parsers.DocumentBuilderFactory.newDefaultNSInstance();
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var document = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml.body())));
            var nodes = document.getElementsByTagNameNS("http://subsonic.org/restapi", "watcher");
            boolean found = false;
            for (int i = 0; i < nodes.getLength(); i++) {
                var row = (org.w3c.dom.Element) nodes.item(i);
                if (name.equals(row.getAttribute("name"))) { found = true; assertEquals(directory.toString(), row.getAttribute("path")); }
            }
            assertTrue(found);
            java.nio.file.Files.createFile(directory.resolve("after-diagnostics.txt"));
            assertEquals("after-diagnostics.txt", events.poll(10, java.util.concurrent.TimeUnit.SECONDS));
            var snapshot = watchers.getWatchers();
            watchers.invalidateWatcher(name);
            assertEquals(directory.toString(), snapshot.get(name));
            JsonNode remaining = body(get(client, "/rest/getPathWatchers" + auth + "&f=json")).path("pathWatchers").path("watcher");
            for (JsonNode row : remaining) assertNotEquals(name, row.path("name").asText());
        } finally {
            watchers.invalidateWatcher(name);
            java.nio.file.Files.deleteIfExists(directory.resolve("after-diagnostics.txt"));
            java.nio.file.Files.deleteIfExists(directory);
        }
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
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
