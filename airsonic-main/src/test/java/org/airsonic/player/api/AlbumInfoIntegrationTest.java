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
public class AlbumInfoIntegrationTest {

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

    @MockitoBean
    private LastFmService lastFm;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void preservesBothClientContracts() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = daoGraph.getMediaFileDao().getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes",
                    library.get(0).getId());
            assertNotNull(album);
            Album albumId3 = new Album(0, "album-info-test", "Album info test", "Album info artist", 1, 10,
                    null, null, 0, null, null, Instant.now(), Instant.now(), true, library.get(0).getId(), null);
            daoGraph.getAlbumDao().createOrUpdateAlbum(albumId3);
            albumId3 = daoGraph.getAlbumDao().getAlbum("Album info artist", "Album info test");
            assertNotNull(albumId3);
            AlbumNotes notes = new AlbumNotes("Album notes", "mbid", "https://example.org/lastfm", "small", "medium", "large");
            when(lastFm.getAlbumNotes(any(MediaFile.class))).thenReturn(notes);
            when(lastFm.getAlbumNotes(any(Album.class))).thenReturn(notes);
            for (String endpoint : List.of("getAlbumInfo", "getAlbumInfo2")) {
                boolean second = endpoint.endsWith("2");
                int id = second ? albumId3.getId() : album.getId();
                for (String suffix : List.of("", ".view")) {
                    String path = "/rest/" + endpoint + suffix;
                    HttpResponse<String> json = send(path + "?" + credentials() + "&f=json&id=" + id, null, null);
                    assertInfo(body(json));
                    HttpResponse<String> xml = send(path + "?id=" + id, null, "admin:admin");
                    assertEquals(200, xml.statusCode());
                    assertTrue(xml.body(), xml.body().contains("<albumInfo>"));
                    assertTrue(xml.body(), xml.body().contains("<notes>Album notes</notes>"));
                    HttpResponse<String> jsonp = send(path + "?" + credentials() + "&id=" + id + "&f=jsonp&callback=client.info", null, null);
                    assertTrue(jsonp.body(), jsonp.body().startsWith("client.info("));
                    assertInfo(mapper.readTree(jsonp.body().substring("client.info(".length(), jsonp.body().length() - 2)).path("subsonic-response"));
                    assertError(send(path + "?" + credentials() + "&f=json", null, null), 10, "Required param (id) is missing");
                    assertEquals(400, send(path + "?" + credentials() + "&f=json&id=bad", null, null).statusCode());
                    assertError(send(path + "?" + credentials() + "&f=json&id=2147483647", null, null), 70, second ? "Album not found." : "Media file not found.");
                    assertError(send(path + "?f=json", null, null), 10, null);
                    assertError(send(path + "?f=json&v=" + TestCaseUtils.restApiVersion() + "&c=test&u=admin&p=wrong", null, null), 40, null);
                }
                assertInfo(body(send("/rest/" + endpoint + ".view", credentials() + "&f=json&id=" + id, null)));
                assertEquals(403, send("/rest/" + endpoint, credentials() + "&f=json&id=" + id, null).statusCode());
                if (second) when(lastFm.getAlbumNotes(any(Album.class))).thenReturn(null);
                else when(lastFm.getAlbumNotes(any(MediaFile.class))).thenReturn(null);
                JsonNode empty = body(send("/rest/" + endpoint + "?" + credentials() + "&f=json&id=" + id, null, null));
                assertEquals("ok", empty.path("status").asText());
                assertTrue(empty.has("albumInfo"));
                assertFalse(empty.path("albumInfo").hasNonNull("notes"));
            }
            when(lastFm.getAlbumNotes(any(MediaFile.class))).thenReturn(notes);
            String oldId = StringUtil.utf8HexEncode(album.getFullPath(library.get(0).getPath()).toString());
            assertInfo(body(send("/rest/getAlbumInfo?" + credentials() + "&f=json&id=" + oldId, null, null)));
            verify(lastFm, atLeastOnce()).getAlbumNotes(any(MediaFile.class));
            verify(lastFm, atLeastOnce()).getAlbumNotes(any(Album.class));
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    private void assertInfo(JsonNode body) {
        assertEquals("ok", body.path("status").asText());
        JsonNode info = body.path("albumInfo");
        assertEquals("Album notes", info.path("notes").asText());
        assertEquals("mbid", info.path("musicBrainzId").asText());
        assertEquals("https://example.org/lastfm", info.path("lastFmUrl").asText());
        assertEquals("small", info.path("smallImageUrl").asText());
        assertEquals("medium", info.path("mediumImageUrl").asText());
        assertEquals("large", info.path("largeImageUrl").asText());
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
