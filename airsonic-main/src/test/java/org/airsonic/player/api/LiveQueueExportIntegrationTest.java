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
public class LiveQueueExportIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private org.airsonic.player.service.PlayerService players;
    @Autowired private org.airsonic.player.service.JWTSecurityService jwt;
    @Autowired private org.airsonic.player.service.TranscodingService transcoding;
    @Autowired private org.airsonic.player.service.MediaFileService mediaService;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void exportsActiveQueueAsSignedM3uWithPlayerAndFolderPermissions() throws Exception {
        assertFalse(context.containsBean("m3UController"));
        var library = MusicFolderTestData.getTestMusicFolders();
        var cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            User user = new User("m3u-owner", null); user.setRoles(java.util.Set.of(User.Role.STREAM));
            security.createUser(user, "secret", "test");
            folders.setMusicFoldersForUser(user.getUsername(), java.util.Set.of(library.get(0).getId()));
            HttpClient browser = login(user.getUsername(), "secret");
            var album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            var songs = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true);
            var first = songs.get(0); var second = songs.get(1);
            first.setArtist("Артист\r\n#EXTINF:0,Injected"); first.setTitle("Трек\nInjected"); first.setDuration(null);
            mediaService.updateMediaFile(first);
            var player = new org.airsonic.player.domain.Player(); player.setUsername(user.getUsername());
            player.setTechnology(org.airsonic.player.domain.PlayerTechnology.EXTERNAL_WITH_PLAYLIST);
            player.setM3uBomEnabled(false); players.createPlayer(player);
            player.getPlayQueue().addFiles(false, List.of(first, second, first));
            String path = "/rest/exportLivePlayQueue?playerId=" + player.getId();
            var response = get(browser, path); assertM3u(response);
            var lines = response.body().lines().toList();
            assertEquals(7, lines.size()); assertEquals("#EXTM3U", lines.get(0));
            assertEquals("#EXTINF:-1.0,Артист  #EXTINF:0,Injected - Трек Injected", lines.get(1));
            assertUrl(lines.get(2), user.getUsername(), player.getId(), first.getId(), transcoding.getSuffix(player, first, null));
            assertUrl(lines.get(4), user.getUsername(), player.getId(), second.getId(), transcoding.getSuffix(player, second, null));
            assertUrl(lines.get(6), user.getUsername(), player.getId(), first.getId(), transcoding.getSuffix(player, first, null));
            // Generated URLs remain usable by a player with no browser session.
            var audio = client.send(HttpRequest.newBuilder(URI.create(lines.get(2))).header("Range", "bytes=0-9").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(206, audio.statusCode());
            assertArrayEquals(java.util.Arrays.copyOf(java.nio.file.Files.readAllBytes(first.getFullPath(library.get(0).getPath())), 10), audio.body());
            assertEquals(3, player.getPlayQueue().getFiles().size());
            player.setM3uBomEnabled(true); players.updatePlayer(player);
            assertTrue(get(browser, path).body().startsWith("\uFEFF#EXTM3U\n"));
            player.setTechnology(org.airsonic.player.domain.PlayerTechnology.EXTERNAL); players.updatePlayer(player);
            response = get(browser, path); assertM3u(response); lines = response.body().lines().toList();
            assertEquals(3, lines.size()); assertEquals("#EXTINF:-1,Airsonic", lines.get(1));
            assertUrl(lines.get(2), user.getUsername(), player.getId(), null, transcoding.getSuffix(player, first, null));
            player.getPlayQueue().clear();
            lines = get(browser, path).body().lines().toList();
            assertEquals(3, lines.size()); assertUrl(lines.get(2), user.getUsername(), player.getId(), null, null);
            player.setTechnology(org.airsonic.player.domain.PlayerTechnology.EXTERNAL_WITH_PLAYLIST); player.setM3uBomEnabled(false); players.updatePlayer(player);
            assertEquals("#EXTM3U\n", get(browser, path).body());
            // Omitting playerId resolves the selected website player as before.
            get(browser, "/playQueue/player?player=" + player.getId());
            assertEquals("#EXTM3U\n", get(browser, "/rest/exportLivePlayQueue").body());
            assertM3u(get(client, path + "&u=m3u-owner&p=secret&v=" + TestCaseUtils.restApiVersion() + "&c=m3u-test"));
            var foreign = new org.airsonic.player.domain.Player(); foreign.setUsername("admin"); players.createPlayer(foreign);
            for (String id : List.of("bad", "", "2147483647", foreign.getId().toString()))
                assertError(get(browser, "/rest/exportLivePlayQueue?f=json&playerId=" + id), 50);
            // The second fixture folder is inaccessible to this owner, even if its files reach a live queue.
            var inaccessible = mediaFiles.getChildrenOf("", library.get(1).getId(), true).get(0);
            player.getPlayQueue().addFiles(false, List.of(inaccessible));
            assertError(get(browser, path + "&f=json"), 50);
            var xml = get(browser, path); assertTrue(xml.body(), xml.body().contains("code=\"50\""));
            assertFalse(xml.headers().firstValue("Content-Disposition").isPresent());
            assertError(get(client, path + "&f=json"), 10);
            assertError(get(client, path + "&f=json&u=m3u-owner&p=wrong&v=" + TestCaseUtils.restApiVersion() + "&c=m3u-test"), 40);
            for (String method : List.of("POST", "PUT", "DELETE", "PATCH"))
                assertEquals(405, browser.send(HttpRequest.newBuilder(URI.create(base() + path)).header("X-CSRF-TOKEN", csrf(browser))
                        .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(404, get(browser, "/play.m3u").statusCode());
            var page = get(browser, "/playQueue.view?player=" + player.getId());
            assertEquals(page.body(), 200, page.statusCode());
            assertTrue(page.body().contains("/airsonic/rest/exportLivePlayQueue")); assertFalse(page.body().contains("play.m3u?"));
        } finally { ScanningTestUtils.after(cleanup, folders); folders.expunge(); }
    }
    private void assertM3u(HttpResponse<String> response) {
        assertEquals(response.body(), 200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse(""), response.headers().firstValue("Content-Type").orElse("").startsWith("audio/x-mpegurl"));
        assertTrue(response.headers().firstValue("Content-Type").orElse("").toLowerCase().contains("charset=utf-8"));
        assertEquals("attachment; filename=play.m3u", response.headers().firstValue("Content-Disposition").orElse(""));
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
    }
    private void assertUrl(String value, String username, int playerId, Integer fileId, String suffix) {
        URI uri = URI.create(value);
        assertEquals("/airsonic/ext/stream", uri.getPath());
        var params = queryParams(uri.getRawQuery());
        assertEquals(String.valueOf(playerId), params.get("player"));
        assertEquals(fileId == null ? null : fileId.toString(), params.get("id"));
        assertEquals(suffix == null ? null : "." + suffix, params.get("suffix"));
        var token = jwt.verify(params.get("jwt"));
        assertEquals(username, token.getSubject());
        assertEquals("ext/stream?player=" + playerId + (fileId == null ? "" : "&id=" + fileId) + (suffix == null ? "" : "&suffix=." + suffix), token.getClaim("path").asString());
        assertTrue(token.getExpiresAt().toInstant().isAfter(java.time.Instant.now()));
    }

    private static java.util.Map<String, String> queryParams(String query) {
        if (query == null) return java.util.Collections.emptyMap();
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                map.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return map;
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
