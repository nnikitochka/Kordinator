package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class IndexPageIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    @Autowired private ApplicationContext context;
    private final HttpClient client = HttpClient.newHttpClient();
    private final HttpClient browser = HttpClient.newBuilder()
            .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();

    @Test public void servesPerUserShellSettingsAndDefersFrameNavigationUntilLoaded() throws Exception {
        security.createUser(new User("index-page-member", null), "secret", "test");
        UserSettings adminOriginal = settings.getUserSettings("admin");
        UserSettings memberOriginal = settings.getUserSettings("index-page-member");
        try {
            UserSettings admin = settings.getUserSettings("admin");
            admin.setShowSideBar(false);
            admin.setShowNowPlayingEnabled(false);
            admin.setKeyboardShortcutsEnabled(true);
            settings.updateUserSettings(admin);
            UserSettings member = settings.getUserSettings("index-page-member");
            member.setShowSideBar(true);
            member.setShowNowPlayingEnabled(true);
            member.setKeyboardShortcutsEnabled(false);
            settings.updateUserSettings(member);

            JsonNode adminData = fetch("admin", "admin");
            assertFalse(adminData.path("showSideBar").asBoolean());
            assertFalse(adminData.path("showRight").asBoolean());
            assertTrue(adminData.path("keyboardShortcutsEnabled").asBoolean());
            assertEquals(3, adminData.size());
            assertTrue(get("/rest/indexPageSettings?u=admin&p=admin&v=" + TestCaseUtils.restApiVersion() + "&c=index-test")
                    .headers().firstValue("Cache-Control").orElse("").contains("no-store"));

            JsonNode memberData = fetch("index-page-member", "secret");
            assertTrue(memberData.path("showSideBar").asBoolean());
            assertTrue(memberData.path("showRight").asBoolean());
            assertFalse(memberData.path("keyboardShortcutsEnabled").asBoolean());

            login();
            for (String path : new String[]{"/index", "/index.view"}) {
                HttpResponse<String> page = browserGet(path);
                assertEquals(page.body(), 200, page.statusCode());
                assertTrue(page.body().contains("/airsonic/rest/indexPageSettings"));
                assertTrue(page.body().contains("/airsonic/script/index-page.js?v=1"));
                assertTrue(page.body().contains("id=\"playQueueMusic\""));
                assertFalse(page.body().contains("model.showSideBar"));
                assertFalse(page.body().contains("model.showRight"));
                for (String frame : new String[]{"upper", "left", "main", "right"}) {
                    Matcher iframe = Pattern.compile("<iframe[^>]*id=\"" + frame + "\"[^>]*>").matcher(page.body());
                    assertTrue(page.body(), iframe.find());
                    assertFalse(iframe.group(), iframe.group().contains("src="));
                }
            }
            HttpResponse<String> unsafeMain = browserGet("/index?main=javascript%3Aalert%281%29");
            assertEquals(200, unsafeMain.statusCode());
            assertFalse(unsafeMain.body().contains("<script>alert(1)</script>"));
            assertFalse(context.containsBean("indexController"));
        } finally {
            settings.updateUserSettings(adminOriginal);
            settings.updateUserSettings(memberOriginal);
        }
    }

    private JsonNode fetch(String username, String password) throws Exception {
        String query = "u=" + username + "&p=" + password + "&v=" + TestCaseUtils.restApiVersion() + "&c=index-page-test";
        HttpResponse<String> response = get("/rest/indexPageSettings?" + query);
        assertEquals(response.body(), 200, response.statusCode());
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        return json.readTree(response.body());
    }

    private void login() throws Exception {
        HttpResponse<String> page = browserGet("/login");
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
        assertTrue(token.find());
        String form = "j_username=admin&j_password=admin&_csrf=" + java.net.URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        assertEquals(302, browser.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private HttpResponse<String> browserGet(String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
