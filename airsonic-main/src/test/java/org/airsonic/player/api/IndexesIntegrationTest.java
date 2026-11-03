package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.controller.LeftController;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.util.HomeRule;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import java.net.URI;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.http.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class IndexesIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private SettingsService settings;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    @MockitoSpyBean private LeftController leftController;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void indexesPreserveClientContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            int first = library.get(0).getId();
            int second = library.get(1).getId();
            User restricted = new User("indexes-restricted", null);
            restricted.setRoles(Set.of(User.Role.STREAM));
            security.createUser(restricted, "secret", "test");
            folders.setMusicFoldersForUser(restricted.getUsername(), Set.of(first));
            for (String suffix : List.of("", ".view")) {
                JsonNode all = body(send(suffix, "f=json", false)).path("indexes");
                assertTrue(all.toString(), all.path("lastModified").asLong() > 0);
                assertFalse(all.path("ignoredArticles").asText().isEmpty());
                Set<String> allArtists = artistIds(all);
                assertFalse(all.toString(), allArtists.isEmpty());
                JsonNode one = body(send(suffix, "f=json&musicFolderId=" + first, false)).path("indexes");
                assertEquals(first, settings.getUserSettings("admin").getSelectedMusicFolderId());
                Set<String> oneArtists = artistIds(one);
                assertEquals(oneArtists, artistIds(body(send(suffix, "f=json&musicFolderId=" + second, false, "indexes-restricted", "secret")).path("indexes")));
                assertFalse(one.toString(), oneArtists.isEmpty());
                JsonNode two = body(send(suffix, "f=json&musicFolderId=" + second, false)).path("indexes");
                assertEquals(second, settings.getUserSettings("admin").getSelectedMusicFolderId());
                Set<String> twoArtists = artistIds(two);
                assertTrue(allArtists.containsAll(oneArtists));
                assertTrue(allArtists.containsAll(twoArtists));
                assertTrue(Collections.disjoint(oneArtists, twoArtists));
                assertEquals(allArtists, artistIds(body(send(suffix, "f=json&musicFolderId=2147483647", false)).path("indexes")));
                assertEquals(2147483647, settings.getUserSettings("admin").getSelectedMusicFolderId());
                assertTrue(body(send(suffix, "f=json&ifModifiedSince=9223372036854775807", false)).path("indexes").isMissingNode());
                assertEquals(400, send(suffix, "f=json&musicFolderId=bad&ifModifiedSince=9223372036854775807", false).statusCode());
                assertFalse(body(send(suffix, "f=json&ifModifiedSince=bad", false)).path("indexes").isMissingNode());
                HttpResponse<String> xml = send(suffix, "f=xml", false);
                assertTrue(xml.body(), xml.body().contains("<indexes "));
                HttpResponse<String> jsonp = send(suffix, "f=jsonp&callback=client.indexes", false);
                assertTrue(jsonp.body(), jsonp.body().startsWith("client.indexes("));
            }
            JsonNode beforeStar = body(send("", "f=json&musicFolderId=" + first, false)).path("indexes");
            int starredId = Integer.parseInt(artistIds(beforeStar).iterator().next());
            mediaFiles.starMediaFile(starredId, "admin");
            JsonNode starred = body(send("", "f=json&musicFolderId=" + first, false)).path("indexes");
            boolean foundStarred = false;
            for (JsonNode index : starred.path("index")) {
                for (JsonNode artist : index.path("artist")) {
                    if (artist.path("id").asInt() == starredId) foundStarred = !artist.path("starred").isMissingNode();
                }
            }
            assertTrue(starred.toString(), foundStarred);
            settings.setShortcuts("\"_DIR_ Sixteen Horsepower\"");
            JsonNode withShortcut = body(send("", "f=json&musicFolderId=" + first, false)).path("indexes");
            assertEquals(withShortcut.toString(), 1, withShortcut.path("shortcut").size());
            assertEquals("_DIR_ Sixteen Horsepower", withShortcut.path("shortcut").get(0).path("name").asText());
            assertEquals("ok", body(send(".view", "f=json", true)).path("status").asText());
            assertEquals(403, send("", "f=json", true).statusCode());
            assertTrue(body(send("", "f=json&musicFolderId=" + second + "&ifModifiedSince=9223372036854775807", false)).path("indexes").isMissingNode());
            assertEquals(second, settings.getUserSettings("admin").getSelectedMusicFolderId());
            verifyNoInteractions(leftController);
            websiteSelectsFolder(first);

        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }
    private void websiteSelectsFolder(int folderId) throws Exception {
        HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        String base = "http://localhost:" + port + "/airsonic";
        HttpResponse<String> login = browser.send(HttpRequest.newBuilder(URI.create(base + "/login")).GET().build(), HttpResponse.BodyHandlers.ofString());
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
        assertTrue(token.find());
        String form = "j_username=admin&j_password=admin&_csrf=" + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
        assertEquals(302, browser.send(HttpRequest.newBuilder(URI.create(base + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        var changed = settings.getUserSettings("admin").getChanged();
        HttpResponse<String> page = browser.send(HttpRequest.newBuilder(URI.create(base + "/left.view?musicFolderId=" + folderId)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(page.body(), 200, page.statusCode());
        assertEquals(folderId, settings.getUserSettings("admin").getSelectedMusicFolderId());
        assertEquals(changed, settings.getUserSettings("admin").getChanged());
    }
    private Set<String> artistIds(JsonNode indexes) {
        Set<String> ids = new HashSet<>();
        indexes.path("index").forEach(index -> index.path("artist").forEach(artist -> ids.add(artist.path("id").asText())));
        return ids;
    }
    private HttpResponse<String> send(String suffix, String query, boolean post) throws Exception {
        return send(suffix, query, post, "admin", "admin");
    }
    private HttpResponse<String> send(String suffix, String query, boolean post, String user, String password) throws Exception {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=indexes-test&u=" + user + "&p=" + password + "&" + query;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/getIndexes" + suffix + (post ? "" : "?" + params)));
        if (post) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(params));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
}
