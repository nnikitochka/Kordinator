package org.airsonic.player.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.springframework.context.ApplicationContext;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class StreamIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    @Autowired private ApplicationContext context;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void preservesStreamContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true).get(0);
            byte[] expected = Files.readAllBytes(song.getFullPath(library.get(0).getPath()));
            User denied = new User("stream-denied", null);
            denied.setRoles(Set.of(User.Role.DOWNLOAD));
            security.createUser(denied, "secret", "test");
            User restricted = new User("stream-restricted", null);
            restricted.setRoles(Set.of(User.Role.STREAM));
            security.createUser(restricted, "secret", "test");
            folders.setMusicFoldersForUser(restricted.getUsername(), Set.of(library.get(1).getId()));
            for (String suffix : List.of("", ".view")) {
                String query = "id=" + song.getId() + "&format=raw";
                HttpResponse<byte[]> full = send(suffix, query, "admin", "admin", null, null);
                assertEquals(200, full.statusCode());
                assertArrayEquals(expected, full.body());
                assertEquals(expected.length, full.headers().firstValueAsLong("content-length").orElse(-1));
                assertTrue(full.headers().firstValue("last-modified").isPresent());
                HttpResponse<byte[]> range = send(suffix, query, "admin", "admin", "Range", "bytes=0-9");
                assertEquals(206, range.statusCode());
                assertArrayEquals(Arrays.copyOf(expected, 10), range.body());
                assertEquals("bytes 0-9/" + expected.length, range.headers().firstValue("content-range").orElse(""));
                if (song.getDuration() != null && song.getDuration() > 2) {
                    int offset = (int) Math.round(expected.length / song.getDuration());
                    HttpResponse<byte[]> shifted = send(suffix, query + "&offsetSeconds=1", "admin", "admin", null, null);
                    assertEquals(200, shifted.statusCode());
                    assertArrayEquals(Arrays.copyOfRange(expected, offset, expected.length), shifted.body());
                    HttpResponse<byte[]> shiftedRange = send(suffix, query + "&offsetSeconds=1", "admin", "admin", "Range", "bytes=0-9");
                    assertEquals(206, shiftedRange.statusCode());
                    assertArrayEquals(Arrays.copyOfRange(expected, offset, offset + 10), shiftedRange.body());
                }
                HttpResponse<byte[]> suffixRange = send(suffix, query, "admin", "admin", "Range", "bytes=-10");
                assertEquals(206, suffixRange.statusCode());
                assertArrayEquals(Arrays.copyOfRange(expected, expected.length - 10, expected.length), suffixRange.body());
                assertEquals(416, send(suffix, query, "admin", "admin", "Range", "bytes=999999999-").statusCode());
                for (String invalid : List.of("bytes=-0", "bytes=8-2", "bytes=bad", "items=0-2", "bytes=0-999999999,0-2")) {
                    HttpResponse<byte[]> rejected = send(suffix, query, "admin", "admin", "Range", invalid);
                    assertEquals(invalid, 416, rejected.statusCode());
                    assertEquals("bytes */" + expected.length, rejected.headers().firstValue("content-range").orElse(""));
                }
                HttpResponse<byte[]> openEnded = send(suffix, query, "admin", "admin", "Range", "bytes=10-");
                assertEquals(206, openEnded.statusCode());
                assertArrayEquals(Arrays.copyOfRange(expected, 10, expected.length), openEnded.body());
                HttpResponse<byte[]> capped = send(suffix, query, "admin", "admin", "Range", "bytes=0-999999999");
                assertEquals(206, capped.statusCode());
                assertArrayEquals(expected, capped.body());
                assertEquals(200, send(suffix, query, "admin", "admin", "Range", "bytes=").statusCode());
                HttpResponse<byte[]> multiRange = send(suffix, query, "admin", "admin", "Range", "bytes=0-2,10-12");
                assertEquals(206, multiRange.statusCode());
                assertTrue(multiRange.headers().firstValue("content-type").orElse("").startsWith("multipart/byteranges"));
                String multipart = new String(multiRange.body(), java.nio.charset.StandardCharsets.ISO_8859_1);
                assertTrue(multipart.contains("Content-Range: bytes 0-2/" + expected.length));
                assertTrue(multipart.contains("Content-Range: bytes 10-12/" + expected.length));
                assertTrue(multipart.contains(new String(Arrays.copyOfRange(expected, 0, 3), java.nio.charset.StandardCharsets.ISO_8859_1)));
                assertTrue(multipart.contains(new String(Arrays.copyOfRange(expected, 10, 13), java.nio.charset.StandardCharsets.ISO_8859_1)));
                HttpResponse<byte[]> icy = send(suffix, query, "admin", "admin", "icy-metadata", "1");
                assertEquals(200, icy.statusCode());
                assertTrue(icy.headers().firstValue("icy-metaint").isPresent());
                assertEquals(304, send(suffix, query, "admin", "admin", "If-None-Match", full.headers().firstValue("etag").orElseThrow()).statusCode());
                assertEquals(304, send(suffix, query, "admin", "admin", "If-Modified-Since", full.headers().firstValue("last-modified").orElseThrow()).statusCode());
                assertEquals(200, send(suffix, query, "admin", "admin", "If-Match", "\"wrong\"").statusCode());
                HttpResponse<byte[]> noRole = send(suffix, query + "&f=json", "stream-denied", "secret", null, null);
                assertEquals(50, new ObjectMapper().readTree(noRole.body()).path("subsonic-response").path("error").path("code").asInt());
                HttpResponse<byte[]> forbidden = send(suffix, query + "&f=json", "stream-restricted", "secret", null, null);
                if (suffix.isEmpty()) {
                    assertEquals(200, forbidden.statusCode());
                    assertEquals(0, new ObjectMapper().readTree(forbidden.body()).path("subsonic-response").path("error").path("code").asInt());
                } else assertEquals(403, forbidden.statusCode());
                assertEquals(400, send(suffix, "id=bad", "admin", "admin", null, null).statusCode());
                assertEquals(400, send(suffix, query + "&maxBitRate=bad", "admin", "admin", null, null).statusCode());
                assertEquals(400, send(suffix, query + "&offsetSeconds=bad", "admin", "admin", null, null).statusCode());
                String params = "v=" + TestCaseUtils.restApiVersion() + "&c=stream-test&u=admin&p=admin&" + query;
                HttpRequest head = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/stream" + suffix + "?" + params))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<Void> headResponse = client.send(head, HttpResponse.BodyHandlers.discarding());
                assertEquals(200, headResponse.statusCode());
                assertEquals(expected.length, headResponse.headers().firstValueAsLong("content-length").orElse(-1));
                HttpRequest post = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/stream" + suffix))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(params)).build();
                HttpResponse<byte[]> postResponse = client.send(post, HttpResponse.BodyHandlers.ofByteArray());
                if (suffix.isEmpty()) assertEquals(403, postResponse.statusCode());
                else {
                    assertArrayEquals(expected, postResponse.body());
                    HttpRequest precondition = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/stream" + suffix))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("If-Match", "\"wrong\"")
                            .POST(HttpRequest.BodyPublishers.ofString(params)).build();
                    assertEquals(412, client.send(precondition, HttpResponse.BodyHandlers.ofByteArray()).statusCode());
                }
            }
            if (commandAvailable("ffmpeg") && commandAvailable("ffprobe") && song.getBitRate() != null && song.getBitRate() > 32) {
                HttpResponse<byte[]> transcoded = send(".view", "id=" + song.getId() + "&format=mp3&maxBitRate=32", "admin", "admin", null, null);
                assertEquals(200, transcoded.statusCode());
                assertTrue(transcoded.body().length > 0);
                assertFalse("Expected downsampled audio", Arrays.equals(expected, transcoded.body()));
                java.nio.file.Path audio = Files.createTempFile("stream-transcoded-", ".mp3");
                try {
                    Files.write(audio, transcoded.body());
                    Process probe = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "stream=codec_name",
                            "-of", "default=noprint_wrappers=1:nokey=1", audio.toString()).redirectErrorStream(true).start();
                    String codec = new String(probe.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    assertEquals(codec, 0, probe.waitFor());
                    assertTrue(codec, codec.contains("mp3"));
                } finally {
                    Files.deleteIfExists(audio);
                }
            }
            assertFalse(context.containsBean("streamController"));
            String basic = java.util.Base64.getEncoder().encodeToString("admin:admin".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            HttpRequest website = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                    + "/airsonic/stream?format=raw&id=" + song.getId())).header("Authorization", "Basic " + basic).GET().build();
            HttpResponse<byte[]> websiteResponse = client.send(website, HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, websiteResponse.statusCode());
            assertArrayEquals(expected, websiteResponse.body());

        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    private boolean commandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "-version").redirectErrorStream(true).start();
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private HttpResponse<byte[]> send(String suffix, String query, String user, String password, String header, String value) throws Exception {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=stream-test&u=" + user + "&p=" + password + "&" + query;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/stream" + suffix + "?" + params));
        if (header != null) request.header(header, value);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
