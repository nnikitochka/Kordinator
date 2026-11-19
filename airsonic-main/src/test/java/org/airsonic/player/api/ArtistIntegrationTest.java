package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.domain.Artist;
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
public class ArtistIntegrationTest {

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
    public void artistPreservesClientContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            daoGraph.getArtistDao().createOrUpdateArtist(new Artist(0, "Artist contract", 1, Instant.now(), true, library.get(0).getId()));
            Artist artist = daoGraph.getArtistDao().getArtist("Artist contract");
            assertNotNull(artist);
            Album album = new Album(0, "artist-album-test", "Artist album", "Artist contract", 1, 10,
                    null, null, 0, null, null, Instant.now(), Instant.now(), true, library.get(0).getId(), null);
            daoGraph.getAlbumDao().createOrUpdateAlbum(album);
            for (String suffix : List.of("", ".view")) {
                String path = "/rest/getArtist" + suffix;
                JsonNode result = body(send(path + "?" + credentials() + "&f=json&id=" + artist.getId(), null, null));
                assertEquals("ok", result.path("status").asText());
                assertEquals("Artist contract", result.path("artist").path("name").asText());
                assertEquals("Artist album", result.path("artist").path("album").get(0).path("name").asText());
                assertError(send(path + "?" + credentials() + "&f=json", null, null), 10, "Required param (id) is missing");
                assertEquals(400, send(path + "?" + credentials() + "&id=bad", null, null).statusCode());
                assertError(send(path + "?" + credentials() + "&f=json&id=2147483647", null, null), 70, "Artist not found.");
                HttpResponse<String> xml = send(path + "?id=" + artist.getId(), null, "admin:admin");
                assertTrue(xml.body(), xml.body().contains("<artist "));
                HttpResponse<String> jsonp = send(path + "?" + credentials() + "&id=" + artist.getId() + "&f=jsonp&callback=client.artist", null, null);
                assertTrue(jsonp.body(), jsonp.body().startsWith("client.artist("));
            }
            assertEquals("ok", body(send("/rest/getArtist.view", credentials() + "&f=json&id=" + artist.getId(), null)).path("status").asText());
            assertEquals(403, send("/rest/getArtist", credentials() + "&f=json&id=" + artist.getId(), null).statusCode());
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
