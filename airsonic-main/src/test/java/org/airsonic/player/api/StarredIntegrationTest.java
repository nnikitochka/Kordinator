package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.Album;
import java.time.Instant;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.BookmarkService;
import org.airsonic.player.util.StringUtil;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class StarredIntegrationTest {
    @ClassRule public static final HomeRule homeRule = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    @Autowired private ArtistDao artists;
    @Autowired private AlbumDao albums;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void starredOperationsPreserveClientContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile directory = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower", library.get(0).getId());
            MediaFile albumDir = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFiles.getChildrenOf(albumDir.getPath(), albumDir.getFolderId(), true).stream().filter(MediaFile::isFile).findFirst().orElseThrow();
            assertNotNull(directory);
            artists.createOrUpdateArtist(new Artist(0, "Starred contract", 1, Instant.now(), true, library.get(0).getId()));
            Artist artist = artists.getArtist("Starred contract");
            albums.createOrUpdateAlbum(new Album(0, "starred-contract", "Starred album", "Starred contract", 1, 10,
                    null, null, 0, null, null, Instant.now(), Instant.now(), true, library.get(0).getId(), null));
            Album album = albums.getAlbum("Starred contract", "Starred album");
            security.createUser(new User("starred-reader", null), "secret", "starred test");
            String reader = "starred-reader:secret";
            int songId = song.getId(), albumId = album.getId(), artistId = artist.getId();
            for (String suffix : List.of("", ".view")) {
                assertOk(call("star" + suffix, "", null));
                assertError(call("star" + suffix, "id=2147483647", null), 70);
                assertError(call("star" + suffix, "albumId=2147483647", null), 70);
                assertError(call("star" + suffix, "artistId=2147483647", null), 70);
                assertEquals(400, call("getStarred" + suffix, "musicFolderId=bad", null).statusCode());
                assertEquals(400, call("getStarred2" + suffix, "musicFolderId=bad", null).statusCode());
            }
            String all = "id=" + directory.getId() + "&id=" + albumDir.getId() + "&id=" + songId + "&albumId=" + albumId + "&artistId=" + artistId;
            assertOk(post("star.view", all, null));
            JsonNode old = body(call("getStarred", "musicFolderId=" + library.get(0).getId(), null)).path("starred");
            assertId(old.path("artist"), directory.getId());
            assertId(old.path("album"), albumDir.getId());
            assertId(old.path("song"), songId);
            JsonNode id3 = body(call("getStarred2.view", "musicFolderId=" + library.get(0).getId(), null)).path("starred2");
            assertId(id3.path("artist"), artistId);
            assertId(id3.path("album"), albumId);
            assertId(id3.path("song"), songId);
            assertEquals(0, body(call("getStarred2", "", reader)).path("starred2").path("song").size());
            assertOk(call("star", "id=" + songId, reader));
            assertOk(call("unstar", all, null));
            assertEquals(0, body(call("getStarred2", "", null)).path("starred2").path("song").size());
            assertId(body(call("getStarred2", "", reader)).path("starred2").path("song"), songId);
            assertOk(call("star", "id=" + songId + "&id=bad&albumId=" + albumId, null));
            JsonNode malformed = body(call("getStarred2", "", null)).path("starred2");
            assertEquals(0, malformed.path("song").size());
            assertId(malformed.path("album"), albumId);
            assertError(call("star", "id=" + songId + "&id=2147483647&artistId=" + artistId, null), 70);
            JsonNode partial = body(call("getStarred2", "", null)).path("starred2");
            assertId(partial.path("song"), songId);
            assertEquals(0, partial.path("artist").size());
            HttpResponse<String> xml = raw("getStarred.view", "", null, null, false);
            assertTrue(xml.body(), xml.body().contains("<starred>"));
            HttpResponse<String> jsonp = raw("getStarred2.view", "f=jsonp&callback=client.starred", null, null, false);
            assertTrue(jsonp.body(), jsonp.body().startsWith("client.starred("));
            assertEquals(403, post("star", "id=" + songId, null).statusCode());
            assertOk(post("unstar.view", "id=" + songId + "&albumId=" + albumId, null));
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    private void assertId(JsonNode items, int id) {
        for (JsonNode item : items) if (item.path("id").asInt() == id) return;
        fail("Missing id " + id + " in " + items);
    }

    private HttpResponse<String> call(String endpoint, String query, String basic) throws Exception {
        return raw(endpoint, "f=json&" + query, basic, null, false);
    }
    private HttpResponse<String> post(String endpoint, String query, String basic) throws Exception {
        return raw(endpoint, "f=json&" + query, basic, null, true);
    }
    private HttpResponse<String> raw(String endpoint, String query, String basic, String unused, boolean post) throws Exception {
        String auth = basic == null ? "&v=" + TestCaseUtils.restApiVersion() + "&c=playlists-test&u=admin&p=admin" : "";
        String all = query + auth;
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/airsonic/rest/" + endpoint + (post ? "" : "?" + all))).timeout(Duration.ofSeconds(10));
        if (basic != null) request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
        if (post) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(all));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
    private void assertOk(HttpResponse<String> response) throws Exception { assertEquals(response.body(), "ok", body(response).path("status").asText()); }
    private void assertError(HttpResponse<String> response, int code) throws Exception {
        JsonNode result = body(response);
        assertEquals(response.body(), "failed", result.path("status").asText());
        assertEquals(response.body(), code, result.path("error").path("code").asInt());
    }
}
