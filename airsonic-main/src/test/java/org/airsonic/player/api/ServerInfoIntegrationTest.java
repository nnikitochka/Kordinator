package org.airsonic.player.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.Version;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.VersionService;
import org.airsonic.player.util.HomeRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ServerInfoIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private ApplicationContext context;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    @MockitoBean private VersionService versions;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void helpUsesKtorAndOnlyAdministratorsCanReadConfiguredServerLog() throws Exception {
        assertFalse(context.containsBean("helpController"));
        when(versions.getLocalVersion()).thenReturn(new Version("11.0"));
        when(versions.getLocalBuildDate()).thenReturn(Instant.parse("2026-11-09T00:00:00Z"));
        when(versions.getLocalBuildNumber()).thenReturn("abcdef1234567");
        when(versions.isNewFinalVersionAvailable()).thenReturn(true);
        when(versions.getLatestFinalVersion()).thenReturn(new Version("12.0", "commit", false,
                "https://example.test/releases/12.0", null, null, List.of(Map.of("name", "private-artifact"))));
        security.createUser(new User("info-member", null), "secret", "test");
        var anonymous = get(client, "/rest/serverInfo?f=json");
        assertEquals("failed", json.readTree(anonymous.body()).path("subsonic-response").path("status").asText());
        assertFalse(anonymous.body().contains("logEntries"));
        var admin = login("admin", "admin");
        var member = login("info-member", "secret");
        for (HttpClient browser : List.of(admin, member)) {
            var response = get(browser, "/rest/serverInfo");
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            var info = json.readTree(response.body());
            assertEquals("11.0", info.path("localVersion").asText());
            assertEquals("12.0", info.path("latestVersion").asText());
            assertEquals("2026-11-09T00:00:00Z", info.path("buildDate").asText());
            assertEquals("abcdef1234567", info.path("buildNumber").asText());
            assertTrue(info.path("serverInfo").asText().contains(", java "));
            assertTrue(info.path("usedMemory").asLong() > 0);
            assertTrue(info.path("totalMemory").asLong() >= info.path("usedMemory").asLong());
            assertEquals(browser == admin, info.path("canReadLog").asBoolean());
            assertFalse(info.has("logFile")); assertFalse(info.has("logEntries")); assertFalse(info.has("user"));
            assertFalse(response.body().contains("private-artifact"));
            for (String page : List.of("/help", "/help.view")) {
                var shell = get(browser, page);
                assertEquals(200, shell.statusCode());
                assertTrue(shell.body().contains("/airsonic/rest/serverInfo"));
                assertTrue(shell.body().contains("/airsonic/rest/serverLog"));
                assertTrue(shell.body().contains("server-info.js?v=1"));
                assertFalse(shell.body().contains(settings.getLogFile()));
            }
            var head = send(browser, "/rest/serverInfo", "HEAD", null);
            assertEquals(200, head.statusCode()); assertEquals("", head.body());
        }
        assertEquals(403, get(member, "/rest/serverLog").statusCode());
        assertEquals(403, send(member, "/rest/serverLog", "HEAD", null).statusCode());
        var logPath = Path.of(settings.getLogFile());
        String marker = "Секретная строка <img src=x onerror=alert(1)> & журнал";
        Files.writeString(logPath, "\n" + marker + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        var response = get(admin, "/rest/serverLog?logFile=/etc/passwd");
        assertEquals(200, response.statusCode());
        var log = json.readTree(response.body());
        assertTrue(log.path("available").asBoolean());
        assertEquals(settings.getLogFile(), log.path("logFile").asText());
        assertTrue(log.path("logEntries").size() <= 50);
        assertTrue(log.path("logEntries").toString().contains(marker));
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertEquals("", send(admin, "/rest/serverLog", "HEAD", null).body());
        assertEquals(403, send(admin, "/rest/serverLog", "POST", "").statusCode());
        String basic = Base64.getEncoder().encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8));
        var external = client.send(HttpRequest.newBuilder(uri("/rest/serverLog")).header("Authorization", "Basic " + basic).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, external.statusCode());
        assertTrue(json.readTree(external.body()).path("available").asBoolean());
        var queryAuth = get(client, "/rest/serverInfo?u=admin&p=admin&v=" + org.airsonic.player.TestCaseUtils.restApiVersion() + "&c=help-test&f=json");
        assertEquals(queryAuth.body(), 200, queryAuth.statusCode());
        assertEquals(queryAuth.body(), "11.0", json.readTree(queryAuth.body()).path("localVersion").asText());
        var denied = get(client, "/rest/serverLog?f=json");
        assertEquals("failed", json.readTree(denied.body()).path("subsonic-response").path("status").asText());
        assertFalse(denied.body().contains(marker));
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + "/airsonic" + path); }
    private HttpResponse<String> get(HttpClient browser, String path) throws Exception { return send(browser, path, "GET", null); }
    private HttpResponse<String> send(HttpClient browser, String path, String method, String form) throws Exception {
        var request = HttpRequest.newBuilder(uri(path));
        if (form != null) request.header("Content-Type", "application/x-www-form-urlencoded");
        return browser.send(request.method(method, form == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpClient login(String username, String password) throws Exception {
        var browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(get(browser, "/login").body());
        assertTrue(token.find());
        assertEquals(302, send(browser, "/login", "POST", "j_username=" + username + "&j_password=" + password + "&_csrf=" + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8)).statusCode());
        return browser;
    }
}
