package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class MusicFolderSettingsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    @Autowired private MediaFolderService folders;
    @Autowired private ApplicationContext context;
    @Autowired private org.airsonic.player.service.search.IndexManager indexManager;
    @MockitoSpyBean private MediaScannerService scanner;
    @MockitoSpyBean private org.airsonic.player.dao.ArtistDao artists;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void administerLibraryViaKtor() throws Exception {
        doNothing().when(scanner).scanLibrary();
        doNothing().when(scanner).schedule();
        clearInvocations(scanner, artists);
        User member = new User("folders-member", null);
        member.setRoles(Set.of(User.Role.SETTINGS, User.Role.STREAM));
        security.createUser(member, "secret", "test");
        assertFalse(context.containsBean("musicFolderSettingsController"));
        JsonNode initial = read("admin", "admin");
        assertTrue(initial.path("types").path("type").isArray());
        assertTrue(initial.has("scanning"));
        String baseSettings = "interval=-1&hour=3&fastCache=true&ignoreSymLinks=true&fullScan=false"
                + "&clearFullScanSettingAfterScan=true&uploadsFolder=uploads&excludePatternString=";
        error(api("getMusicFolderSettings", "folders-member", "secret", null), 50);
        verify(scanner, never()).scanLibrary();
        verify(artists, never()).expunge();
        assertEquals(405, raw("saveMusicFolderSettings", null).statusCode());
        assertEquals(405, raw("expungeMusicFolders", null).statusCode());
        HttpClient browser = login("admin", "admin");
        for (String page : new String[]{"musicFolderSettings", "musicFolderSettings.view?scanNow&expunge"}) {
            HttpResponse<String> response = get(browser, "/" + page);
            assertEquals(response.body(), 200, response.statusCode());
            assertTrue(response.body().contains("initMusicFolderSettings"));
            assertFalse(response.body().contains("${_csrf.token}"));
        }
        verify(scanner, never()).scanLibrary();
        verify(artists, never()).expunge();
        String top = get(browser, "/top.view").body();
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(top);
        assertTrue(token.find());
        String csrf = "&_csrf=" + encode(token.group(1));
        for (String operation : new String[]{"saveMusicFolderSettings", "expungeMusicFolders"}) {
            assertEquals(403, post(browser, "/rest/" + operation, "f=json&" + baseSettings).statusCode());
        }
        assertEquals(403, post(browser, "/rest/startScan", "f=json").statusCode());
        HttpClient memberBrowser = login("folders-member", "secret");
        assertEquals(403, get(memberBrowser, "/musicFolderSettings.view").statusCode());
        Matcher memberToken = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(memberBrowser, "/top.view").body());
        assertTrue(memberToken.find());
        for (String operation : new String[]{"saveMusicFolderSettings", "expungeMusicFolders"}) {
            error(website(memberBrowser, operation, baseSettings + "&_csrf=" + encode(memberToken.group(1))), 50);
        }
        error(mapper.readTree(get(memberBrowser, "/rest/getMusicFolderSettings?f=json").body()).path("subsonic-response"), 50);
        assertEquals(403, post(memberBrowser, "/rest/startScan", "f=json&_csrf=" + encode(memberToken.group(1))).statusCode());
        assertEquals(405, post(browser, "/musicFolderSettings.view", "_csrf=" + encode(token.group(1))).statusCode());
        Path root = Files.createTempDirectory(Path.of(System.getProperty("airsonic.home")), "admin-music-");
        String create = baseSettings + "&newMusicFolder.path=" + encode(root.toString())
                + "&newMusicFolder.name=" + encode("<Music & more>") + "&newMusicFolder.type=MEDIA&newMusicFolder.enabled=true";
        ok(website(browser, "saveMusicFolderSettings", create + csrf));
        MusicFolder folder = folders.getAllMusicFolders(true, true).stream().filter(f -> f.getPath().equals(root)).findFirst().orElseThrow();
        int id = folder.getId();
        assertEquals("<Music & more>", folder.getName());
        assertEquals(3, settings.getIndexCreationHour());
        assertTrue(settings.isFastCacheEnabled());
        assertTrue(settings.getIgnoreSymLinks());
        assertFalse(settings.getFullScan());
        assertFalse(settings.getClearFullScanSettingAfterScan());
        JsonNode data = read("admin", "admin");
        assertTrue(data.path("folders").path("folder").isArray());
        JsonNode createdFolder = null;
        for (JsonNode entry : data.path("folders").path("folder")) {
            if (entry.path("id").asInt() == id) createdFolder = entry;
        }
        assertNotNull(data.toString(), createdFolder);
        assertEquals("<Music & more>", createdFolder.path("name").asText());
        assertTrue(createdFolder.path("existing").asBoolean());
        JsonNode standard = api("getMusicFolders", "admin", "admin", null);
        assertTrue(standard.toString(), standard.path("musicFolders").path("musicFolder").findValuesAsText("name").contains("<Music & more>"));
        assertFalse(standard.has("musicFolderSettings"));
        String update = baseSettings + "&folderId=" + id + "&folder." + id + ".name=Renamed&folder." + id + ".path=" + encode(root.toString())
                + "&folder." + id + ".type=MEDIA&folder." + id + ".enabled=false";
        // Duplicate parameters use the first value: put invalid hour before the otherwise valid form.
        error(website(browser, "saveMusicFolderSettings", "hour=24&" + update + csrf), 0);
        error(website(browser, "saveMusicFolderSettings", "hour=bad&" + update + csrf), 0);
        error(website(browser, "saveMusicFolderSettings", update + "&folderId=999999" + csrf), 0);
        error(website(browser, "saveMusicFolderSettings", update + "&folderId=" + id + csrf), 0);
        error(website(browser, "saveMusicFolderSettings", "excludePatternString=%5B&" + update + csrf), 0);
        error(website(browser, "saveMusicFolderSettings", "folder." + id + ".type=INVALID&" + update + csrf), 0);
        assertEquals("<Music & more>", folders.getMusicFolderById(id, true, true).getName());
        assertTrue(folders.getMusicFolderById(id, true, true).isEnabled());
        ok(website(browser, "saveMusicFolderSettings", update + csrf));
        assertFalse(folders.getMusicFolderById(id, true, true).isEnabled());
        assertEquals("Renamed", folders.getMusicFolderById(id, true, true).getName());
        String xml = get(client, "/rest/getMusicFolderSettings?u=admin&p=admin&v=" + TestCaseUtils.restApiVersion() + "&c=test").body();
        assertTrue(xml, xml.contains("<musicFolderSettings"));
        assertTrue(xml, xml.contains("name=\"Renamed\""));
        ok(website(browser, "startScan", csrf));
        verify(scanner, times(1)).scanLibrary();
        ok(website(browser, "saveMusicFolderSettings", baseSettings + "&folderId=" + id + "&folder." + id + ".delete=true" + csrf));
        assertNull(folders.getMusicFolderById(id, true, true));
        indexManager.startIndexing();
        indexManager.stopIndexing(new org.airsonic.player.domain.MediaLibraryStatistics());
        assertNotNull(indexManager.getStatistics());
        ok(website(browser, "expungeMusicFolders", csrf));
        assertNotNull(indexManager.getStatistics());
        verify(artists, times(1)).expunge();
        verify(scanner, times(1)).scanLibrary();
    }

    private JsonNode read(String user, String password) throws Exception {
        JsonNode body = api("getMusicFolderSettings", user, password, null);
        ok(body); return body.path("musicFolderSettings");
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
