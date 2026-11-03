package org.airsonic.player.api;

import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.controller.HLSController;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.util.HomeRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class HlsPlaybackIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @MockitoSpyBean private HLSController mvc;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void playsSignedVariantAndSegmentWithoutClientCredentials() throws Exception {
        Assume.assumeTrue("FFmpeg and FFprobe are required", available("ffmpeg") && available("ffprobe"));
        Path directory = Files.createTempDirectory("hls-video-");
        Path video = directory.resolve("sample.mp4");
        Process generate = new ProcessBuilder("ffmpeg", "-v", "error", "-f", "lavfi", "-i", "color=c=black:s=160x120:r=10",
                "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100", "-t", "12", "-c:v", "libx264",
                "-pix_fmt", "yuv420p", "-c:a", "aac", "-y", video.toString()).redirectErrorStream(true).start();
        String diagnostics = new String(generate.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(diagnostics, 0, generate.waitFor());
        MusicFolder folder = new MusicFolder(null, directory, "HLS test", MusicFolder.Type.MEDIA, true, Instant.now());
        UUID cleanup = ScanningTestUtils.before(List.of(folder), folders, scanner);
        try {
            MediaFile media = mediaFiles.getMediaFile("sample.mp4", folder.getId());
            assertNotNull(media);
            String url = "http://localhost:" + port + "/airsonic/rest/hls.view?v=" + TestCaseUtils.restApiVersion()
                    + "&c=video-test&u=admin&p=admin&id=" + media.getId() + "&maxBitRate=300&maxBitRate=500&size=160x120";
            HttpResponse<String> master = getText(url);
            assertEquals(master.body(), 200, master.statusCode());
            String variantUrl = firstUrl(master.body());
            assertFalse(variantUrl.contains("p=admin"));
            assertTrue(variantUrl.contains("jwt="));
            HttpResponse<String> variant = getText(variantUrl);
            assertEquals(variant.body(), 200, variant.statusCode());
            String segmentUrl = firstUrl(variant.body());
            assertTrue(segmentUrl.contains("duration=10"));
            assertTrue(segmentUrl.contains("jwt="));
            HttpResponse<byte[]> segment = client.send(HttpRequest.newBuilder(URI.create(segmentUrl)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(new String(segment.body(), StandardCharsets.UTF_8), 200, segment.statusCode());
            assertTrue(segment.body().length > 188);
            assertEquals(0x47, segment.body()[0] & 0xff);
            Path segmentFile = directory.resolve("result.ts");
            Files.write(segmentFile, segment.body());
            Process probe = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "stream=codec_type",
                    "-of", "csv=p=0", segmentFile.toString()).redirectErrorStream(true).start();
            String streams = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(streams, 0, probe.waitFor());
            assertTrue(streams, streams.contains("video"));
            assertTrue(streams, streams.contains("audio"));
            String lastUrl = variant.body().lines().filter(line -> line.startsWith("http")).skip(1).findFirst().orElseThrow();
            assertTrue(lastUrl, lastUrl.contains("segmentIndex=1") && lastUrl.contains("duration=10"));
            HttpResponse<byte[]> last = client.send(HttpRequest.newBuilder(URI.create(lastUrl)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, last.statusCode());
            Path lastFile = directory.resolve("last.ts");
            Files.write(lastFile, last.body());
            Process lastProbe = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", lastFile.toString()).redirectErrorStream(true).start();
            String lastDuration = new String(lastProbe.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            assertEquals(lastDuration, 0, lastProbe.waitFor());
            assertTrue(lastDuration, Double.parseDouble(lastDuration) < 4.0);
            HttpResponse<Void> head = client.send(HttpRequest.newBuilder(URI.create(segmentUrl))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(200, head.statusCode());
            assertEquals(403, getText(segmentUrl.replace("segmentIndex=0", "segmentIndex=1")).statusCode());
            assertEquals(403, getText(segmentUrl.replace("size=160x120", "size=320x240")).statusCode());
            assertNotEquals(200, getText(segmentUrl.replaceFirst("&jwt=.*", "")).statusCode());
            verify(mvc, never()).handleSegmentRequest(any(), anyInt(), anyInt(), anyString(), anyInt(), anyString(), any(), any(), any());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private boolean available(String command) {
        try {
            Process process = new ProcessBuilder(command, "-version").redirectErrorStream(true).start();
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private HttpResponse<String> getText(String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private String firstUrl(String playlist) {
        return playlist.lines().filter(line -> line.startsWith("http")).findFirst().orElseThrow();
    }
}
