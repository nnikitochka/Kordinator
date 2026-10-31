package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SettingsService;
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
import java.net.http.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SongsByGenreIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private SettingsService settings;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void preservesSongsByGenreContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            int first = library.get(0).getId();
            User restricted = new User("genre-restricted", null);
            restricted.setRoles(Set.of(User.Role.STREAM));
            security.createUser(restricted, "secret", "test");
            folders.setMusicFoldersForUser(restricted.getUsername(), Set.of(first));
            String q = "f=json&genre=Baroque%20Instrumental";
            for (String suffix : List.of("", ".view")) {
                JsonNode all = body(send(suffix, q, false)).path("songsByGenre").path("song");
                assertTrue(all.toString(), all.size() > 0);
                JsonNode page = body(send(suffix, q + "&count=1&offset=1", false)).path("songsByGenre").path("song");
                assertEquals(1, page.size());
                assertEquals(all.get(1).path("id"), page.get(0).path("id"));
                assertEquals(body(send(suffix, q + "&count=0", false)).path("songsByGenre"),
                        body(send(suffix, q + "&count=-1", false)).path("songsByGenre"));
                assertEquals(body(send(suffix, q + "&musicFolderId=" + first, false)).path("songsByGenre"),
                        body(send(suffix, q, false, "genre-restricted", "secret")).path("songsByGenre"));
                assertEquals(10, body(send(suffix, "f=json", false)).path("error").path("code").asInt());
                assertEquals(400, send(suffix, q + "&musicFolderId=bad", false).statusCode());
                assertEquals(400, send(suffix, q + "&musicFolderId=", false).statusCode());
                assertEquals(all, body(send(suffix, q + "&count=bad&offset=bad", false)).path("songsByGenre").path("song"));
                assertEquals(10, body(send(suffix, "f=json&musicFolderId=bad", false)).path("error").path("code").asInt());
                assertTrue(send(suffix, "f=xml&genre=Baroque%20Instrumental", false).body().contains("<songsByGenre"));
                assertTrue(send(suffix, "f=jsonp&callback=client.test&genre=Baroque%20Instrumental", false).body().startsWith("client.test("));
            }
            assertEquals("ok", body(send(".view", q, true)).path("status").asText());
            assertEquals(403, send("", q, true).statusCode());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }
    private HttpResponse<String> send(String suffix, String query, boolean post) throws Exception {
        return send(suffix, query, post, "admin", "admin");
    }
    private HttpResponse<String> send(String suffix, String query, boolean post, String user, String password) throws Exception {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=indexes-test&u=" + user + "&p=" + password + "&" + query;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/getSongsByGenre" + suffix + (post ? "" : "?" + params)));
        if (post) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(params));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
}
