package org.airsonic.player.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class HlsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void preservesHlsContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true).get(0);
            User denied = new User("hls-denied", null);
            denied.setRoles(Set.of(User.Role.DOWNLOAD));
            security.createUser(denied, "secret", "test");
            User restricted = new User("hls-restricted", null);
            restricted.setRoles(Set.of(User.Role.STREAM));
            security.createUser(restricted, "secret", "test");
            folders.setMusicFoldersForUser(restricted.getUsername(), Set.of(library.get(1).getId()));
            for (String suffix : List.of("", ".view")) {
                HttpResponse<String> response = send(suffix, "id=" + song.getId(), "admin", "admin", false);
                assertEquals(response.body(), 200, response.statusCode());
                assertTrue(response.body(), response.body().startsWith("#EXTM3U"));
                assertTrue(response.body(), response.body().contains("#EXTINF:"));
                assertTrue(response.body(), response.body().contains("segment.ts"));
                assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/x-mpegurl"));
                HttpResponse<String> variants = send(suffix, "id=" + song.getId() + "&maxBitRate=300&maxBitRate=600", "admin", "admin", false);
                assertTrue(variants.body(), variants.body().contains("#EXT-X-STREAM-INF:"));
                assertTrue(variants.body(), variants.body().contains("hls.m3u8"));
                assertEquals(50, error(send(suffix, "id=" + song.getId() + "&f=json", "hls-denied", "secret", false)));
                HttpResponse<String> forbidden = send(suffix, "id=" + song.getId() + "&f=json", "hls-restricted", "secret", false);
                if (suffix.isEmpty()) assertEquals(0, error(forbidden));
                else assertEquals(403, forbidden.statusCode());
                assertEquals(10, error(send(suffix, "f=json", "admin", "admin", false)));
                assertEquals(400, send(suffix, "id=bad", "admin", "admin", false).statusCode());
                assertEquals(404, send(suffix, "id=" + album.getId(), "admin", "admin", false).statusCode());
            }
            assertEquals(200, send(".view", "id=" + song.getId(), "admin", "admin", true).statusCode());
            assertEquals(403, send("", "id=" + song.getId(), "admin", "admin", true).statusCode());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    private int error(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response").path("error").path("code").asInt();
    }

    private HttpResponse<String> send(String suffix, String query, String user, String password, boolean post) throws Exception {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=hls-test&u=" + user + "&p=" + password + "&" + query;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/hls" + suffix + (post ? "" : "?" + params)));
        if (post) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(params));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
