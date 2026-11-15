package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.domain.Version;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.VersionService;
import org.airsonic.player.util.HomeRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RightPageIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SettingsService settings;
    @Autowired private SecurityService security;
    @Autowired private ApplicationContext context;
    @MockitoBean private VersionService versions;
    private final HttpClient client = HttpClient.newHttpClient();
    private final HttpClient browser = HttpClient.newBuilder()
            .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();

    @Test public void servesOnlyTheCurrentUsersRightPanelSettingsAndPrefersFinalRelease() throws Exception {
        UserSettings original = settings.getUserSettings("admin");
        try {
            UserSettings user = settings.getUserSettings("admin");
            user.setFinalVersionNotificationEnabled(true);
            user.setBetaVersionNotificationEnabled(true);
            user.setShowNowPlayingEnabled(false);
            settings.updateUserSettings(user);
            when(versions.isNewFinalVersionAvailable()).thenReturn(true);
            when(versions.isNewBetaVersionAvailable()).thenReturn(true);
            when(versions.getLatestFinalVersion()).thenReturn(version("12.0", "https://github.com/airsonic-advanced/airsonic-advanced/releases/tag/12.0"));
            when(versions.getLatestBetaVersion()).thenReturn(version("13.0-beta", "https://github.com/airsonic-advanced/airsonic-advanced/releases/tag/13.0-beta"));

            JsonNode data = fetch("admin", "admin");
            assertEquals(settings.getBrand(), data.path("brand").asText());
            assertFalse(data.path("showNowPlaying").asBoolean());
            assertTrue(data.path("newVersionAvailable").asBoolean());
            assertEquals("12.0", data.path("latestVersion").asText());
            assertEquals("https://github.com/airsonic-advanced/airsonic-advanced/releases/tag/12.0", data.path("latestVersionUrl").asText());
            assertFalse(data.has("user"));
            assertFalse(data.has("settings"));
            assertFalse(data.has("artifacts"));

            login();
            for (String path : List.of("/right", "/right.view")) {
                HttpResponse<String> shell = browserGet(path);
                assertEquals(shell.body(), 200, shell.statusCode());
                assertTrue(shell.body().contains("/airsonic/rest/rightPageSettings"));
                assertFalse(shell.body().contains("${model."));
                assertTrue(shell.body().contains("if (!settings.showNowPlaying) return"));
            }
            assertFalse(context.containsBean("rightController"));
        } finally {
            settings.updateUserSettings(original);
        }
    }

    @Test public void respectsBetaPreferenceAndDoesNotReturnUntrustedReleaseLinks() throws Exception {
        security.createUser(new User("right-page-member", null), "secret", "test");
        UserSettings original = settings.getUserSettings("right-page-member");
        try {
            UserSettings user = settings.getUserSettings("right-page-member");
            user.setFinalVersionNotificationEnabled(false);
            user.setBetaVersionNotificationEnabled(true);
            user.setShowNowPlayingEnabled(true);
            settings.updateUserSettings(user);
            when(versions.isNewFinalVersionAvailable()).thenReturn(true);
            when(versions.isNewBetaVersionAvailable()).thenReturn(true);
            when(versions.getLatestBetaVersion()).thenReturn(version("13.0-beta", "https://evil.example/release"));

            JsonNode data = fetch("right-page-member", "secret");
            assertTrue(data.path("showNowPlaying").asBoolean());
            assertTrue(data.path("newVersionAvailable").asBoolean());
            assertEquals("13.0-beta", data.path("latestVersion").asText());
            assertTrue(data.path("latestVersionUrl").isNull());

            user.setBetaVersionNotificationEnabled(false);
            settings.updateUserSettings(user);
            when(versions.isNewBetaVersionAvailable()).thenReturn(false);
            JsonNode noUpdate = fetch("right-page-member", "secret");
            assertFalse(noUpdate.path("newVersionAvailable").asBoolean());
            assertTrue(noUpdate.path("latestVersion").isNull());
            assertTrue(noUpdate.path("latestVersionUrl").isNull());
        } finally {
            settings.updateUserSettings(original);
        }
    }

    private Version version(String number, String url) {
        return new Version(number, "commit", false, url, null, null, List.of(Map.of("name", "private-artifact")));
    }

    private JsonNode fetch(String username, String password) throws Exception {
        String query = "u=" + username + "&p=" + password + "&v=" + TestCaseUtils.restApiVersion() + "&c=right-page-test&f=json";
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/rightPageSettings?" + query)).GET().build(), HttpResponse.BodyHandlers.ofString());
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
}
