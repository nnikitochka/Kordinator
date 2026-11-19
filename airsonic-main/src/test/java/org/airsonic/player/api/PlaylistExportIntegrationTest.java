package org.airsonic.player.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.PlaylistService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.util.MusicFolderTestData;
import java.util.List;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PlaylistExportIntegrationTest {

    @Autowired
    private org.airsonic.player.runtime.DaoGraph daoGraph;

    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private PlaylistService playlists;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;

    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void exportsFilesWithPlaylistPermissionsAndRestErrors() throws Exception {
        assertFalse(context.containsBean("exportPlayListController"));
        var library = MusicFolderTestData.getTestMusicFolders();
        var cleanup = ScanningTestUtils.before(library, folders, scanner);
        var created = new java.util.ArrayList<Integer>();
        try {
            User member = new User("export-member", null);
            security.createUser(member, "secret", "test");
            HttpClient admin = login("admin", "admin");
            HttpClient browser = login(member.getUsername(), "secret");
            var album = daoGraph.getMediaFileDao().getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            var songs = daoGraph.getMediaFileDao().getChildrenOf(album.getPath(), album.getFolderId(), true);
            var playlist = new org.airsonic.player.domain.Playlist();
            playlist.setUsername("admin"); playlist.setName("Музыка \"quoted\" / <test>\r\nInjected: yes");
            playlist.setCreated(java.time.Instant.now()); playlist.setChanged(java.time.Instant.now()); playlist.setShared(false);
            playlists.createPlaylist(playlist); int id = playlist.getId(); created.add(id);
            playlists.setFilesInPlaylist(id, List.of(songs.get(0), songs.get(1), songs.get(0)));
            String url = "/rest/exportPlaylist?id=" + id;
            assertError(get(browser, url + "&f=json"), 50);
            assertError(get(client, url + "&f=json"), 10);
            assertError(get(client, url + "&f=json&u=admin&p=wrong&c=export-test&v=" + TestCaseUtils.restApiVersion()), 40);
            assertError(get(admin, "/rest/exportPlaylist?f=json"), 10);
            for (String invalid : List.of("bad", "2147483648", "")) assertError(get(admin, "/rest/exportPlaylist?f=json&id=" + invalid), 0);
            assertError(get(admin, "/rest/exportPlaylist?f=json&id=2147483647"), 70);
            var xml = get(admin, "/rest/exportPlaylist?id=2147483647");
            assertTrue(xml.body(), xml.body().contains("status=\"failed\""));
            assertTrue(xml.body(), xml.body().contains("code=\"70\""));
            assertFalse(xml.headers().firstValue("Content-Disposition").isPresent());
            var expected = new java.io.ByteArrayOutputStream(); playlists.exportPlaylist(id, expected);
            assertExport(get(admin, url), expected.toString(StandardCharsets.UTF_8));
            playlists.addPlaylistUser(playlist, member.getUsername());
            assertExport(get(browser, url + "&f=json"), expected.toString(StandardCharsets.UTF_8));
            playlist.setShared(true); playlists.updatePlaylist(playlist);
            User shared = new User("export-shared", null); security.createUser(shared, "secret", "test");
            assertExport(get(login(shared.getUsername(), "secret"), url), expected.toString(StandardCharsets.UTF_8));
            // Query authentication continues to work for external REST clients.
            assertExport(get(client, url + "&u=admin&p=admin&v=" + TestCaseUtils.restApiVersion() + "&c=export-test"), expected.toString(StandardCharsets.UTF_8));
            String token = csrf(admin);
            for (String method : List.of("POST", "PUT", "DELETE", "PATCH")) {
                assertEquals(405, admin.send(HttpRequest.newBuilder(URI.create(base() + url)).header("X-CSRF-TOKEN", token)
                    .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            assertEquals(404, get(admin, "/exportPlaylist.view?id=" + id).statusCode());
            assertEquals(404, get(admin, "/exportPlaylist?id=" + id).statusCode());
            for (String page : List.of("/playlist.view?id=" + id, "/playlists.view")) {
                var html = get(admin, page); assertEquals(html.body(), 200, html.statusCode());
                assertTrue(html.body(), html.body().contains("/airsonic/rest/exportPlaylist"));
                assertFalse(html.body(), html.body().contains("exportPlaylist.view"));
            }
            playlists.setFilesInPlaylist(id, List.of());
            expected.reset(); playlists.exportPlaylist(id, expected);
            assertExport(get(admin, url), expected.toString(StandardCharsets.UTF_8));
        } finally {
            for (int id : created) playlists.deletePlaylist(id);
            ScanningTestUtils.after(cleanup, folders);
        }
    }
    private void assertExport(HttpResponse<String> response, String expected) {
        assertEquals(response.body(), 200, response.statusCode()); assertEquals(expected, response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse(""), response.headers().firstValue("Content-Type").orElse("").startsWith("application/x-download"));
        String header = response.headers().firstValue("Content-Disposition").orElseThrow();
        assertTrue(header, header.startsWith("attachment;")); assertTrue(header, header.contains(".m3u8"));
        assertTrue(header, header.contains("filename*=utf-8\'\'%D0%9C%D1%83"));
        assertFalse(header.contains("\r")); assertFalse(header.contains("\n"));
        assertFalse(response.headers().firstValue("Injected").isPresent());
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
    }
    private void assertError(HttpResponse<String> response, int code) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        var result = mapper.readTree(response.body()).path("subsonic-response");
        assertEquals(response.body(), "failed", result.path("status").asText());
        assertEquals(response.body(), code, result.path("error").path("code").asInt());
        assertFalse(response.headers().firstValue("Content-Disposition").isPresent());
    }
    private String csrf(HttpClient browser) throws Exception {
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body()); assertTrue(token.find()); return token.group(1);
    }
    private HttpClient login(String username, String password) throws Exception {
        HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(get(browser, "/login").body());
        assertTrue(token.find());
        assertEquals(302, post(browser, "/login", "j_username=" + username + "&j_password=" + password + "&_csrf=" + encode(token.group(1))).statusCode());
        return browser;
    }
    private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(HttpRequest.newBuilder().timeout(java.time.Duration.ofSeconds(30)).uri(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient browser, String path, String form) throws Exception {
        return browser.send(HttpRequest.newBuilder().timeout(java.time.Duration.ofSeconds(30)).uri(URI.create(base() + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String base() { return "http://localhost:" + port + "/airsonic"; }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
