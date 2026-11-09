package org.airsonic.player.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.dao.BookmarkDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.*;
import org.airsonic.player.service.*;
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
import java.nio.file.Files;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class VideoPlayerIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private ApplicationContext context;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao files;
    @Autowired private BookmarkDao bookmarks;
    @Autowired private SettingsService settings;
    @Autowired private SecurityService security;
    @Autowired private PlayerService players;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test public void videoPageLoadsRestDataAndPreservesPlaybackBookmarksAndAccess() throws Exception {
        assertFalse(context.containsBean("videoPlayerController"));
        var directory = Files.createTempDirectory("video-player-");
        var album = Files.createDirectories(directory.resolve("Album"));
        Files.writeString(album.resolve("Русские субтитры.srt"), "1\n00:00:00,000 --> 00:00:01,000\nПривет!\n");
        var mkvPath = album.resolve("sample.mkv");
        generate("ffmpeg", "-v", "error", "-f", "lavfi", "-i", "color=c=black:s=160x120:r=10", "-f", "lavfi", "-i",
                "sine=frequency=440:sample_rate=44100", "-t", "2", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-y", mkvPath.toString());
        generate("ffmpeg", "-v", "error", "-i", mkvPath.toString(), "-c", "copy", "-metadata", "title=Видео <script> & \"тест\"", "-y", album.resolve("sample.mp4").toString());
        var folder = new MusicFolder(null, directory, "Video player", MusicFolder.Type.MEDIA, true, Instant.now());
        var cleanup = ScanningTestUtils.before(List.of(folder), folders, scanner);
        try {
            var mp4 = files.getMediaFile("Album/sample.mp4", folder.getId());
            var mkv = files.getMediaFile("Album/sample.mkv", folder.getId());
            int id = mp4.getId();
            var preferences = settings.getUserSettings("admin");
            preferences.setAutoBookmark(true); preferences.setVideoBookmarkFrequency(5); settings.updateUserSettings(preferences);
            var now = Instant.now();
            bookmarks.createOrUpdateBookmark(new Bookmark(0, id, 750, "admin", "Resume", now, now));
            files.starMediaFile(id, "admin");
            var player = new Player(); player.setUsername("admin"); players.createPlayer(player);
            var response = get("/rest/videoPlayer?id=" + id + "&player=" + player.getId(), "admin:admin");
            assertEquals(response.body(), 200, response.statusCode());
            assertTrue(response.headers().allValues("Cache-Control").toString().contains("no-store"));
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
            var data = json.readTree(response.body());
            assertEquals(id, data.path("videoId").asInt());
            assertTrue(data.path("videoTitle").asText(), data.path("videoTitle").asText().contains("<script>"));
            assertTrue(data.path("streamable").asBoolean()); assertTrue(data.path("castable").asBoolean());
            assertTrue(data.path("starred").asBoolean()); assertTrue(data.path("canShare").asBoolean()); assertTrue(data.path("canDownload").asBoolean());
            assertFalse(data.path("csrfToken").asText().isEmpty());
            assertEquals("X-CSRF-TOKEN", data.path("csrfHeader").asText());
            String cookie = response.headers().allValues("Set-Cookie").stream().filter(value -> value.startsWith("JSESSIONID="))
                    .map(value -> value.split(";", 2)[0]).findFirst().orElseThrow();
            var mutation = client.send(auth(HttpRequest.newBuilder(URI.create(base() + "/rest/unstar")), "admin:admin")
                    .header("Cookie", cookie).header(data.path("csrfHeader").asText(), data.path("csrfToken").asText())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("f=json&c=video-test&id=" + id)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(mutation.body(), 200, mutation.statusCode());
            assertEquals("ok", json.readTree(mutation.body()).path("subsonic-response").path("status").asText());
            assertEquals(750, data.path("position").asLong()); assertTrue(data.path("autoBookmark").asBoolean()); assertEquals(5, data.path("videoBookmarkFrequency").asInt());
            assertEquals("Original", data.path("defaultBitRate").asText()); assertEquals(12, data.path("streamUrls").size());
            assertFalse(data.path("streamUrls").has("remoteStreamUrl")); assertEquals("video/mp4", data.path("contentType").asText());
            String stream = data.path("streamUrls").path("Original").asText();
            assertTrue(stream, stream.contains("/airsonic/stream?id=" + id + "&player=" + player.getId()));
            var bytes = client.send(auth(HttpRequest.newBuilder(URI.create(stream)).header("Range", "bytes=0-9"), "admin:admin").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(206, bytes.statusCode()); assertEquals(10, bytes.body().length);
            assertEquals(200, get(data.path("remoteStreamUrl").asText(), null).statusCode());
            assertEquals(1, data.path("captions").size());
            assertTrue(get(data.path("captions").get(0).path("url").asText() + "&format=vtt", "admin:admin").body().startsWith("WEBVTT"));
            assertEquals(200, get(data.path("remoteCaptionsListUrl").asText(), null).statusCode());
            assertEquals(0, json.readTree(get("/rest/videoPlayer?id=" + id + "&position=0", "admin:admin").body()).path("position").asLong());
            assertEquals(750, bookmarks.getBookmark("admin", id).getPositionMillis());
            var head = client.send(auth(HttpRequest.newBuilder(URI.create(base() + "/rest/videoPlayer?id=" + id)), "admin:admin")
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, head.statusCode()); assertEquals("", head.body());
            for (String page : List.of("/videoPlayer", "/videoPlayer.view")) {
                var shell = get(page + "?id=" + id, "admin:admin"); assertEquals(shell.body(), 200, shell.statusCode());
                assertTrue(shell.body(), shell.body().contains("/airsonic/script/video-player.js?v=1"));
                assertTrue(shell.body(), shell.body().contains("data-url=\"/airsonic/rest/videoPlayer\""));
                assertFalse(shell.body().contains(data.path("videoTitle").asText()));
            }
            var fallback = json.readTree(get("/rest/videoPlayer?id=" + mkv.getId(), "admin:admin").body());
            assertFalse(fallback.path("streamable").asBoolean()); assertTrue(fallback.path("castable").asBoolean());
            assertEquals("1500 Kbps", fallback.path("defaultBitRate").asText()); assertEquals(11, fallback.path("streamUrls").size());
            var master = get(fallback.path("streamUrls").path("1500 Kbps").asText(), "admin:admin");
            assertEquals(master.body(), 200, master.statusCode());
            assertTrue(master.body(), master.body().contains("#EXT-X-TARGETDURATION"));
            String segment = master.body().lines().filter(line -> line.startsWith("http")).findFirst().orElseThrow();
            var ts = client.send(auth(HttpRequest.newBuilder(URI.create(segment)), "admin:admin").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, ts.statusCode()); assertTrue(ts.body().length > 188); assertEquals(0x47, ts.body()[0] & 0xff);
            for (String query : List.of("", "id=bad", "id=" + id + "&position=bad", "id=" + id + "&position=-1", "id=" + id + "&player=bad"))
                assertEquals(query, 400, get("/rest/videoPlayer?" + query, "admin:admin").statusCode());
            assertEquals(404, get("/rest/videoPlayer?id=" + Integer.MAX_VALUE, "admin:admin").statusCode());
            assertEquals(404, get("/rest/videoPlayer?id=" + files.getMediaFile("Album", folder.getId()).getId(), "admin:admin").statusCode());
            var reader = new User("video-reader", null); reader.setRoles(Set.of(User.Role.STREAM)); security.createUser(reader, "secret", "test");
            folders.setMusicFoldersForUser(reader.getUsername(), Set.of(folder.getId()));
            var readerData = json.readTree(get("/rest/videoPlayer?id=" + id, "video-reader:secret").body());
            assertEquals(0, readerData.path("position").asLong()); assertFalse(readerData.path("starred").asBoolean());
            assertFalse(readerData.path("canShare").asBoolean()); assertFalse(readerData.path("canDownload").asBoolean());
            assertEquals(200, get("/videoPlayer.view?id=" + id, "video-reader:secret").statusCode());
            assertEquals(403, get("/rest/videoPlayer?id=" + id + "&player=" + player.getId(), "video-reader:secret").statusCode());
            folders.setMusicFoldersForUser(reader.getUsername(), Set.of());
            assertEquals(403, get("/rest/videoPlayer?id=" + id, "video-reader:secret").statusCode());
            var denied = new User("video-denied", null); denied.setRoles(Set.of(User.Role.DOWNLOAD)); security.createUser(denied, "secret", "test");
            assertEquals(403, get("/rest/videoPlayer?id=" + id, "video-denied:secret").statusCode());
        } finally {
            ScanningTestUtils.after(cleanup, folders); folders.expunge();
            try (var paths = Files.walk(directory)) { for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
    private void generate(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(diagnostics, 0, process.waitFor());
    }
    private String base() { return "http://localhost:" + port + "/airsonic"; }
    private HttpRequest.Builder auth(HttpRequest.Builder builder, String credentials) {
        return credentials == null ? builder : builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
    }
    private HttpResponse<String> get(String url, String credentials) throws Exception {
        return client.send(auth(HttpRequest.newBuilder(URI.create(url.startsWith("http") ? url : base() + url)), credentials).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
