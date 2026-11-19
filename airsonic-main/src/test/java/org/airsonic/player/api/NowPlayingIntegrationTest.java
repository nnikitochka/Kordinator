package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.PlayStatus;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.StatusService;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class NowPlayingIntegrationTest {

    @Autowired
    private org.airsonic.player.runtime.DaoGraph daoGraph;

    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;

    @Autowired private PlayerService players;
    @Autowired private StatusService status;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void preservesNowPlayingContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = daoGraph.getMediaFileDao().getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            List<MediaFile> songs = daoGraph.getMediaFileDao().getChildrenOf(album.getPath(), album.getFolderId(), true);
            Player active = player("admin", "active-player");
            Player remote = player("admin", "remote-player");
            security.createUser(new User("hidden-listener", null), "secret", "test");
            UserSettings hiddenSettings = settings.getUserSettings("hidden-listener");
            hiddenSettings.setNowPlayingAllowed(false);
            settings.updateUserSettings(hiddenSettings);
            Player hidden = player("hidden-listener", "hidden-player");
            status.addActiveLocalPlay(new PlayStatus(UUID.randomUUID(), songs.get(0), active, Instant.now().minus(3, ChronoUnit.MINUTES)));
            status.addRemotePlay(new PlayStatus(UUID.randomUUID(), songs.get(1), remote, Instant.now().minus(8, ChronoUnit.MINUTES)));
            status.addRemotePlay(new PlayStatus(UUID.randomUUID(), songs.get(0), hidden, Instant.now()));
            status.addRemotePlay(new PlayStatus(UUID.randomUUID(), songs.get(0), active, Instant.now().minus(61, ChronoUnit.MINUTES)));
            for (String suffix : List.of("", ".view")) {
                HttpResponse<String> response = send(suffix, "f=json", false);
                assertTrue(response.headers().allValues("set-cookie").stream().noneMatch(cookie -> cookie.startsWith("player-")));
                JsonNode entries = body(response).path("nowPlaying").path("entry");
                assertEquals(entries.toString(), 2, entries.size());
                assertEntry(entries, active, songs.get(0), 3);
                assertFalse(entries.get(0).has("active"));
                assertFalse(entries.get(0).has("displayUsername"));
                assertFalse(entries.get(0).has("streamUrl"));
                assertEntry(entries, remote, songs.get(1), 8);
                assertTrue(send(suffix, "f=xml", false).body().contains("<nowPlaying"));
                assertTrue(send(suffix, "f=jsonp&callback=client.test", false).body().startsWith("client.test("));
            }
            assertFalse(players.getPlayersForUserAndClientId("admin", "now-playing-test").isEmpty());
            assertEquals("ok", body(send(".view", "f=json", true)).path("status").asText());
            assertEquals(403, send("", "f=json", true).statusCode());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }
    private Player player(String username, String name) {
        Player p = new Player();
        p.setUsername(username);
        p.setName(name);
        p.setClientId(name);
        players.createPlayer(p);
        return players.getPlayersForUserAndClientId(username, name).get(0);
    }
    private void assertEntry(JsonNode entries, Player player, MediaFile file, int minutes) {
        for (JsonNode entry : entries) {
            if (entry.path("playerId").asInt() == player.getId()) {
                assertEquals(player.getUsername(), entry.path("username").asText());
                assertEquals(player.getName(), entry.path("playerName").asText());
                assertEquals(file.getId().toString(), entry.path("id").asText());
                assertTrue(entry.toString(), entry.path("minutesAgo").asInt() >= minutes && entry.path("minutesAgo").asInt() <= minutes + 1);
                return;
            }
        }
        fail("No entry for player " + player.getName() + ": " + entries);
    }
    private HttpResponse<String> send(String suffix, String query, boolean post) throws Exception {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=now-playing-test&u=admin&p=admin&" + query;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/getNowPlaying" + suffix + (post ? "" : "?" + params)));
        if (post) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(params));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
}
