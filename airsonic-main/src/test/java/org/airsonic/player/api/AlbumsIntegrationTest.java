package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.AlbumNotes;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AlbumsIntegrationTest {

    @Autowired
    private org.airsonic.player.runtime.DaoGraph daoGraph;

    @ClassRule
    public static final HomeRule homeRule = new HomeRule();

    @LocalServerPort
    private int port;
    @Autowired
    private MediaFolderService folders;
    @Autowired
    private MediaScannerService scanner;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void albumEndpointsPreserveClientContracts() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            Album album = new Album(0, "albums-test", "Album contract", "Album artist", 1, 10,
                    null, null, 0, null, null, Instant.now(), Instant.now(), true, library.get(0).getId(), null);
            daoGraph.getAlbumDao().createOrUpdateAlbum(album);
            album = daoGraph.getAlbumDao().getAlbum("Album artist", "Album contract");
            assertNotNull(album);
            int id = album.getId();
            MediaFile albumDir = daoGraph.getMediaFileDao().getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = daoGraph.getMediaFileDao().getChildrenOf(albumDir.getPath(), albumDir.getFolderId(), true).stream()
                    .filter(MediaFile::isFile).findFirst().orElseThrow();
            song.setAlbumName("Album contract");
            song.setAlbumArtist("Album artist");
            daoGraph.getMediaFileDao().createOrUpdateMediaFile(song, ignored -> { });
            for (String suffix : List.of("", ".view")) {
                String albumPath = "/rest/getAlbum" + suffix;
                JsonNode result = body(send(albumPath + "?" + credentials() + "&f=json&id=" + id, null, null));
                assertEquals("ok", result.path("status").asText());
                assertEquals("Album contract", result.path("album").path("name").asText());
                assertEquals(String.valueOf(id), result.path("album").path("id").asText());
                assertEquals(String.valueOf(song.getId()), result.path("album").path("song").get(0).path("id").asText());
                assertError(send(albumPath + "?" + credentials() + "&f=json", null, null), 10, "Required param (id) is missing");
                assertEquals(400, send(albumPath + "?" + credentials() + "&id=bad", null, null).statusCode());
                assertError(send(albumPath + "?" + credentials() + "&f=json&id=2147483647", null, null), 70, "Album not found.");
                for (String endpoint : List.of("getAlbumList", "getAlbumList2")) {
                    String path = "/rest/" + endpoint + suffix;
                    String field = endpoint.equals("getAlbumList") ? "albumList" : "albumList2";
                    assertError(send(path + "?" + credentials() + "&f=json", null, null), 10, "Required param (type) is missing");
                    assertError(send(path + "?" + credentials() + "&f=json&type=byGenre", null, null), 10, "Required param (genre) is missing");
                    assertError(send(path + "?" + credentials() + "&f=json&type=byYear", null, null), 10, "Required param (fromYear) is missing");
                    assertError(send(path + "?" + credentials() + "&f=json&type=byYear&fromYear=2000", null, null), 10, "Required param (toYear) is missing");
                    assertEquals(400, send(path + "?" + credentials() + "&type=byYear&fromYear=bad&toYear=2020", null, null).statusCode());
                    assertEquals(400, send(path + "?" + credentials() + "&type=newest&musicFolderId=bad", null, null).statusCode());
                    assertError(send(path + "?" + credentials() + "&f=json&type=invalid", null, null), 0, "Request processing failed: java.lang.Exception: Invalid list type: invalid");
                    for (String type : List.of("frequent", "recent", "newest", "starred", "alphabeticalByArtist", "alphabeticalByName", "random", "byGenre&genre=Rock", "byYear&fromYear=1900&toYear=2100")) {
                        JsonNode list = body(send(path + "?" + credentials() + "&f=json&type=" + type + "&size=bad&offset=bad", null, null));
                        assertEquals(type, "ok", list.path("status").asText());
                        assertTrue(type, list.has(field));
                    }
                    JsonNode empty = body(send(path + "?" + credentials() + "&f=json&type=newest&size=-1", null, null));
                    JsonNode zero = body(send(path + "?" + credentials() + "&f=json&type=newest&size=0", null, null));
                    assertEquals(zero.path(field).path("album").size(), empty.path(field).path("album").size());
                    JsonNode filtered = body(send(path + "?" + credentials() + "&f=json&type=newest&musicFolderId=" + library.get(0).getId(), null, null));
                    assertEquals("ok", filtered.path("status").asText());
                }
            }
            JsonNode albums = body(send("/rest/getAlbumList2?" + credentials() + "&f=json&type=newest&size=500", null, null)).path("albumList2").path("album");
            boolean found = false;
            for (JsonNode listed : albums) {
                if (listed.path("id").asInt() == id) {
                    assertEquals("Album contract", listed.path("name").asText());
                    found = true;
                }
            }
            assertTrue("Created album missing from list", found);
            assertEquals(400, send("/rest/getAlbumList?" + credentials() + "&musicFolderId=bad", null, null).statusCode());
            assertError(send("/rest/getAlbumList2?" + credentials() + "&f=json&musicFolderId=bad", null, null), 10, "Required param (type) is missing");
            assertError(send("/rest/getAlbumList2?" + credentials() + "&f=json&type=highest", null, null), 0, "Request processing failed: java.lang.Exception: Invalid list type: highest");
            assertEquals("ok", body(send("/rest/getAlbumList?" + credentials() + "&f=json&type=highest", null, null)).path("status").asText());
            HttpResponse<String> xml = send("/rest/getAlbum.view?id=" + id, null, "admin:admin");
            assertEquals(200, xml.statusCode());
            assertTrue(xml.body(), xml.body().contains("<album "));
            HttpResponse<String> jsonp = send("/rest/getAlbumList.view?" + credentials() + "&type=newest&f=jsonp&callback=client.albums", null, null);
            assertTrue(jsonp.body(), jsonp.body().startsWith("client.albums("));
            assertEquals("ok", body(send("/rest/getAlbum.view", credentials() + "&f=json&id=" + id, null)).path("status").asText());
            assertEquals(403, send("/rest/getAlbum", credentials() + "&f=json&id=" + id, null).statusCode());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    private void assertError(HttpResponse<String> response, int code, String message) throws Exception {
        JsonNode result = body(response);
        assertEquals("failed", result.path("status").asText());
        assertEquals(response.body(), code, result.path("error").path("code").asInt());
        if (message != null) assertEquals(message, result.path("error").path("message").asText());
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }

    private String credentials() {
        return "v=" + TestCaseUtils.restApiVersion() + "&c=album-info-test&u=admin&p=admin";
    }

    private HttpResponse<String> send(String path, String form, String basic) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/airsonic" + path))
                .timeout(Duration.ofSeconds(10));
        if (basic != null) {
            request.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
        }
        if (form != null) {
            request.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
