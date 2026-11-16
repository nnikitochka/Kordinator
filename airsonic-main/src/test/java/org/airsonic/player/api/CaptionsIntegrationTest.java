package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.airsonic.player.TestCaseUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class CaptionsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private ApplicationContext context;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao files;
    @Autowired private JWTSecurityService jwt;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test public void servesExternalAndEmbeddedCaptionsWithLegacyAndSignedLinks() throws Exception {
        assertFalse(context.containsBean("captionsController"));
        var directory = Files.createTempDirectory("captions-");
        var album = Files.createDirectories(directory.resolve("Album"));
        String subtitle = "1\n00:00:00,000 --> 00:00:01,000\nПривет, subtitles!\n";
        var srt = album.resolve("Русские субтитры.srt");
        Files.writeString(srt, "\ufeff" + subtitle);
        String vtt = "WEBVTT\n\n00:00.000 --> 00:01.000\nExternal VTT\n";
        Files.writeString(album.resolve("English.vtt"), vtt);
        var video = album.resolve("sample.mkv");
        Process generate = new ProcessBuilder("ffmpeg", "-v", "error", "-f", "lavfi", "-i", "color=c=black:s=160x120:r=10",
                "-i", srt.toString(), "-t", "2", "-c:v", "libx264", "-c:s", "srt", "-metadata:s:s:0", "language=rus",
                "-y", video.toString()).redirectErrorStream(true).start();
        String diagnostics = new String(generate.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(diagnostics, 0, generate.waitFor());
        Files.copy(video, album.resolve("other.mkv"));
        var folder = new MusicFolder(null, directory, "Captions", MusicFolder.Type.MEDIA, true, Instant.now());
        var cleanup = ScanningTestUtils.before(List.of(folder), folders, scanner);
        try {
            var file = files.getMediaFile("Album/sample.mkv", folder.getId());
            int id = file.getId();
            int otherId = files.getMediaFile("Album/other.mkv", folder.getId()).getId();
            String listUrl = rest("/rest/captions/list", id);
            var list = get(listUrl); assertEquals(list.body(), 200, list.statusCode());
            JsonNode tracks = json.readTree(list.body()); assertEquals(tracks.toPrettyString(), 3, tracks.size());
            assertEquals(200, get(rest("/captions/list", id)).statusCode());
            for (JsonNode track : tracks) {
                String url = track.path("url").asText();
                assertTrue(url, url.contains("/airsonic/rest/captions?"));
                var direct = get(auth(url)); assertEquals(direct.body(), 200, direct.statusCode());
                assertEquals("*", direct.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
                assertTrue(direct.headers().firstValue("Last-Modified").isPresent());
                if (track.path("format").asText().equals("vtt")) {
                    assertEquals(vtt, direct.body());
                    assertTrue(direct.headers().firstValue("Content-Type").orElse("").startsWith("text/vtt"));
                } else {
                    assertTrue(direct.body(), direct.body().contains("Привет, subtitles!"));
                    assertFalse(direct.body().startsWith("\ufeff"));
                    var converted = get(auth(url) + "&format=vtt");
                    assertEquals(converted.body(), 200, converted.statusCode());
                    assertTrue(converted.body(), converted.body().startsWith("WEBVTT"));
                    assertTrue(converted.body(), converted.body().contains("Привет, subtitles!"));
                }
                var head = client.send(HttpRequest.newBuilder(URI.create(auth(url))).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, head.statusCode()); assertEquals("", head.body());
                assertEquals(direct.headers().firstValue("Content-Type"), head.headers().firstValue("Content-Type"));
                var conditional = client.send(HttpRequest.newBuilder(URI.create(auth(url)))
                        .header("If-Modified-Since", direct.headers().firstValue("Last-Modified").orElseThrow())
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(304, conditional.statusCode()); assertEquals("", conditional.body());
                assertEquals(200, get(auth(url.replace("/rest/captions?", "/captions?"))).statusCode());
            }
            assertEquals(200, get(rest("/rest/captions", id)).statusCode());
            assertEquals(400, get(auth(base() + "/rest/captions?id=bad")).statusCode());
            assertEquals(404, get(rest("/rest/captions", id) + "&captionId=missing").statusCode());
            assertEquals(404, get(rest("/rest/captions", id) + "&format=invalid").statusCode());
            assertEquals(404, get(rest("/rest/captions", Integer.MAX_VALUE)).statusCode());
            assertEquals(404, get(rest("/rest/captions", id) + "&captionId=" + URLEncoder.encode("/etc/passwd", StandardCharsets.UTF_8)).statusCode());
            var post = client.send(HttpRequest.newBuilder(URI.create(rest("/rest/captions", id))).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertTrue(post.body(), post.statusCode() == 403 || post.statusCode() == 405);
            // Ordinary requests enforce folder permission; signed public links keep their existing access.
            folders.setMusicFoldersForUser("admin", Set.of());
            assertEquals(403, get(listUrl).statusCode());
            assertEquals(403, get(rest("/captions", id)).statusCode());
            folders.setMusicFoldersForUser("admin", Set.of(folder.getId()));
            Instant expiration = Instant.now().plusSeconds(300);
            for (String path : List.of("ext/rest/captions/list", "ext/captions/list")) {
                String signed = sign(path + "?id=" + id, expiration);
                var response = get(signed); assertEquals(response.body(), 200, response.statusCode());
                JsonNode publicTracks = json.readTree(response.body()); assertEquals(3, publicTracks.size());
                for (JsonNode track : publicTracks) {
                    String url = track.path("url").asText();
                    assertTrue(url, url.contains("/ext/rest/captions?"));
                    assertEquals(200, get(url).statusCode());
                    String token = queryParam(url, "jwt");
                    assertEquals(expiration.getEpochSecond(), jwt.verify(token).getExpiresAt().toInstant().getEpochSecond());
                    assertEquals(403, get(url.replace("id=" + id, "id=" + otherId)).statusCode());
                }
                assertTrue(get(signed.replace("jwt=", "jwt=bad")).statusCode() >= 300);
                assertTrue(get(sign(path + "?id=" + id, Instant.now().minusSeconds(60))).statusCode() >= 300);
            }
            assertTrue(get(base() + "/ext/captions/list?id=" + id).statusCode() >= 300);
        } finally {
            ScanningTestUtils.after(cleanup, folders); folders.expunge();
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private String base() { return "http://localhost:" + port + "/airsonic"; }
    private String auth(String url) { return url + (url.contains("?") ? "&" : "?") + "v=" + TestCaseUtils.restApiVersion() + "&c=captions-test&u=admin&p=admin"; }
    private String rest(String path, int id) { return auth(base() + path + "?id=" + id); }
    private String sign(String path, Instant expiration) {
        return base() + "/" + jwt.addJWTToken(User.USERNAME_GUEST, path, expiration);
    }
    private HttpResponse<String> get(String url) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url));
        if (url.contains("u=admin")) request.header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8)));
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String queryParam(String url, String name) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0) return null;
        for (String pair : url.substring(queryStart + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
