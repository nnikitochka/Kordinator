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
            for (String suffix : List.of("", ".view")) {
                String query = "id=" + song.getId() + "&format=raw";
                HttpResponse<byte[]> full = send(suffix, query, "admin", "admin", null, null);
                assertEquals(200, full.statusCode());
                assertArrayEquals(expected, full.body());
                HttpResponse<byte[]> range = send(suffix, query, "admin", "admin", "Range", "bytes=0-9");
                assertEquals(206, range.statusCode());
                assertArrayEquals(Arrays.copyOf(expected, 10), range.body());
                HttpResponse<byte[]> suffixRange = send(suffix, query, "admin", "admin", "Range", "bytes=-10");
                assertEquals(206, suffixRange.statusCode());
                assertArrayEquals(Arrays.copyOfRange(expected, expected.length - 10, expected.length), suffixRange.body());
                assertEquals(416, send(suffix, query, "admin", "admin", "Range", "bytes=999999999-").statusCode());
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
                HttpResponse<byte[]> noRole = send(suffix, query + "&f=json", "stream-denied", "secret", null, null);
                assertEquals(50, new ObjectMapper().readTree(noRole.body()).path("subsonic-response").path("error").path("code").asInt());
                assertEquals(400, send(suffix, "id=bad", "admin", "admin", null, null).statusCode());
            }
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    private HttpResponse<byte[]> send(String suffix, String query, String user, String password, String header, String value) throws Exception {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=stream-test&u=" + user + "&p=" + password + "&" + query;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/stream" + suffix + "?" + params));
        if (header != null) request.header(header, value);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
