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
import org.airsonic.player.util.StringUtil;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipInputStream;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class DownloadIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void preservesDownloadContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true).get(0);
            byte[] expected = Files.readAllBytes(song.getFullPath(library.get(0).getPath()));
            User denied = new User("download-denied", null);
            denied.setRoles(Set.of(User.Role.STREAM));
            security.createUser(denied, "secret", "test");
            User restricted = new User("download-restricted", null);
            restricted.setRoles(Set.of(User.Role.STREAM, User.Role.DOWNLOAD));
            security.createUser(restricted, "secret", "test");
            folders.setMusicFoldersForUser(restricted.getUsername(), Set.of(library.get(1).getId()));
            for (String suffix : List.of("", ".view")) {
                String query = "id=" + song.getId();
                HttpResponse<byte[]> response = send(suffix, query, "admin", "admin", null, null, false);
                assertEquals(200, response.statusCode());
                assertArrayEquals(expected, response.body());
                assertTrue(response.headers().firstValue("content-disposition").orElse("").startsWith("attachment;"));
                assertEquals(expected.length, response.headers().firstValueAsLong("content-length").orElse(-1));
                String etag = response.headers().firstValue("etag").orElseThrow();
                assertEquals(304, send(suffix, query, "admin", "admin", "If-None-Match", etag, false).statusCode());
                HttpResponse<byte[]> range = send(suffix, query, "admin", "admin", "Range", "bytes=0-9", false);
                assertEquals(206, range.statusCode());
                assertArrayEquals(Arrays.copyOf(expected, 10), range.body());
                HttpResponse<byte[]> legacy = send(suffix, "id=" + StringUtil.utf8HexEncode(song.getFullPath(library.get(0).getPath()).toString()), "admin", "admin", null, null, false);
                assertArrayEquals(expected, legacy.body());
                HttpResponse<byte[]> zip = send(suffix, "id=" + album.getId(), "admin", "admin", null, null, false);
                assertEquals(200, zip.statusCode());
                assertTrue(zip.headers().firstValue("content-disposition").orElse("").contains(".zip"));
                try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip.body()))) {
                    assertNotNull(input.getNextEntry());
                }
                HttpResponse<byte[]> noRole = send(suffix, query + "&f=json", "download-denied", "secret", null, null, false);
                assertEquals(200, noRole.statusCode());
                assertEquals(50, mapper.readTree(noRole.body()).path("subsonic-response").path("error").path("code").asInt());
                HttpResponse<byte[]> forbidden = send(suffix, query + "&f=json", "download-restricted", "secret", null, null, false);
                if (suffix.isEmpty()) {
                    assertEquals(200, forbidden.statusCode());
                    assertEquals(0, mapper.readTree(forbidden.body()).path("subsonic-response").path("error").path("code").asInt());
                } else {
                    assertEquals(403, forbidden.statusCode());
                }
                assertEquals(400, send(suffix, query + "&playlist=bad", "admin", "admin", null, null, false).statusCode());
            }
            assertArrayEquals(expected, send(".view", "id=" + song.getId(), "admin", "admin", null, null, true).body());
            assertEquals(403, send("", "id=" + song.getId(), "admin", "admin", null, null, true).statusCode());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }
    private HttpResponse<byte[]> send(String suffix, String query, String user, String password, String header, String value, boolean post) throws Exception {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=download-test&u=" + user + "&p=" + password + "&" + query;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/download" + suffix + (post ? "" : "?" + params)));
        if (header != null) request.header(header, value);
        if (post) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(params));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
