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
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
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
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            List<MediaFile> songs = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true);
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
            JsonNode detailed = body(send("", "f=json&includeWebsiteDetails=true", false)).path("nowPlaying").path("entry");
            assertEquals(2, detailed.size());
            for (JsonNode entry : detailed) {
                assertTrue(entry.has("active"));
                assertTrue(entry.hasNonNull("transferId"));
                assertTrue(entry.hasNonNull("streamUrl"));
                assertTrue(entry.hasNonNull("albumUrl"));
                assertTrue(entry.hasNonNull("coverArtUrl"));
                assertTrue(entry.path("displayUsername").asText().contains("admin@"));
                assertEquals(entry.path("playerId").asInt() == active.getId(), entry.path("active").asBoolean());
            }
            HttpClient browser = HttpClient.newBuilder()
                    .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<String> login = browser(browser, "/login", null, null);
            java.util.regex.Matcher token = java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
            assertTrue(token.find());
            String encoded = java.net.URLEncoder.encode(token.group(1), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(302, browser(browser, "/login", "j_username=admin&j_password=admin&_csrf=" + encoded, null).statusCode());
            for (String path : List.of("/nowPlaying", "/nowPlaying.view")) {
                HttpResponse<String> navigation = browser(browser, path, null, null);
                assertEquals(navigation.body(), 302, navigation.statusCode());
                assertEquals("home.view", navigation.headers().firstValue("location").orElse(null));
            }
            HttpResponse<String> page = browser(browser, "/right.view", null, null);
            assertEquals(page.body(), 200, page.statusCode());
            assertTrue(page.body().contains("/airsonic/script/subsonic-now-playing.js"));
            assertFalse(page.body().contains("/app/nowPlaying/"));
            token = java.util.regex.Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(page.body());
            assertTrue(page.body(), token.find());
            String form = "f=json&c=airsonic-web&includeWebsiteDetails=true";
            assertEquals(403, browser(browser, "/rest/getNowPlaying", form, null).statusCode());
            assertEquals(2, body(browser(browser, "/rest/getNowPlaying", form, token.group(1))).path("nowPlaying").path("entry").size());
            HttpResponse<String> queuePage = browser(browser, "/playQueue.view", null, null);
            assertEquals(queuePage.body(), 200, queuePage.statusCode());
            assertTrue(queuePage.body().contains("/airsonic/script/subsonic-now-playing.js"));
            assertFalse(queuePage.body().contains("/app/nowPlaying/"));
            assertFalse(players.getPlayersForUserAndClientId("admin", "now-playing-test").isEmpty());
            assertEquals("ok", body(send(".view", "f=json", true)).path("status").asText());
            assertEquals(403, send("", "f=json", true).statusCode());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }
    private HttpResponse<String> browser(HttpClient browser, String path, String form, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (form != null) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form));
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
