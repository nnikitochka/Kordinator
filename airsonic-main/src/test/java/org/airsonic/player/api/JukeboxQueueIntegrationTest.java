package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.PlayerTechnology;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.JukeboxService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.PlayQueueService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class JukeboxQueueIntegrationTest {
    @ClassRule public static final HomeRule homeRule = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private PlayerService players;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @MockitoBean private JukeboxService jukebox;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void commandsChangeTheRealPlayQueue() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            User user = new User("jukebox-test", null);
            user.setRoles(Set.of(User.Role.JUKEBOX, User.Role.STREAM));
            security.createUser(user, "secret", "jukebox test");
            folders.setMusicFoldersForUser("jukebox-test", List.of(library.get(0).getId()));
            when(jukebox.canControl(any())).thenReturn(true);
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true).stream().filter(MediaFile::isFile).findFirst().orElseThrow();
            assertOk(call("", "action=add&id=" + song.getId(), "jukebox-test", false));
            JsonNode queue = body(call("", "action=get", "jukebox-test", false)).path("jukeboxPlaylist");
            assertEquals(song.getId().intValue(), queue.path("entry").get(0).path("id").asInt());
            assertOk(call("", "action=stop", "jukebox-test", false));
            assertFalse(body(call("", "action=status", "jukebox-test", false)).path("jukeboxStatus").path("playing").asBoolean());
            assertOk(call("", "action=start", "jukebox-test", false));
            assertTrue(body(call("", "action=status", "jukebox-test", false)).path("jukeboxStatus").path("playing").asBoolean());
            assertOk(call("", "action=skip&index=0&offset=2", "jukebox-test", false));
            assertError(call("", "action=remove&index=0", "jukebox-test", false), 0);
            assertOk(call("", "action=clear", "jukebox-test", false));
            assertEquals(0, body(call("", "action=get", "jukebox-test", false)).path("jukeboxPlaylist").path("entry").size());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    private HttpResponse<String> call(String suffix, String query, String user, boolean post) throws Exception {
        return send(suffix, query, user, post, "json");
    }
    private HttpResponse<String> send(String suffix, String query, String user, boolean post, String format) throws Exception {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=jukebox-test&u=" + user + "&p=secret&f=" + format + "&" + query;
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/airsonic/rest/jukeboxControl" + suffix + (post ? "" : "?" + params))).timeout(Duration.ofSeconds(10));
        if (post) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(params));
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
