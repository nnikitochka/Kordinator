package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.HomeRule;
import org.airsonic.player.util.MusicFolderTestData;
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
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class WebShareIntegrationTest {

    @Autowired
    private org.airsonic.player.runtime.DaoGraph daoGraph;

    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private ApplicationContext context;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;

    @Autowired private SecurityService security;
    @Autowired private org.airsonic.player.service.MediaFileService mediaService;
    @Autowired private org.airsonic.player.service.ShareService shares;
    @Autowired private org.airsonic.player.service.PlayerService players;
    @Autowired private org.airsonic.player.service.PlaylistService playlists;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void createsSharesFromWebsiteSelectionsWithPermissionsAndCsrf() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = daoGraph.getMediaFileDao().getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            var songs = mediaService.getChildrenOf(album, true, false, true);
            User user = new User("web-sharer", null);
            user.setRoles(Set.of(User.Role.SHARE, User.Role.STREAM));
            security.createUser(user, "secret", "test");
            folders.setMusicFoldersForUser(user.getUsername(), Set.of(library.get(0).getId()));
            User denied = new User("web-share-denied", null);
            denied.setRoles(Set.of(User.Role.SHARE)); security.createUser(denied, "secret", "test");
            folders.setMusicFoldersForUser(denied.getUsername(), Set.of(library.get(1).getId()));
            security.createUser(new User("web-reader", null), "secret", "test");
            var browser = login(user.getUsername());
            String path = "/rest/createWebShare";
            int before = shares.getSharesForUser(user).size();
            HttpResponse<String> page = send(browser, "/createShare.view?id=" + album.getId(), null, null);
            assertEquals(page.body(), 200, page.statusCode());
            assertTrue(page.body().contains("/airsonic/script/create-share.js?v=1"));
            assertEquals(before, shares.getSharesForUser(user).size()); // Page render has no write side effects.
            String csrf = csrf(browser);
            assertEquals(403, send(browser, path, "f=json&id=" + album.getId(), null).statusCode());
            assertEquals(405, send(browser, path, null, null).statusCode());
            assertEquals(405, send(browser, "/createShare.view", "id=" + album.getId(), csrf).statusCode());
            assertFalse(context.containsBean("shareManagementController"));
            var created = share(send(browser, path, "f=json&c=airsonic-web&id=" + album.getId(), csrf));
            assertEquals("web-sharer", created.path("username").asText());
            assertTrue(created.path("url").asText().contains("/airsonic/ext/rest/share/"));
            assertEquals(1, created.path("entry").size());
            assertEquals(album.getId().intValue(), created.path("entry").get(0).path("id").asInt());
            var selected = share(send(browser, path, "f=json&id=" + album.getId() + "&i=1", csrf));
            assertEquals(songs.get(1).getId().intValue(), selected.path("entry").get(0).path("id").asInt());
            assertEquals(1, selected.path("entry").size());
            var multiple = share(send(browser, path, "f=json&id=" + album.getId() + "&i=1&i=0", csrf));
            assertEquals(2, multiple.path("entry").size());
            assertEquals(Set.of(songs.get(0).getId(), songs.get(1).getId()), Set.of(
                    multiple.path("entry").get(0).path("id").asInt(), multiple.path("entry").get(1).path("id").asInt()));
            var player = new org.airsonic.player.domain.Player(); player.setUsername(user.getUsername()); players.createPlayer(player);
            player.getPlayQueue().addFiles(false, List.of(songs.get(0), songs.get(1)));
            var queue = share(send(browser, path, "f=json&player=" + player.getId() + "&i=1", csrf));
            assertEquals(1, queue.path("entry").size());
            assertEquals(songs.get(1).getId().intValue(), queue.path("entry").get(0).path("id").asInt());
            assertEquals(2, player.getPlayQueue().getFiles().size());
            var playlist = new org.airsonic.player.domain.Playlist();
            playlist.setUsername("admin"); playlist.setName("Плейлист <test> & музыка");
            playlist.setCreated(Instant.now()); playlist.setChanged(Instant.now()); playlist.setShared(false);
            playlists.createPlaylist(playlist); playlists.setFilesInPlaylist(playlist.getId(), List.of(songs.get(1)));
            assertError(send(browser, path, "f=json&playlist=" + playlist.getId(), csrf), 50);
            playlist.setShared(true); playlists.updatePlaylist(playlist);
            var fromPlaylist = share(send(browser, path, "f=json&playlist=" + playlist.getId(), csrf));
            assertEquals(playlist.getName(), fromPlaylist.path("description").asText());
            assertEquals(songs.get(1).getId().intValue(), fromPlaylist.path("entry").get(0).path("id").asInt());
            var foreign = new org.airsonic.player.domain.Player(); foreign.setUsername("admin"); players.createPlayer(foreign);
            before = shares.getSharesForUser(user).size();
            for (String invalid : List.of("id=bad", "id=" + album.getId() + "&i=-1", "id=" + album.getId() + "&i=999999", "id=" + album.getId() + "&i=x", "id=" + album.getId() + "&player=" + player.getId()))
                assertError(send(browser, path, "f=json&" + invalid, csrf), 0);
            assertError(send(browser, path, "f=json", csrf), 10);
            assertError(send(browser, path, "f=json&id=2147483647", csrf), 70);
            assertError(send(browser, path, "f=json&playlist=2147483647", csrf), 70);
            assertError(send(browser, path, "f=json&player=" + foreign.getId(), csrf), 50);
            player.getPlayQueue().clear();
            assertError(send(browser, path, "f=json&player=" + player.getId(), csrf), 0);
            assertEquals(before, shares.getSharesForUser(user).size());
            HttpResponse<String> xml = send(browser, path, "id=" + songs.get(0).getId(), csrf);
            assertTrue(xml.body(), xml.body().contains("<shares>"));
            assertTrue(xml.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            for (String username : List.of("web-reader", "web-share-denied")) {
                var deniedBrowser = login(username);
                assertError(send(deniedBrowser, path, "f=json&id=" + songs.get(0).getId(), csrf(deniedBrowser)), 50);
            }
            assertEquals(403, send(HttpClient.newHttpClient(), path, "id=" + album.getId(), null).statusCode());
            playlists.deletePlaylist(playlist.getId());
        } finally {
            ScanningTestUtils.after(cleanup, folders); folders.expunge();
        }
    }
    private HttpClient login(String username) throws Exception {
        var browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(send(browser, "/login", null, null).body());
        assertTrue(token.find());
        send(browser, "/login", "j_username=" + username + "&j_password=secret&_csrf=" + enc(token.group(1)), null);
        return browser;
    }
    private String csrf(HttpClient browser) throws Exception {
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(send(browser, "/top.view", null, null).body());
        assertTrue(token.find()); return token.group(1);
    }
    private JsonNode share(HttpResponse<String> response) throws Exception {
        var result = body(response); assertEquals(response.body(), "ok", result.path("status").asText());
        return result.path("shares").path("share").get(0);
    }
    private void assertError(HttpResponse<String> response, int code) throws Exception {
        assertEquals(response.body(), code, body(response).path("error").path("code").asInt());
    }
    private String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private HttpResponse<String> send(HttpClient client, String path, String form, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (form != null) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
}
