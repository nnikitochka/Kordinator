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
public class JukeboxIntegrationTest {
    @ClassRule public static final HomeRule homeRule = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private PlayerService players;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @MockitoBean private JukeboxService jukebox;
    @MockitoBean private PlayQueueService queues;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void commandsPreserveClientContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            User user = new User("jukebox-test", null);
            user.setRoles(Set.of(User.Role.JUKEBOX, User.Role.STREAM));
            security.createUser(user, "secret", "jukebox test");
            security.createUser(new User("jukebox-denied", null), "secret", "jukebox test");
            when(jukebox.getGain(any())).thenReturn(0.5f);
            when(jukebox.canControl(any())).thenReturn(true);
            when(jukebox.getPosition(any())).thenReturn(17);
            for (String suffix : List.of("", ".view")) {
                assertError(call(suffix, "", "jukebox-denied", false), 50);
                assertError(call(suffix, "", "jukebox-test", false), 10);
                assertError(call(suffix, "action=skip", "jukebox-test", false), 10);
                assertError(call(suffix, "action=setGain", "jukebox-test", false), 10);
                assertEquals(400, call(suffix, "action=skip&index=bad", "jukebox-test", false).statusCode());
                assertEquals(400, call(suffix, "action=setGain&gain=bad", "jukebox-test", false).statusCode());
                assertError(call(suffix, "action=unknown", "jukebox-test", false), 0);
            }
            for (String action : List.of("start", "stop", "clear", "shuffle")) assertOk(call("", "action=" + action, "jukebox-test", false));
            assertOk(call("", "action=skip&index=2&offset=3", "jukebox-test", false));
            assertOk(call("", "action=skip&index=1&offset=bad", "jukebox-test", false));
            assertOk(call("", "action=remove&index=4", "jukebox-test", false));
            assertOk(call("", "action=setGain&gain=0.75", "jukebox-test", false));
            assertOk(call("", "action=add&id=3&id=5", "jukebox-test", false));
            assertOk(call("", "action=set&id=3&id=bad", "jukebox-test", false));
            verify(queues).start(any(Player.class));
            verify(queues).stop(any(Player.class));
            verify(queues).clear(any(Player.class));
            verify(queues).shuffle(any(Player.class));
            verify(queues).skip(any(Player.class), eq(2), eq(3000L));
            verify(queues).skip(any(Player.class), eq(1), eq(0L));
            verify(queues).remove(any(Player.class), eq(List.of(4)));
            verify(queues).setJukeboxGain(any(Player.class), eq(0.75f));
            verify(queues).add(any(Player.class), eq(List.of(3, 5)), isNull(), eq(true), eq(true));
            verify(queues).reset(any(Player.class), eq(List.of()), eq(true));
            Player player = players.getPlayersForUserAndClientId("jukebox-test", "jukebox-test-jukebox").get(0);
            assertEquals(PlayerTechnology.JUKEBOX, player.getTechnology());
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true).stream().filter(MediaFile::isFile).findFirst().orElseThrow();
            player.getPlayQueue().addFiles(true, song);
            JsonNode status = body(call("", "action=status", "jukebox-test", false)).path("jukeboxStatus");
            assertEquals(0, status.path("currentIndex").asInt());
            assertTrue(status.path("playing").asBoolean());
            assertEquals(17, status.path("position").asInt());
            assertEquals(0.5, status.path("gain").asDouble(), 0.001);
            JsonNode playlist = body(call(".view", "action=get", "jukebox-test", true)).path("jukeboxPlaylist");
            assertEquals(song.getId().intValue(), playlist.path("entry").get(0).path("id").asInt());
            when(jukebox.canControl(any())).thenReturn(false);
            JsonNode uncontrolled = body(call("", "action=status", "jukebox-test", false)).path("jukeboxStatus");
            assertEquals(-1, uncontrolled.path("currentIndex").asInt());
            assertFalse(uncontrolled.path("playing").asBoolean());
            assertEquals(0, uncontrolled.path("position").asInt());
            HttpResponse<String> xml = send(".view", "action=status", "jukebox-test", false, "xml");
            assertTrue(xml.body(), xml.body().contains("<jukeboxStatus "));
            HttpResponse<String> jsonp = send(".view", "action=status&callback=client.jukebox", "jukebox-test", false, "jsonp");
            assertTrue(jsonp.body(), jsonp.body().startsWith("client.jukebox("));
            assertEquals(403, call("", "action=status", "jukebox-test", true).statusCode());
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
