package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.dao.ShareDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Share;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.*;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PublicShareIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private ApplicationContext context;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao files;
    @Autowired private ShareDao shareDao;
    @Autowired private ShareService shares;
    @Autowired private JWTSecurityService jwt;
    @Autowired private PlayerService players;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test public void loadsPublicSharesThroughSignedRestAndStreamsWithoutLogin() throws Exception {
        assertFalse(context.containsBean("externalPlayerController"));
        var directory = Files.createTempDirectory("public-share-");
        var albumDir = Files.createDirectories(directory.resolve("Artist/Album"));
        var original = MusicFolderTestData.resolveMusicFolderPath().resolve("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes/Sixteen Horsepower - 01 I Seen What I Saw.mp3");
        Files.copy(original, albumDir.resolve("One.mp3"));
        Files.copy(original, albumDir.resolve("Two.mp3"));
        var folder = new MusicFolder(null, directory, "Public shares", MusicFolder.Type.MEDIA, true, Instant.now());
        UUID cleanup = ScanningTestUtils.before(List.of(folder), folders, scanner);
        try {
            var player = players.getGuestPlayer(null);
            folders.setMusicFoldersForUser(User.USERNAME_GUEST, Set.of(folder.getId()));
            MediaFile album = files.getMediaFile("Artist/Album", folder.getId());
            MediaFile song = files.getMediaFile("Artist/Album/One.mp3", folder.getId());
            Share share = create("PublicTest", List.of(album), "<script>alert('x')</script> Кириллица");
            String pageUrl = signed("ext/share/" + share.getName(), Instant.now().plusSeconds(600));
            var page = get(pageUrl);
            assertEquals(page.body(), 200, page.statusCode());
            assertTrue(page.body(), page.body().contains("/airsonic/script/public-share.js?v=1"));
            assertFalse(page.body(), page.body().contains(share.getDescription()));
            assertTrue(page.body(), page.body().contains("property=\"og:image\" content=\"http://localhost:"));
            assertTrue(page.body(), page.body().contains("property=\"og:title\""));
            assertEquals(0, shares.getShareById(share.getId()).getVisitCount());
            var match = Pattern.compile("id=\"publicShareData\" data-url=\"([^\"]+)\"").matcher(page.body());
            assertTrue(page.body(), match.find());
            String dataUrl = match.group(1).replace("&amp;", "&");
            assertTrue(dataUrl, dataUrl.contains("/airsonic/ext/rest/share/PublicTest?jwt="));
            var head = client.send(HttpRequest.newBuilder(URI.create(dataUrl)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, head.statusCode()); assertEquals("", head.body());
            assertEquals(0, shares.getShareById(share.getId()).getVisitCount());
            var response = get(dataUrl);
            assertEquals(response.body(), 200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/json"));
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
            JsonNode data = json.readTree(response.body());
            assertTrue(data.path("available").asBoolean());
            assertFalse(data.path("videoPresent").asBoolean());
            assertEquals(share.getDescription(), data.path("description").asText());
            assertEquals(2, data.path("media").size());
            assertEquals(song.getId().intValue(), data.path("media").get(0).path("id").asInt());
            assertEquals(1, shares.getShareById(share.getId()).getVisitCount());
            assertNotNull(shares.getShareById(share.getId()).getLastVisited());
            var media = data.path("media").get(0);
            assertTrue(media.path("streamable").asBoolean());
            String stream = media.path("streamUrl").asText();
            assertTrue(stream, stream.contains("/airsonic/ext/stream?id="));
            var bytes = client.send(HttpRequest.newBuilder(URI.create(stream)).header("Range", "bytes=0-9").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(206, bytes.statusCode());
            assertArrayEquals(java.util.Arrays.copyOf(Files.readAllBytes(original), 10), bytes.body());
            for (String field : List.of("streamUrl", "captionsUrl", "coverArtUrl")) {
                String token = UriComponentsBuilder.fromUriString(media.path(field).asText()).build().getQueryParams().getFirst("jwt");
                var decoded = jwt.verify(token);
                assertEquals(User.USERNAME_GUEST, decoded.getSubject());
                assertTrue(decoded.getExpiresAt().toInstant().isBefore(Instant.now().plusSeconds(601)));
                assertFalse(media.path(field).asText().contains("admin"));
            }
            assertEquals(200, get(media.path("captionsUrl").asText()).statusCode());
            assertTrue(get(dataUrl.substring(0, dataUrl.indexOf('?'))).statusCode() >= 300);
            assertTrue(get(dataUrl.replace("PublicTest", "OtherShare")).statusCode() >= 300);
            assertTrue(get(signed("ext/rest/share/PublicTest", Instant.now().minusSeconds(60))).statusCode() >= 300);
            assertTrue(get(dataUrl.substring(0, dataUrl.indexOf("jwt=")) + "jwt=bad").statusCode() >= 300);
            assertTrue(get(dataUrl.replace("/ext/rest/share/", "/ext/share/")).statusCode() >= 300);
            assertFalse(json.readTree(get(signed("ext/rest/share/Missing", Instant.now().plusSeconds(60))).body()).path("available").asBoolean());
            share = shares.getShareById(share.getId());
            share.setExpires(Instant.now().minusSeconds(1)); shares.updateShare(share);
            JsonNode expired = json.readTree(get(dataUrl).body());
            assertFalse(expired.path("available").asBoolean()); assertEquals(0, expired.path("media").size());
            assertEquals(1, shares.getShareById(share.getId()).getVisitCount());
            share.setExpires(Instant.now().plusSeconds(60)); shares.updateShare(share);
            JsonNode renewed = json.readTree(get(dataUrl).body());
            String freshToken = UriComponentsBuilder.fromUriString(renewed.path("media").get(0).path("streamUrl").asText()).build().getQueryParams().getFirst("jwt");
            assertTrue(jwt.verify(freshToken).getExpiresAt().toInstant().isBefore(Instant.now().plusSeconds(61)));
            Files.delete(albumDir.resolve("Two.mp3"));
            assertEquals(1, json.readTree(get(dataUrl).body()).path("media").size());
            folders.setMusicFoldersForUser(User.USERNAME_GUEST, Set.of());
            assertFalse(json.readTree(get(dataUrl).body()).path("available").asBoolean());
            shares.deleteShare(share.getId());
            assertFalse(json.readTree(get(dataUrl).body()).path("available").asBoolean());
        } finally {
            ScanningTestUtils.after(cleanup, folders); folders.expunge();
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    @Test public void publicVideoSelectsDirectOrHlsPlayback() throws Exception {
        var directory = Files.createTempDirectory("public-video-");
        var video = directory.resolve("sample.mkv");
        Process generate = new ProcessBuilder("ffmpeg", "-v", "error", "-f", "lavfi", "-i", "color=c=black:s=160x120:r=10",
                "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100", "-t", "2", "-c:v", "libx264",
                "-pix_fmt", "yuv420p", "-c:a", "aac", "-y", video.toString()).redirectErrorStream(true).start();
        String diagnostics = new String(generate.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(diagnostics, 0, generate.waitFor());
        Process remux = new ProcessBuilder("ffmpeg", "-v", "error", "-i", video.toString(), "-c", "copy", "-y",
                directory.resolve("sample.mp4").toString()).redirectErrorStream(true).start();
        diagnostics = new String(remux.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(diagnostics, 0, remux.waitFor());
        var folder = new MusicFolder(null, directory, "Public video", MusicFolder.Type.MEDIA, true, Instant.now());
        UUID cleanup = ScanningTestUtils.before(List.of(folder), folders, scanner);
        try {
            players.getGuestPlayer(null);
            folders.setMusicFoldersForUser(User.USERNAME_GUEST, Set.of(folder.getId()));
            var mkv = files.getMediaFile("sample.mkv", folder.getId());
            var mp4 = files.getMediaFile("sample.mp4", folder.getId());
            var share = create("PublicVideo", List.of(mkv, mp4), "Videos");
            var data = json.readTree(get(signed("ext/rest/share/" + share.getName(), Instant.now().plusSeconds(600))).body());
            assertTrue(data.toPrettyString(), data.path("available").asBoolean());
            assertTrue(data.path("videoPresent").asBoolean());
            assertEquals(2, data.path("media").size());
            for (JsonNode media : data.path("media")) {
                assertTrue(media.path("video").asBoolean());
                String stream = media.path("streamUrl").asText();
                if (media.path("id").asInt() == mkv.getId()) {
                    assertFalse(media.path("streamable").asBoolean());
                    assertEquals("application/x-mpegurl", media.path("contentType").asText());
                    assertTrue(stream, stream.contains("/ext/hls/hls.m3u8"));
                    var master = get(stream); assertEquals(master.body(), 200, master.statusCode());
                    String variant = master.body().lines().filter(line -> line.startsWith("http")).findFirst().orElseThrow();
                    var playlist = get(variant); assertEquals(playlist.body(), 200, playlist.statusCode());
                    String segment = playlist.body().lines().filter(line -> line.startsWith("http")).findFirst().orElseThrow();
                    var bytes = client.send(HttpRequest.newBuilder(URI.create(segment)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                    assertEquals(200, bytes.statusCode()); assertTrue(bytes.body().length > 188); assertEquals(0x47, bytes.body()[0] & 0xff);
                } else {
                    assertTrue(media.path("streamable").asBoolean());
                    assertTrue(stream, stream.contains("/ext/stream?"));
                    assertEquals(200, get(stream).statusCode());
                }
            }
            shares.deleteShare(share.getId());
        } finally {
            ScanningTestUtils.after(cleanup, folders); folders.expunge();
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private Share create(String name, List<MediaFile> media, String description) {
        Share share = new Share(); share.setName(name); share.setUsername("admin");
        share.setDescription(description); share.setCreated(Instant.now()); share.setExpires(Instant.now().plusSeconds(3600));
        shareDao.createShare(share); shareDao.createSharedFiles(share.getId(), media.stream().map(MediaFile::getId).toList());
        return share;
    }

    private String signed(String path, Instant expiration) {
        return "http://localhost:" + port + "/airsonic/" + jwt.addJWTToken(User.USERNAME_ANONYMOUS,
                UriComponentsBuilder.fromPath(path), expiration).toUriString();
    }

    private HttpResponse<String> get(String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
