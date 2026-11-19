package org.airsonic.player.api;

import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.PlayerService;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ExternalStreamIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private JWTSecurityService jwt;
    @Autowired private PlayerService players;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void signedShareStreamsRemainAvailableWithoutWebsiteAliases() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true).get(0);
            byte[] expected = java.nio.file.Files.readAllBytes(song.getFullPath(library.get(0).getPath()));
            String signed = "/" + jwt.addJWTToken("admin", "ext/stream?format=raw&id=" + song.getId());
            HttpResponse<byte[]> full = send(signed, null, null);
            assertEquals(200, full.statusCode());
            assertArrayEquals(expected, full.body());
            HttpResponse<byte[]> partial = send(signed, "Range", "bytes=3-12");
            assertEquals(206, partial.statusCode());
            assertArrayEquals(Arrays.copyOfRange(expected, 3, 13), partial.body());

            Player player = new Player();
            player.setUsername("admin");
            players.createPlayer(player);
            player.getPlayQueue().addFiles(false, List.of(song));
            String live = "/" + jwt.addJWTToken("admin", "ext/stream?player=" + player.getId() + "&suffix=.mp3");
            HttpResponse<byte[]> liveResponse = send(live, null, null);
            assertEquals(200, liveResponse.statusCode());
            assertArrayEquals(Arrays.copyOf(expected, 10), Arrays.copyOf(liveResponse.body(), 10));

            assertEquals(404, send("/stream?format=raw&id=" + song.getId(), null, null).statusCode());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    private HttpResponse<byte[]> send(String path, String header, String value) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path));
        if (header != null) request.header(header, value);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
