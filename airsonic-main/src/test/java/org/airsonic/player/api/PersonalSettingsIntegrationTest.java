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
public class PersonalSettingsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void manageOnlyAuthenticatedUsersPersonalSettings() throws Exception {
        User member = new User("personal-member", null);
        member.setRoles(Set.of(User.Role.SETTINGS, User.Role.STREAM));
        security.createUser(member, "secret", "test");
        User outsider = new User("personal-outsider", null);
        outsider.setRoles(Set.of(User.Role.STREAM)); security.createUser(outsider, "secret", "test");
        assertFalse(context.containsBean("personalSettingsController"));
        JsonNode initial = read("personal-member", "secret");
        JsonNode adminInitial = read("admin", "admin");
        assertEquals("personal-member", initial.path("username").asText());
        assertTrue(initial.path("lastfmCredsAbsent").asBoolean());
        assertTrue(initial.path("listenBrainzCredsAbsent").asBoolean());
        assertTrue(security.createCredential(new org.airsonic.player.domain.UserCredential("personal-member", "third-party-user", "TOP_SECRET_LASTFM", "noop", org.airsonic.player.domain.UserCredential.App.LASTFM)));
        assertTrue(security.createCredential(new org.airsonic.player.domain.UserCredential("personal-member", "third-party-user", "TOP_SECRET_LISTENBRAINZ", "noop", org.airsonic.player.domain.UserCredential.App.LISTENBRAINZ)));
        initial = read("personal-member", "secret");
        assertFalse(initial.path("lastfmCredsAbsent").asBoolean());
        assertFalse(initial.path("listenBrainzCredsAbsent").asBoolean());
        assertFalse(initial.toString().contains("TOP_SECRET")); assertFalse(initial.toString().contains("third-party-user"));
        error(api("getPersonalSettings", "personal-outsider", "secret", null), 50);
        assertEquals(405, raw("savePersonalSettings", null).statusCode());
        HttpClient browser = login("personal-member", "secret");
        for (String page : new String[]{"personalSettings", "personalSettings.view"}) {
            HttpResponse<String> response = get(browser, "/" + page);
            assertEquals(response.body(), 200, response.statusCode());
            assertTrue(response.body().contains("initPersonalSettings"));
            assertFalse(response.body().contains("<form:")); assertFalse(response.body().contains("TOP_SECRET"));
        }
        HttpClient outsiderBrowser = login("personal-outsider", "secret");
        assertEquals(403, get(outsiderBrowser, "/personalSettings.view").statusCode());
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body());
        assertTrue(token.find()); String csrf = "&_csrf=" + encode(token.group(1));
        String base = payload(initial, "");
        assertEquals(403, post(browser, "/rest/savePersonalSettings", "f=json" + base).statusCode());
        assertEquals(405, post(browser, "/personalSettings.view", csrf.substring(1)).statusCode());
        Matcher outsiderToken = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(outsiderBrowser, "/top.view").body());
        assertTrue(outsiderToken.find());
        error(website(outsiderBrowser, "savePersonalSettings", base + "&_csrf=" + encode(outsiderToken.group(1))), 50);
        java.time.Instant changed = settings.getUserSettings("personal-member").getChanged();
        for (String invalid : new String[]{"themeId=missing", "locale=xx-INVALID", "albumListId=missing", "avatarId=2147483647", "avatarId=bad",
                "paginationSizeFiles=0", "paginationSizeFolders=-2", "paginationSizePlaylist=2147483648", "searchCount=0", "audioBookmarkFrequency=0", "videoBookmarkFrequency=-1",
                "lastFmEnabled=yes", "mainVisibility.discNumberVisible=1", "listenBrainzUrl=javascript:alert(1)", "listenBrainzUrl=https://", "listenBrainzUrl=bad%20uri"}) {
            error(website(browser, "savePersonalSettings", invalid + "&partyModeEnabled=true" + base + csrf), 0);
            assertEquals(initial, read("personal-member", "secret"));
            assertEquals(changed, settings.getUserSettings("personal-member").getChanged());
        }
        error(website(browser, "savePersonalSettings", csrf), 0);
        String theme = initial.path("theme").get(0).path("id").asText();
        String locale = initial.path("localeOption").get(0).path("id").asText();
        StringBuilder changes = new StringBuilder("themeId=" + encode(theme) + "&locale=" + encode(locale)
                + "&albumListId=newest&avatarId=-2&listenBrainzUrl=" + encode(" https://listenbrainz.example.org/path?q=REST%26Ktor ")
                + "&username=admin&user.username=admin");
        // Exercise every editable scalar and every column visibility flag, including unchecked values.
        appendChangedFields(changes, initial, "");
        ok(website(browser, "savePersonalSettings", changes + base + csrf));
        JsonNode saved = read("personal-member", "secret");
        assertEquals("personal-member", saved.path("username").asText());
        assertEquals(theme, saved.path("themeId").asText()); assertEquals(locale, saved.path("locale").asText());
        assertEquals("newest", saved.path("albumListId").asText()); assertEquals(-2, saved.path("avatarId").asInt());
        assertEquals("https://listenbrainz.example.org/path?q=REST%26Ktor", saved.path("listenBrainzUrl").asText());
        assertChangedFields(initial, saved, "");
        assertEquals(adminInitial, read("admin", "admin"));
        assertFalse(changed.equals(settings.getUserSettings("personal-member").getChanged()));
        String xml = get(client, "/rest/getPersonalSettings?u=personal-member&p=secret&v=" + TestCaseUtils.restApiVersion() + "&c=test").body();
        assertFalse(xml.contains("TOP_SECRET"));
        org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        assertEquals("personal-member", ((org.w3c.dom.Element) document.getElementsByTagName("personalSettings").item(0)).getAttribute("username"));
        // System, custom and no-avatar schemes; empty locale/theme inherit global defaults.
        int avatar = saved.path("avatar").get(0).path("id").asInt();
        ok(website(browser, "savePersonalSettings", "avatarId=" + avatar + payload(saved, "") + csrf));
        assertEquals(org.airsonic.player.domain.AvatarScheme.SYSTEM, settings.getUserSettings("personal-member").getAvatarScheme());
        assertEquals(Integer.valueOf(avatar), settings.getUserSettings("personal-member").getSystemAvatarId());
        ok(website(browser, "savePersonalSettings", "themeId=&locale=&avatarId=-1&listenBrainzUrl=" + payload(saved, "") + csrf));
        org.airsonic.player.domain.UserSettings reset = settings.getUserSettings("personal-member");
        assertNull(reset.getLocale()); assertNull(reset.getThemeId()); assertNull(reset.getSystemAvatarId());
        assertNull(reset.getListenBrainzUrl()); assertEquals(org.airsonic.player.domain.AvatarScheme.NONE, reset.getAvatarScheme());
        // Unrelated settings managed by other endpoints are retained.
        assertTrue(reset.getShowSideBar()); assertFalse(reset.getViewAsList());
    }

    private void appendChangedFields(StringBuilder body, JsonNode node, String prefix) {
        node.fields().forEachRemaining(entry -> {
            String key = prefix + entry.getKey(); JsonNode value = entry.getValue();
            if (value.isObject() && entry.getKey().endsWith("Visibility")) appendChangedFields(body, value, key + ".");
            else if (editableBoolean(key, value)) body.append('&').append(key).append('=').append(!value.asBoolean());
            else if (editableInteger(key)) body.append('&').append(key).append('=').append(key.startsWith("pagination") ? -1 : value.asInt() + 3);
        });
    }
    private void assertChangedFields(JsonNode before, JsonNode after, String prefix) {
        before.fields().forEachRemaining(entry -> {
            String key = prefix + entry.getKey(); JsonNode value = entry.getValue();
            if (value.isObject() && entry.getKey().endsWith("Visibility")) assertChangedFields(value, after.path(entry.getKey()), key + ".");
            else if (editableBoolean(key, value)) assertEquals(key, !value.asBoolean(), after.path(entry.getKey()).asBoolean());
            else if (editableInteger(key)) assertEquals(key, key.startsWith("pagination") ? -1 : value.asInt() + 3, after.path(entry.getKey()).asInt());
        });
    }
    private boolean editableBoolean(String key, JsonNode value) {
        return value.isBoolean() && !Set.of("admin", "lastfmCredsAbsent", "listenBrainzCredsAbsent").contains(key);
    }
    private boolean editableInteger(String key) {
        return key.startsWith("paginationSize") || Set.of("audioBookmarkFrequency", "videoBookmarkFrequency", "searchCount").contains(key);
    }
    private String payload(JsonNode data, String prefix) {
        StringBuilder body = new StringBuilder();
        data.fields().forEachRemaining(entry -> {
            if (entry.getValue().isObject() && entry.getKey().endsWith("Visibility")) body.append(payload(entry.getValue(), entry.getKey() + "."));
            else if (entry.getValue().isValueNode()) body.append('&').append(prefix).append(entry.getKey()).append('=').append(encode(entry.getValue().asText()));
        });
        return body.toString();
    }
    private JsonNode read(String user, String password) throws Exception {
        JsonNode result = api("getPersonalSettings", user, password, null); ok(result); return result.path("personalSettings");
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
