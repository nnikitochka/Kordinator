package org.airsonic.player.api;

import org.airsonic.player.domain.User;
import org.airsonic.player.service.*;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.dao.PlayQueueDao;
import org.airsonic.player.domain.*;
import org.airsonic.player.util.MusicFolderTestData;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.airsonic.player.TestCaseUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.HomeRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RandomQueueIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private MediaFileService mediaService;
    @Autowired private RatingService ratings;
    @Autowired private PlayerService players;
    @Autowired private PlayQueueDao savedQueues;
    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void randomFiltersModifyOnlyTheOwnedLiveQueue() throws Exception {
        var library = MusicFolderTestData.getTestMusicFolders();
        var cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            User user = new User("random-member", null); user.setRoles(Set.of(User.Role.STREAM));
            security.createUser(user, "secret", "test");
            folders.setMusicFoldersForUser(user.getUsername(), List.of(library.get(0).getId()));
            var browser = login(user.getUsername(), "secret");
            Matcher csrf = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body());
            assertTrue(csrf.find()); String token = csrf.group(1);
            assertFalse(context.containsBean("randomPlayQueueController"));
            String page = get(browser, "/more.view").body();
            assertTrue(page.contains("/airsonic/rest/createRandomPlayQueue"));
            assertTrue(page.contains("bindRandomQueue("));
            assertFalse(page.contains("action=\"randomPlayQueue.view"));
            var moreData = mapper.readTree(get(browser, "/rest/morePageData").body());
            assertTrue(moreData.path("user").path("streamRole").asBoolean());
            assertTrue(moreData.path("uploadDirectory").isNull());
            assertEquals(library.get(0).getId().longValue(), moreData.path("musicFolders").get(0).path("id").asLong());
            assertTrue(moreData.path("currentYear").asInt() >= 2026);
            assertEquals(404, post(browser, "/randomPlayQueue.view", "_csrf=" + encode(token)).statusCode());
            assertEquals(405, get(browser, "/rest/createRandomPlayQueue").statusCode());
            assertEquals("failed", body(get(client, "/rest/createRandomPlayQueue?f=json")).path("status").asText());
            var album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            var songs = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true);
            var song = songs.get(0); var other = songs.get(1);
            song.setYear(2001); song.setGenre("random-test-unique"); song.setPlayCount(7);
            song.setLastPlayed(Instant.now().minusSeconds(2 * 86400)); mediaService.updateMediaFile(song);
            mediaFiles.starMediaFile(song.getId(), user.getUsername());
            ratings.setRatingForUser(user.getUsername(), album, 4);
            Player player = new Player(); player.setUsername(user.getUsername()); player.setName("Random target"); players.createPlayer(player);
            Player untouched = new Player(); untouched.setUsername(user.getUsername()); untouched.setName("Other live queue"); players.createPlayer(untouched);
            untouched.getPlayQueue().addFiles(false, List.of(other));
            Player foreign = new Player(); foreign.setUsername("admin"); players.createPlayer(foreign);
            String prefix = "f=json&playerId=" + player.getId() + "&_csrf=" + encode(token);
            assertEquals(403, post(browser, "/rest/createRandomPlayQueue", "f=json&playerId=" + player.getId()).statusCode());
            for (String method : new String[]{"PUT", "DELETE", "PATCH"}) {
                assertEquals(405, browser.send(HttpRequest.newBuilder(URI.create(base() + "/rest/createRandomPlayQueue"))
                    .header("X-CSRF-TOKEN", token).method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            for (String id : new String[]{"bad", "2147483647", String.valueOf(foreign.getId())}) {
                error(body(post(browser, "/rest/createRandomPlayQueue", "f=json&playerId=" + id + "&_csrf=" + encode(token))), 50);
            }
            ok(body(post(browser, "/rest/savePlayQueue", "f=json&c=random-test&id=" + other.getId() + "&current=" + other.getId() + "&position=456&_csrf=" + encode(token))));
            var saved = savedQueues.getPlayQueue(user.getUsername());
            player.getPlayQueue().addFiles(false, List.of(other));
            String filters = "&size=10&year=2000+2002&genre=random-test-unique&songRating=starred&albumRatingValue=4&albumRatingComp=ge"
                    + "&playCountValue=6&playCountComp=gt&lastPlayedValue=1week&lastPlayedComp=gt&format=" + song.getFormat() + "&musicFolderId=" + library.get(0).getId();
            var append = post(browser, "/rest/createRandomPlayQueue", prefix + filters + "&addToPlaylist=Add");
            assertTrue(append.headers().firstValue("Cache-Control").orElse("").contains("no-store")); ok(body(append));
            assertEquals(List.of(other.getId(), song.getId()), ids(player)); assertNull(player.getPlayQueue().getRandomSearchCriteria());
            ok(body(post(browser, "/rest/createRandomPlayQueue", prefix + filters + "&autoRandom=Radio")));
            assertEquals(List.of(song.getId()), ids(player));
            RandomSearchCriteria criteria = player.getPlayQueue().getRandomSearchCriteria();
            assertEquals(2000, criteria.getFromYear().intValue()); assertEquals(2002, criteria.getToYear().intValue());
            assertEquals(4, criteria.getMinAlbumRating().intValue()); assertEquals(7, criteria.getMinPlayCount().intValue());
            assertTrue(criteria.isShowStarredSongs()); assertFalse(criteria.isShowUnstarredSongs());
            assertNotNull(criteria.getMinLastPlayedDate()); assertNull(criteria.getMaxLastPlayedDate());
            assertEquals(List.of(library.get(0).getId()), MusicFolder.toIdList(criteria.getMusicFolders()));
            JsonNode visible = body(post(browser, "/rest/livePlayQueue", "f=json&action=get&playerId=" + player.getId() + "&_csrf=" + encode(token)));
            assertEquals(song.getId().intValue(), visible.path("livePlayQueue").path("entry").get(0).path("id").asInt());
            // Validate everything before replacing either the queue or its radio criteria.
            for (String invalid : new String[]{"size=0", "size=-1", "size=bad", "year=2001", "year=2002+2000", "year=bad+2000",
                "albumRatingValue=6", "albumRatingValue=x", "albumRatingValue=4&albumRatingComp=no", "playCountValue=-1",
                "playCountValue=2147483647&playCountComp=gt", "songRating=no", "lastPlayedValue=no", "lastPlayedValue=1day&lastPlayedComp=no", "musicFolderId=bad"}) {
                error(body(post(browser, "/rest/createRandomPlayQueue", prefix + "&" + invalid)), 0);
                assertEquals(List.of(song.getId()), ids(player)); assertSame(criteria, player.getPlayQueue().getRandomSearchCriteria());
            }
            error(body(post(browser, "/rest/createRandomPlayQueue", prefix + "&musicFolderId=" + library.get(1).getId())), 50);
            assertEquals(List.of(song.getId()), ids(player));
            for (String comparison : new String[]{"lt", "le", "eq", "ge", "gt"}) {
                ok(body(post(browser, "/rest/createRandomPlayQueue", prefix + "&autoRandom=1&albumRatingValue=3&albumRatingComp=" + comparison + "&playCountValue=3&playCountComp=" + comparison)));
                var c = player.getPlayQueue().getRandomSearchCriteria();
                Integer low = switch (comparison) { case "eq", "ge" -> 3; case "gt" -> 4; default -> null; };
                Integer high = switch (comparison) { case "eq", "le" -> 3; case "lt" -> 2; default -> null; };
                assertEquals(low, c.getMinAlbumRating()); assertEquals(high, c.getMaxAlbumRating());
                assertEquals(low, c.getMinPlayCount()); assertEquals(high, c.getMaxPlayCount());
            }
            for (var period : Map.of("1day", 86400L, "1week", 604800L, "1month", 2629746L, "3months", 7889238L, "6months", 15778476L, "1year", 31556952L).entrySet()) {
                Instant before = Instant.now().minusSeconds(period.getValue());
                ok(body(post(browser, "/rest/createRandomPlayQueue", prefix + "&autoRandom=1&lastPlayedValue=" + period.getKey() + "&lastPlayedComp=lt")));
                Instant cutoff = player.getPlayQueue().getRandomSearchCriteria().getMaxLastPlayedDate();
                assertFalse(cutoff.isBefore(before)); assertFalse(cutoff.isAfter(Instant.now().minusSeconds(period.getValue())));
            }
            ok(body(post(browser, "/rest/createRandomPlayQueue", prefix + "&autoRandom=1&songRating=unstarred")));
            assertTrue(ids(player).stream().noneMatch(id -> id.equals(song.getId())));
            // Defaults use all accessible folders, accept empty number inputs and disable radio on ordinary requests.
            ok(body(post(browser, "/rest/createRandomPlayQueue", prefix + "&albumRatingValue=&playCountValue=&addToPlaylist=1")));
            assertNull(player.getPlayQueue().getRandomSearchCriteria());
            assertEquals(List.of(other.getId()), ids(untouched));
            var after = savedQueues.getPlayQueue(user.getUsername());
            assertEquals(saved.getMediaFileIds(), after.getMediaFileIds()); assertEquals(saved.getPositionMillis(), after.getPositionMillis());
            // A browser can omit playerId and use its existing website player selection.
            int selected = mapper.readTree(get(browser, "/playQueue/player").body()).path("id").asInt();
            var xml = post(browser, "/rest/createRandomPlayQueue", "size=1&_csrf=" + encode(token));
            assertTrue(xml.body().contains("status=\"ok\""));
            assertEquals(1, players.getPlayerById(selected).getPlayQueue().size());
        } finally { ScanningTestUtils.after(cleanup, folders); }
    }
    private List<Integer> ids(Player player) { return player.getPlayQueue().getFiles().stream().map(MediaFile::getId).toList(); }
    private void ok(JsonNode result) { assertEquals(result.toString(), "ok", result.path("status").asText()); }
    private void error(JsonNode result, int code) {
        assertEquals(result.toString(), "failed", result.path("status").asText()); assertEquals(code, result.path("error").path("code").asInt());
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }

    private HttpClient login(String username, String password) throws Exception {
        HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(get(browser, "/login").body());
        assertTrue(token.find());
        assertEquals(302, post(browser, "/login", "j_username=" + username + "&j_password=" + password + "&_csrf=" + encode(token.group(1))).statusCode());
        return browser;
    }
    private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient browser, String path, String form) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(base() + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String base() { return "http://localhost:" + port + "/airsonic"; }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
