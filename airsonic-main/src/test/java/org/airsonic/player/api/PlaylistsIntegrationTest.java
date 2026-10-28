package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.PlaylistService;
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
public class PlaylistsIntegrationTest {
    @ClassRule public static final HomeRule homeRule = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    @Autowired private PlaylistService playlists;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void playlistOperationsPreserveClientContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            List<MediaFile> songs = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true);
            assertTrue(songs.size() >= 2);
            int a = songs.get(0).getId(), b = songs.get(1).getId();
            security.createUser(new User("playlist-reader", null), "secret", "playlist test");
            folders.setMusicFoldersForUser("playlist-reader", List.of(library.get(1).getId()));
            String reader = "playlist-reader:secret";

            assertError(call("createPlaylist", "", null), 10);
            assertError(call("getPlaylist", "", null), 10);
            assertError(call("updatePlaylist", "", null), 10);
            assertError(call("deletePlaylist", "", null), 10);
            assertError(call("getPlaylist", "id=2147483647", null), 70);
            assertError(call("createPlaylist", "playlistId=2147483647", null), 70);
            assertError(call("updatePlaylist", "playlistId=2147483647", null), 70);
            assertError(call("deletePlaylist", "id=2147483647", null), 70);
            assertEquals(400, call("getPlaylist", "id=bad", null).statusCode());
            assertEquals(400, call("createPlaylist", "playlistId=bad", null).statusCode());
            assertEquals(400, call("updatePlaylist", "playlistId=bad", null).statusCode());
            assertEquals(400, call("deletePlaylist", "id=bad", null).statusCode());

            assertOk(post("createPlaylist.view", "name=MyPlaylist&songId=" + a + "&songId=" + b + "&songId=" + a, null));
            JsonNode listing = body(call("getPlaylists", "", null)).path("playlists").path("playlist");
            assertEquals(1, listing.size());
            int id = listing.get(0).path("id").asInt();
            assertEquals("MyPlaylist", listing.get(0).path("name").asText());
            assertEquals("admin", listing.get(0).path("owner").asText());
            assertEquals(3, listing.get(0).path("songCount").asInt());
            assertFalse(listing.get(0).path("public").asBoolean());
            JsonNode entries = body(call("getPlaylist.view", "id=" + id, null)).path("playlist").path("entry");
            assertEquals(3, entries.size());
            assertEquals(String.valueOf(a), entries.get(0).path("id").asText());
            assertEquals(String.valueOf(b), entries.get(1).path("id").asText());
            assertEquals(String.valueOf(a), entries.get(2).path("id").asText());
            assertError(call("getPlaylist", "id=" + id, reader), 50);
            assertError(call("getPlaylists", "username=playlist-reader", reader), 50);
            assertError(call("updatePlaylist", "playlistId=" + id + "&name=Forbidden", reader), 50);
            assertError(call("createPlaylist", "playlistId=" + id, reader), 50);
            assertError(call("deletePlaylist", "id=" + id, reader), 50);

            assertOk(post("updatePlaylist.view", "playlistId=" + id + "&name=Renamed&comment=Comment&public=YES&songIndexToRemove=0&songIndexToRemove=2&songIndexToRemove=0&songIdToAdd=" + a, null));
            JsonNode updated = body(call("getPlaylist", "id=" + id, null)).path("playlist");
            assertEquals("Renamed", updated.path("name").asText());
            assertEquals("Comment", updated.path("comment").asText());
            assertTrue(updated.path("public").asBoolean());
            assertEquals(2, updated.path("entry").size());
            assertEquals(String.valueOf(b), updated.path("entry").get(0).path("id").asText());
            assertEquals(String.valueOf(a), updated.path("entry").get(1).path("id").asText());
            JsonNode filtered = body(call("getPlaylist", "id=" + id, reader)).path("playlist");
            assertEquals(0, filtered.path("entry").size());
            assertEquals(2, filtered.path("songCount").asInt());
            assertEquals(1, body(call("getPlaylists.view", "username=playlist-reader", null)).path("playlists").path("playlist").size());

            assertOk(call("createPlaylist", "playlistId=" + id + "&songId=" + b + "&songId=2147483647", null));
            assertEquals(1, playlists.getFilesInPlaylist(id).size());
            assertEquals(b, playlists.getFilesInPlaylist(id).get(0).getId().intValue());
            HttpResponse<String> xml = raw("getPlaylist.view", "id=" + id, null, null, false);
            assertEquals(200, xml.statusCode());
            assertTrue(xml.body(), xml.body().contains("<playlist "));
            HttpResponse<String> jsonp = raw("getPlaylists.view", "f=jsonp&callback=client.lists", null, null, false);
            assertTrue(jsonp.body(), jsonp.body().startsWith("client.lists("));
            assertEquals(403, post("deletePlaylist", "id=" + id, null).statusCode());
            assertOk(post("deletePlaylist.view", "id=" + id, null));
            assertError(call("getPlaylist", "id=" + id, null), 70);
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
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
