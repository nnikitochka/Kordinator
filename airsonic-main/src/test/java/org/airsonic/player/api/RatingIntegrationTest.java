package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.RatingService;
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
import org.springframework.test.context.junit4.SpringRunner;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RatingIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private RatingService ratings;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void preservesSetRatingContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true).get(0);
            String id = "id=" + song.getId();
            String legacyId = StringUtil.utf8HexEncode(song.getFullPath(library.get(0).getPath()).toString());
            for (String suffix : List.of("", ".view")) {
                assertEquals("ok", body(send(suffix, "f=json&" + id + "&rating=3", false)).path("status").asText());
                assertEquals(3, ratings.getRatingForUser("admin", song).intValue());
                assertEquals("ok", body(send(suffix, "f=json&id=" + legacyId + "&rating=4", false)).path("status").asText());
                assertEquals(4, ratings.getRatingForUser("admin", song).intValue());
                assertEquals("ok", body(send(suffix, "f=json&" + id + "&rating=0", false)).path("status").asText());
                assertNull(ratings.getRatingForUser("admin", song));
                assertEquals(10, body(send(suffix, "f=json", false)).path("error").path("code").asInt());
                assertEquals(10, body(send(suffix, "f=json&" + id, false)).path("error").path("code").asInt());
                assertEquals(10, body(send(suffix, "f=json&rating=3", false)).path("error").path("code").asInt());
                assertEquals(70, body(send(suffix, "f=json&id=2147483647&rating=3", false)).path("error").path("code").asInt());
                assertEquals(400, send(suffix, "f=json&" + id + "&rating=abc", false).statusCode());
                assertEquals(400, send(suffix, "f=json&id=abc&rating=3", false).statusCode());
            }
            assertEquals("ok", body(send(".view", "f=json&" + id + "&rating=2", true)).path("status").asText());
            assertEquals(2, ratings.getRatingForUser("admin", song).intValue());
            assertEquals(403, send("", "f=json&" + id + "&rating=2", true).statusCode());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }
    private HttpResponse<String> send(String suffix, String query, boolean post) throws Exception {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=rating-test&u=admin&p=admin&" + query;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/setRating" + suffix + (post ? "" : "?" + params)));
        if (post) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(params));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
}
