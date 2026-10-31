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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PlayQueueIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void queueRoundTripAndValidation() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            for (String name : List.of("queue-user", "queue-other")) {
                User user = new User(name, null);
                user.setRoles(Set.of(User.Role.STREAM));
                security.createUser(user, "secret", "test");
            }
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            List<MediaFile> songs = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true).stream().filter(MediaFile::isFile).limit(2).toList();
            assertEquals(2, songs.size());
            int first = songs.get(0).getId();
            int second = songs.get(1).getId();
            for (String suffix : List.of("", ".view")) {
                assertTrue(body(call("getPlayQueue" + suffix, "queue-user", "")).path("playQueue").isMissingNode());
                error(call("savePlayQueue" + suffix, "queue-user", ""), 0);
                error(call("savePlayQueue" + suffix, "queue-user", "id=" + first + "&current=" + second), 0);
                assertEquals(400, call("savePlayQueue" + suffix, "queue-user", "id=" + first + "&current=bad").statusCode());
                assertEquals(400, call("savePlayQueue" + suffix, "queue-user", "id=" + first + "&current=" + first + "&position=bad").statusCode());
                error(call("savePlayQueue" + suffix, "queue-user", "id=" + first + "&id=bad&current=" + first), 0);
            }
            ok(call("savePlayQueue", "queue-user", "id=" + first + "&id=" + second + "&current=" + second + "&position=1234"));
            JsonNode queue = body(call("getPlayQueue.view", "queue-user", "")).path("playQueue");
            assertEquals("queue-user", queue.path("username").asText());
            assertEquals(second, queue.path("current").asInt());
            assertEquals(1234, queue.path("position").asLong());
            assertEquals("queue-test", queue.path("changedBy").asText());
            assertFalse(queue.path("changed").isMissingNode());
            assertEquals(2, queue.path("entry").size());
            assertEquals(first, queue.path("entry").get(0).path("id").asInt());
            assertEquals(second, queue.path("entry").get(1).path("id").asInt());
            assertTrue(body(call("getPlayQueue", "queue-other", "")).path("playQueue").isMissingNode());
            error(call("savePlayQueue", "queue-user", "id=" + first + "&current=" + second), 0);
            assertEquals(second, body(call("getPlayQueue", "queue-user", "")).path("playQueue").path("current").asInt());
            String xmlUrl = "http://localhost:" + port + "/airsonic/rest/getPlayQueue.view?v=" + TestCaseUtils.restApiVersion() + "&c=queue-test&u=queue-user&p=secret&f=xml";
            HttpResponse<String> xml = client.send(HttpRequest.newBuilder(URI.create(xmlUrl)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, xml.statusCode());
            assertTrue(xml.body(), xml.body().contains("<playQueue "));
            ok(call("savePlayQueue.view", "queue-user", "id=" + first + "&current=" + first));
            assertEquals(first, body(call("getPlayQueue", "queue-user", "")).path("playQueue").path("current").asInt());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }
    private HttpResponse<String> call(String path, String user, String query) throws Exception {
        String url = "http://localhost:" + port + "/airsonic/rest/" + path + "?v=" + TestCaseUtils.restApiVersion() + "&c=queue-test&u=" + user + "&p=secret&f=json&" + query;
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
    private void ok(HttpResponse<String> response) throws Exception { assertEquals(response.body(), "ok", body(response).path("status").asText()); }
    private void error(HttpResponse<String> response, int code) throws Exception { assertEquals(response.body(), code, body(response).path("error").path("code").asInt()); }
}
