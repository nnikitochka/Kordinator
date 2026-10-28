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
import org.airsonic.player.service.ShareService;
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
public class SharesIntegrationTest {
    @ClassRule public static final HomeRule homeRule = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    @Autowired private ShareService shares;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shareOperationsPreserveClientContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            List<MediaFile> songs = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true);
            int song = songs.get(0).getId();
            security.createUser(new User("share-reader", null), "secret", "share test");
            String reader = "share-reader:secret";
            assertError(call("createShare", "", null), 10);
            assertError(call("createShare", "id=" + song, reader), 50);
            assertError(call("createShare", "id=bad", reader), 50);
            assertEquals(400, call("createShare", "id=bad", null).statusCode());
            assertEquals(400, call("createShare", "id=" + song + "&id=bad", null).statusCode());
            for (String endpoint : List.of("updateShare", "deleteShare")) {
                assertError(call(endpoint, "", null), 10);
                assertError(call(endpoint, "id=2147483647", null), 70);
                assertEquals(400, call(endpoint, "id=bad", null).statusCode());
            }
            JsonNode created = body(post("createShare.view", "id=" + song + "&id=" + songs.get(1).getId() + "&description=Original&expires=2000000000000", null)).path("shares").path("share").get(0);
            int id = created.path("id").asInt();
            assertEquals("admin", created.path("username").asText());
            assertEquals("Original", created.path("description").asText());
            assertTrue(created.path("url").asText().contains("/airsonic/ext/share/"));
            assertEquals(2, created.path("entry").size());
            assertEquals(String.valueOf(song), created.path("entry").get(0).path("id").asText());
            assertEquals(1, body(call("getShares", "", null)).path("shares").path("share").size());
            assertEquals(0, body(call("getShares.view", "", reader)).path("shares").path("share").size());
            assertError(call("updateShare", "id=" + id, reader), 50);
            assertError(call("deleteShare", "id=" + id, reader), 50);
            assertOk(post("updateShare.view", "id=" + id + "&description=Changed&expires=0", null));
            assertEquals("Changed", shares.getShareById(id).getDescription());
            assertNull(shares.getShareById(id).getExpires());
            assertOk(call("updateShare", "id=" + id + "&expires=2000000000000", null));
            assertNull(shares.getShareById(id).getDescription());
            assertEquals(2000000000000L, shares.getShareById(id).getExpires().toEpochMilli());
            assertOk(call("updateShare", "id=" + id + "&description=Again", null));
            assertEquals(2000000000000L, shares.getShareById(id).getExpires().toEpochMilli());
            HttpResponse<String> xml = raw("getShares.view", "", null, null, false);
            assertEquals(200, xml.statusCode());
            assertTrue(xml.body(), xml.body().contains("<shares>"));
            HttpResponse<String> jsonp = raw("getShares.view", "f=jsonp&callback=client.shares", null, null, false);
            assertTrue(jsonp.body(), jsonp.body().startsWith("client.shares("));
            assertEquals(403, post("deleteShare", "id=" + id, null).statusCode());
            assertOk(post("deleteShare.view", "id=" + id, null));
            assertError(call("deleteShare", "id=" + id, null), 70);

            User owner = new User("share-owner", null);
            owner.setRoles(java.util.Set.of(User.Role.SHARE));
            security.createUser(owner, "secret", "share test");
            folders.setMusicFoldersForUser("share-owner", List.of(library.get(0).getId()));
            String ownerAuth = "share-owner:secret";
            JsonNode own = body(call("createShare", "id=" + song + "&expires=bad", ownerAuth)).path("shares").path("share").get(0);
            int ownId = own.path("id").asInt();
            assertEquals("share-owner", own.path("username").asText());
            assertNotNull(shares.getShareById(ownId).getExpires());
            assertOk(call("updateShare", "id=" + ownId + "&description=Owned", ownerAuth));
            assertEquals("Owned", shares.getShareById(ownId).getDescription());
            assertOk(call("deleteShare", "id=" + ownId, null));
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
