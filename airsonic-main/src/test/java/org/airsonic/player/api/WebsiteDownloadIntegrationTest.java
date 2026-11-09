package org.airsonic.player.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
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
public class WebsiteDownloadIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private org.airsonic.player.service.PlaylistService playlists;
    @Autowired private org.airsonic.player.service.MediaFileService media;
    @Autowired private org.airsonic.player.service.StatusService statuses;
    @Autowired private org.airsonic.player.service.PlayerService players;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void downloadsWebsiteSelectionsWithPermissionsUnicodeArchivesAndRanges() throws Exception {
        assertFalse(context.containsBean("downloadController"));
        var temp = java.nio.file.Files.createTempDirectory("airsonic-download-");
        var albumPath = java.nio.file.Files.createDirectories(temp.resolve("Артист/Альбом"));
        var original = MusicFolderTestData.resolveMusicFolderPath().resolve("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes/Sixteen Horsepower - 01 I Seen What I Saw.mp3");
        java.nio.file.Files.copy(original, albumPath.resolve("Песня первая.mp3"));
        java.nio.file.Files.copy(original, albumPath.resolve("Песня вторая.mp3"));
        var own = new org.airsonic.player.domain.MusicFolder(1, temp, "Downloads", org.airsonic.player.domain.MusicFolder.Type.MEDIA, true, java.time.Instant.now());
        var other = MusicFolderTestData.getTestMusicFolders().get(1);
        var cleanup = ScanningTestUtils.before(List.of(own, other), folders, scanner);
        try {
            User owner = new User("website-download-owner", null); owner.setRoles(java.util.Set.of(User.Role.DOWNLOAD, User.Role.STREAM));
            security.createUser(owner, "secret", "test"); folders.setMusicFoldersForUser(owner.getUsername(), java.util.Set.of(own.getId()));
            User reader = new User("website-download-reader", null); security.createUser(reader, "secret", "test");
            var browser = login(owner.getUsername(), "secret");
            var album = mediaFiles.getMediaFile("Артист/Альбом", own.getId());
            var songs = media.getChildrenOf(album, true, false, true);
            var song = songs.get(0); var second = songs.get(1);
            byte[] expected = java.nio.file.Files.readAllBytes(original);
            String path = "/rest/downloadMedia";
            String file = path + "?id=" + song.getId();
            long before = security.getUserByName(owner.getUsername()).getBytesDownloaded();
            var head = bytes(browser, file, "HEAD", null, null); assertEquals(200, head.statusCode());
            assertEquals(expected.length, head.headers().firstValueAsLong("Content-Length").orElse(-1)); assertEquals(0, head.body().length);
            assertEquals(before, security.getUserByName(owner.getUsername()).getBytesDownloaded());
            var full = bytes(browser, file, "GET", null, null); assertFile(full, expected);
            String disposition = full.headers().firstValue("Content-Disposition").orElseThrow();
            assertTrue(disposition, disposition.contains("filename*=utf-8''%D0%9F"));
            assertTrue(full.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            assertEquals(304, bytes(browser, file, "GET", "If-None-Match", full.headers().firstValue("ETag").orElseThrow()).statusCode());
            assertEquals(304, bytes(browser, file, "GET", "If-Modified-Since", full.headers().firstValue("Last-Modified").orElseThrow()).statusCode());
            var range = bytes(browser, file, "GET", "Range", "bytes=0-9"); assertEquals(206, range.statusCode()); assertArrayEquals(java.util.Arrays.copyOf(expected, 10), range.body());
            assertEquals("bytes 0-9/" + expected.length, range.headers().firstValue("Content-Range").orElse(""));
            assertEquals(206, bytes(browser, file, "HEAD", "Range", "bytes=0-9").statusCode());
            assertEquals(206, bytes(browser, file, "GET", "Range", "bytes=0-2,10-12").statusCode());
            assertEquals(416, bytes(browser, file, "GET", "Range", "bytes=999999999-").statusCode());
            var archive = bytes(browser, path + "?id=" + album.getId(), "GET", null, null);
            assertZip(archive, java.util.Set.of("Песня первая.mp3", "Песня вторая.mp3"), expected);
            assertFile(bytes(browser, path + "?id=" + album.getId() + "&i=1", "GET", null, null), expected);
            assertZip(bytes(browser, path + "?id=" + album.getId() + "&i=1,0", "GET", null, null), java.util.Set.of("Песня первая.mp3", "Песня вторая.mp3"), expected);
            var playlist = new org.airsonic.player.domain.Playlist(); playlist.setUsername("admin"); playlist.setName("Список <test>\r\nInjected: yes");
            playlist.setCreated(java.time.Instant.now()); playlist.setChanged(java.time.Instant.now()); playlist.setShared(false);
            playlists.createPlaylist(playlist); playlists.setFilesInPlaylist(playlist.getId(), songs);
            String playlistPath = path + "?playlist=" + playlist.getId();
            assertError(get(browser, playlistPath + "&f=json"), 50);
            playlists.addPlaylistUser(playlist, owner.getUsername());
            assertZip(bytes(browser, playlistPath, "GET", null, null), java.util.Set.of("Песня первая.mp3", "Песня вторая.mp3"), expected);
            assertFalse(bytes(browser, playlistPath, "HEAD", null, null).headers().firstValue("Injected").isPresent());
            assertFile(bytes(browser, playlistPath + "&i=0", "GET", null, null), expected);
            var player = new org.airsonic.player.domain.Player(); player.setUsername(owner.getUsername()); players.createPlayer(player);
            player.getPlayQueue().addFiles(false, songs);
            String queuePath = path + "?player=" + player.getId();
            assertZip(bytes(browser, queuePath, "GET", null, null), java.util.Set.of("Песня первая.mp3", "Песня вторая.mp3"), expected);
            assertFile(bytes(browser, queuePath + "&i=1", "GET", null, null), expected);
            assertEquals(2, player.getPlayQueue().getFiles().size());
            var foreign = new org.airsonic.player.domain.Player(); foreign.setUsername("admin"); players.createPlayer(foreign);
            assertError(get(browser, path + "?f=json&player=" + foreign.getId()), 50);
            assertError(get(browser, path + "?f=json&player=2147483647"), 50);
            for (String invalid : List.of("id=bad", "player=bad", "playlist=bad", "id=" + album.getId() + "&i=-1", "id=" + album.getId() + "&i=99", "id=" + album.getId() + "&i=x", "id=" + song.getId() + "&playlist=" + playlist.getId()))
                assertError(get(browser, path + "?f=json&" + invalid), 0);
            assertError(get(browser, path + "?f=json"), 10);
            assertError(get(browser, path + "?f=json&id=2147483647"), 70);
            assertError(get(browser, path + "?f=json&playlist=2147483647"), 70);
            var inaccessible = mediaFiles.getChildrenOf("", other.getId(), true).get(0);
            assertError(get(browser, path + "?f=json&id=" + inaccessible.getId()), 50);
            playlists.setFilesInPlaylist(playlist.getId(), List.of(song, inaccessible));
            assertError(get(browser, playlistPath + "&f=json"), 50);
            // An accessible selected track remains downloadable from a mixed-folder playlist.
            assertFile(bytes(browser, playlistPath + "&i=0", "GET", null, null), expected);
            player.getPlayQueue().clear(); player.getPlayQueue().addFiles(false, List.of(inaccessible));
            assertError(get(browser, queuePath + "&f=json"), 50);
            player.getPlayQueue().clear();
            var empty = bytes(browser, queuePath, "GET", null, null); assertEquals(200, empty.statusCode()); assertEquals(0, empty.body().length);
            assertTrue(empty.headers().firstValue("Content-Disposition").orElse("").contains("emptyfile.download"));
            assertError(get(login(reader.getUsername(), "secret"), file + "&f=json"), 50);
            assertError(get(client, file + "&f=json"), 10);
            assertFile(bytes(client, file + "&u=" + owner.getUsername() + "&p=secret&v=" + TestCaseUtils.restApiVersion() + "&c=download-web-test", "GET", null, null), expected);
            var xml = get(browser, path + "?id=2147483647"); assertTrue(xml.body(), xml.body().contains("code=\"70\""));
            assertFalse(xml.headers().firstValue("Content-Disposition").isPresent());
            for (String method : List.of("POST", "PUT", "DELETE", "PATCH"))
                assertEquals(405, browser.send(HttpRequest.newBuilder(URI.create(base() + file)).header("X-CSRF-TOKEN", csrf(browser)).method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            for (String old : List.of("/download", "/download.view")) assertEquals(404, get(browser, old + "?id=" + song.getId()).statusCode());
            assertTrue(security.getUserByName(owner.getUsername()).getBytesDownloaded() > before);
            var page = get(browser, "/main.view?id=" + album.getId()); assertEquals(page.body(), 200, page.statusCode());
            assertTrue(page.body().contains("/airsonic/rest/downloadMedia")); assertFalse(page.body().contains("download.view"));
            playlists.deletePlaylist(playlist.getId());
        } finally {
            ScanningTestUtils.after(cleanup, folders); folders.expunge();
            try (var walk = java.nio.file.Files.walk(temp)) { walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); }
        }
    }
    private void assertFile(HttpResponse<byte[]> response, byte[] expected) {
        assertEquals(200, response.statusCode()); assertArrayEquals(expected, response.body());
        assertEquals(expected.length, response.headers().firstValueAsLong("Content-Length").orElse(-1));
        assertTrue(response.headers().firstValue("Content-Disposition").orElse("").startsWith("attachment;"));
    }
    private void assertZip(HttpResponse<byte[]> response, java.util.Set<String> names, byte[] expected) throws Exception {
        assertEquals(200, response.statusCode()); assertEquals(response.body().length, response.headers().firstValueAsLong("Content-Length").orElse(-1));
        var actual = new java.util.HashSet<String>();
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(response.body()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) { actual.add(entry.getName()); assertArrayEquals(expected, zip.readAllBytes()); }
        }
        assertEquals(names, actual);
    }
    private void assertError(HttpResponse<String> response, int code) throws Exception {
        assertEquals(response.body(), 200, response.statusCode()); var body = mapper.readTree(response.body()).path("subsonic-response");
        assertEquals(response.body(), "failed", body.path("status").asText()); assertEquals(response.body(), code, body.path("error").path("code").asInt());
        assertFalse(response.headers().firstValue("Content-Disposition").isPresent());
    }
    private HttpResponse<byte[]> bytes(HttpClient browser, String path, String method, String header, String value) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + path)).timeout(java.time.Duration.ofSeconds(30)).method(method, HttpRequest.BodyPublishers.noBody());
        if (header != null) request.header(header, value);
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
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
