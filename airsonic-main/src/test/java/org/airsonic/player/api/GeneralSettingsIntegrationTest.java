package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.SettingsService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class GeneralSettingsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    @Autowired private ApplicationContext context;
    @MockitoSpyBean private org.airsonic.player.service.PlaylistService playlists;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void administerGeneralSettingsViaKtor() throws Exception {
        doNothing().when(playlists).addPlaylistFolderWatcher();
        doNothing().when(playlists).importPlaylists();
        clearInvocations(playlists);
        User member = new User("general-member", null);
        member.setRoles(Set.of(User.Role.SETTINGS, User.Role.STREAM));
        security.createUser(member, "secret", "test");
        assertFalse(context.containsBean("generalSettingsController"));
        JsonNode initial = read("admin", "admin");
        assertTrue(initial.path("themes").path("theme").isArray());
        assertTrue(initial.path("locales").path("locale").isArray());
        error(api("getGeneralSettings", "general-member", "secret", null), 50);
        assertEquals(405, raw("saveGeneralSettings", null).statusCode());
        HttpClient browser = login("admin", "admin");
        for (String page : new String[]{"generalSettings", "generalSettings.view"}) {
            HttpResponse<String> response = get(browser, "/" + page);
            assertEquals(response.body(), 200, response.statusCode());
            assertTrue(response.body().contains("initGeneralSettings"));
            assertFalse(response.body().contains("${_csrf.token}"));
            assertFalse(response.body().contains("<form:"));
        }
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body());
        assertTrue(token.find());
        String csrf = "&_csrf=" + encode(token.group(1));
        StringBuilder payload = new StringBuilder();
        initial.fields().forEachRemaining(entry -> {
            if (entry.getValue().isValueNode()) payload.append('&').append(entry.getKey()).append('=').append(encode(entry.getValue().asText()));
        });
        String baseSettings = payload.toString();
        assertEquals(403, post(browser, "/rest/saveGeneralSettings", "f=json" + baseSettings).statusCode());
        assertEquals(405, post(browser, "/generalSettings.view", "_csrf=" + encode(token.group(1))).statusCode());
        HttpClient memberBrowser = login("general-member", "secret");
        assertEquals(403, get(memberBrowser, "/generalSettings.view").statusCode());
        Matcher memberToken = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(memberBrowser, "/top.view").body());
        assertTrue(memberToken.find());
        error(website(memberBrowser, "saveGeneralSettings", baseSettings + "&_csrf=" + encode(memberToken.group(1))), 50);
        String originalTitle = settings.getWelcomeTitle();
        for (String invalid : new String[]{"coverArtQuality=-1", "coverArtQuality=101", "coverArtQuality=bad",
                "coverArtConcurrency=0", "coverArtConcurrency=bad", "coverArtSource=BAD", "themeId=unknown", "locale=unknown",
                "sessionDuration=bad", "sessionDuration=0m", "sortAlbumsByYear=yes"}) {
            error(website(browser, "saveGeneralSettings", invalid + "&welcomeTitle=SHOULD_NOT_SAVE" + baseSettings + csrf), 0);
            assertEquals(originalTitle, settings.getWelcomeTitle());
        }
        error(website(browser, "saveGeneralSettings", "welcomeTitle=incomplete" + csrf), 0);
        verify(playlists, never()).importPlaylists();
        String themeId = initial.path("themes").path("theme").get(1).path("id").asText();
        String locale = initial.path("locales").path("locale").get(1).path("id").asText();
        String changedSource = initial.path("coverArtSource").asText().equals("TAG") ? "FILE" : "TAG";
        settings.setFullScan(false); settings.setClearFullScanSettingAfterScan(false);
        String update = "themeId=" + encode(themeId) + "&locale=" + encode(locale) + "&welcomeTitle=" + encode("<REST & Ktor>") + "&welcomeMessage=" + encode("line 1\nline 2")
                + "&sortAlbumsByYear=true&gettingStartedEnabled=false&coverArtQuality=85&coverArtConcurrency=2"
                + "&sessionDuration=45m&coverArtSource=" + changedSource + baseSettings;
        ok(website(browser, "saveGeneralSettings", update + csrf));
        assertEquals("<REST & Ktor>", settings.getWelcomeTitle());
        assertEquals("line 1\nline 2", settings.getWelcomeMessage());
        assertEquals(85, settings.getCoverArtQuality());
        assertEquals(2, settings.getCoverArtConcurrency());
        assertEquals("45m", settings.getSessionDuration());
        assertTrue(settings.isSortAlbumsByYear());
        assertFalse(settings.isGettingStartedEnabled());
        assertTrue(settings.getFullScan());
        assertTrue(settings.getClearFullScanSettingAfterScan());
        verify(playlists, times(1)).addPlaylistFolderWatcher();
        verify(playlists, times(1)).importPlaylists();
        JsonNode saved = read("admin", "admin");
        assertEquals("<REST & Ktor>", saved.path("welcomeTitle").asText());
        assertEquals(themeId, settings.getThemeId());
        assertEquals(themeId, saved.path("themeId").asText());
        assertEquals(locale, settings.getLocale().toLanguageTag());
        assertEquals(locale, saved.path("locale").asText());
        String xml = get(client, "/rest/getGeneralSettings?u=admin&p=admin&v=" + TestCaseUtils.restApiVersion() + "&c=test").body();
        assertTrue(xml, xml.contains("<generalSettings"));
        org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        assertEquals("<REST & Ktor>", ((org.w3c.dom.Element) document.getElementsByTagName("generalSettings").item(0)).getAttribute("welcomeTitle"));
        // Re-saving the same source must not override an existing full-scan policy.
        settings.setFullScan(true); settings.setClearFullScanSettingAfterScan(false);
        ok(website(browser, "saveGeneralSettings", update + csrf));
        assertFalse(settings.getClearFullScanSettingAfterScan());
        ok(website(browser, "saveGeneralSettings", "welcomeTitle=" + baseSettings + csrf));
        assertTrue(read("admin", "admin").path("welcomeTitle").asText().isEmpty());
        assertFalse(settings.getClearFullScanSettingAfterScan());
    }

    private JsonNode read(String user, String password) throws Exception {
        JsonNode body = api("getGeneralSettings", user, password, null);
        ok(body); return body.path("generalSettings");
    }
    private HttpResponse<String> raw(String operation, String body) throws Exception {
        String path = "/rest/" + operation + "?u=admin&p=admin&v=" + TestCaseUtils.restApiVersion() + "&c=test&f=json";
        return body == null ? get(client, path) : post(client, path, body);
    }
    private JsonNode api(String operation, String user, String password, String body) throws Exception {
        String path = "/rest/" + operation + "?u=" + user + "&p=" + password + "&v=" + TestCaseUtils.restApiVersion() + "&c=test&f=json";
        HttpResponse<String> response = body == null ? get(client, path) : post(client, path, body);
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
    private JsonNode website(HttpClient browser, String operation, String body) throws Exception {
        HttpResponse<String> response = post(browser, "/rest/" + operation, "f=json&c=airsonic-web&" + body);
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
    private void ok(JsonNode body) { assertEquals(body.toString(), "ok", body.path("status").asText()); }
    private void error(JsonNode body, int code) {
        assertEquals(body.toString(), "failed", body.path("status").asText());
        assertEquals(body.toString(), code, body.path("error").path("code").asInt());
    }
}
