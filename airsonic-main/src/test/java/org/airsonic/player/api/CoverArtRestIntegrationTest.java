package org.airsonic.player.api;

import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.PlaylistService;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.util.HomeRule;
import org.airsonic.player.util.MusicFolderTestData;
import org.airsonic.player.util.StringUtil;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class CoverArtRestIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao files;
    @Autowired private AlbumDao albums;
    @Autowired private ArtistDao artists;
    @Autowired private PlaylistService playlists;
    @Autowired private JWTSecurityService jwt;
    @Autowired private ApplicationContext context;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void servesOriginalResizedAndGeneratedImagesWithoutCallingSpringMvc() throws Exception {
        assertFalse(context.containsBean("coverArtController"));
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        Playlist playlist = new Playlist(0, "admin", false, "Test playlist", null, 0, 0, Instant.now(), Instant.now(), null);
        playlists.createPlaylist(playlist);
        try {
            String albumPath = "_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes";
            MediaFile dir = files.getMediaFile(albumPath, library.get(0).getId());
            assertNotNull(dir);
            byte[] original = Files.readAllBytes(library.get(0).getPath().resolve(albumPath + "/cover.jpg"));
            String legacyId = StringUtil.utf8HexEncode(dir.getFullPath(library.get(0).getPath()).toString());
            String auth = "v=" + TestCaseUtils.restApiVersion() + "&c=test&u=admin&p=admin";
            for (String suffix : new String[]{"", ".view"}) {
                String endpoint = "/rest/getCoverArt" + suffix + "?" + auth;
                HttpResponse<byte[]> full = request(endpoint + "&id=" + dir.getId(), "GET", null, false);
                assertEquals(200, full.statusCode());
                assertEquals("image/jpeg", full.headers().firstValue("content-type").orElseThrow());
                assertArrayEquals(original, full.body());
                assertArrayEquals(original, request(endpoint + "&id=" + legacyId, "GET", null, false).body());
                assertArrayEquals(original, request(endpoint + "&id=" + dir.getId() + "&offset=invalid", "GET", null, false).body());
                HttpResponse<byte[]> scaled = request(endpoint + "&id=" + dir.getId() + "&size=48", "GET", null, false);
                assertEquals(200, scaled.statusCode());
                assertEquals("image/jpeg", scaled.headers().firstValue("content-type").orElseThrow());
                assertDimensions(scaled, 48, 48);
                assertArrayEquals(scaled.body(), request(endpoint + "&id=" + dir.getId() + "&size=48", "GET", null, false).body());
                assertDimensions(request(endpoint + "&size=24", "GET", null, false), 24, 24);
                assertDimensions(request(endpoint + "&id=2147483647&size=24", "GET", null, false), 24, 24);
                HttpResponse<byte[]> generated = request(endpoint + "&id=pl-" + playlist.getId() + "&size=32", "GET", null, false);
                assertEquals("image/png", generated.headers().firstValue("content-type").orElseThrow());
                assertDimensions(generated, 32, 32);
                HttpResponse<byte[]> head = request(endpoint + "&id=" + dir.getId(), "HEAD", null, false);
                assertEquals(200, head.statusCode());
                assertEquals("image/jpeg", head.headers().firstValue("content-type").orElseThrow());
                assertEquals(0, head.body().length);
            }
            assertArrayEquals(original, request("/rest/getCoverArt.view", "POST", auth + "&id=" + dir.getId(), false).body());
            assertEquals(403, request("/rest/getCoverArt", "POST", auth + "&id=" + dir.getId(), false).statusCode());
            artists.createOrUpdateArtist(new Artist(0, "Cover test artist", 1, Instant.now(), true, library.get(0).getId()));
            Artist artist = artists.getArtist("Cover test artist");
            assertNotNull(artist);
            assertDimensions(request("/rest/getCoverArt?" + auth + "&id=ar-" + artist.getId() + "&size=32", "GET", null, false), 32, 32);
            albums.createOrUpdateAlbum(new Album(0, albumPath, "Cover test album", "Cover test artist", 1, 1,
                    null, null, 0, null, null, Instant.now(), Instant.now(), true, library.get(0).getId(), null));
            Album album = albums.getAlbum("Cover test artist", "Cover test album");
            assertNotNull(album);
            assertDimensions(request("/rest/getCoverArt?" + auth + "&id=al-" + album.getId() + "&size=32", "GET", null, false), 32, 32);
            String parallelPath = "/rest/getCoverArt?" + auth + "&id=" + dir.getId() + "&size=47";
            var first = CompletableFuture.supplyAsync(() -> uncheckedRequest(parallelPath));
            var second = CompletableFuture.supplyAsync(() -> uncheckedRequest(parallelPath));
            assertArrayEquals(first.get().body(), second.get().body());
            assertDimensions(first.get(), 47, 47);
            HttpResponse<byte[]> restScaled = request("/rest/getCoverArt?" + auth + "&id=" + dir.getId() + "&size=48", "GET", null, false);

            for (String path : new String[]{"/ext/coverArt", "/ext/coverArt.view"}) {
                String signed = "/" + jwt.addJWTToken("admin", path.substring(1) + "?id=" + dir.getId() + "&size=48");
                HttpResponse<byte[]> external = request(signed, "GET", null, false);
                assertEquals(path, 200, external.statusCode());
                assertEquals("image/jpeg", external.headers().firstValue("content-type").orElseThrow());
                assertArrayEquals(restScaled.body(), external.body());
                assertEquals(200, request(signed, "HEAD", null, false).statusCode());
                String signedPath = path.substring(1) + "?id=" + dir.getId() + "&size=48";
                String expired = "/" + jwt.addJWTToken("admin", signedPath, Instant.now().minusSeconds(60));
                assertTrue(request(expired, "GET", null, false).statusCode() >= 300);
                int tokenStart = signed.indexOf("jwt=") + "jwt=".length();
                char firstTokenChar = signed.charAt(tokenStart);
                String tamperedToken = signed.substring(0, tokenStart)
                        + (firstTokenChar == 'a' ? 'b' : 'a') + signed.substring(tokenStart + 1);
                assertTrue(request(tamperedToken, "GET", null, false).statusCode() >= 300);
                assertTrue(request(signed.replace("id=" + dir.getId(), "id=" + (dir.getId() + 1)),
                        "GET", null, false).statusCode() >= 300);
                assertTrue(request(signed.replace(path, path.equals("/ext/coverArt")
                        ? "/ext/coverArt.view" : "/ext/coverArt"), "GET", null, false).statusCode() >= 300);
            }
        } finally {
            playlists.deletePlaylist(playlist.getId());
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }
    private void assertDimensions(HttpResponse<byte[]> response, int width, int height) throws Exception {
        assertEquals(200, response.statusCode());
        var image = ImageIO.read(new ByteArrayInputStream(response.body()));
        assertNotNull(image);
        assertEquals(width, image.getWidth());
        assertEquals(height, image.getHeight());
    }
    private HttpResponse<byte[]> uncheckedRequest(String path) {
        try { return request(path, "GET", null, false); } catch (Exception e) { throw new RuntimeException(e); }
    }
    private HttpResponse<byte[]> request(String path, String method, String form, boolean basic) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path));
        if (form != null) builder.header("Content-Type", "application/x-www-form-urlencoded");
        if (basic) builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8)));
        builder.method(method, form == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(form));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
