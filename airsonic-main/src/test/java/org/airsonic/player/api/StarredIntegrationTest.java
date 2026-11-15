package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.Album;
import java.time.Instant;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.BookmarkService;
import org.airsonic.player.util.StringUtil;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.util.HomeRule;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class StarredIntegrationTest {
    @ClassRule public static final HomeRule homeRule = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    @Autowired private ApplicationContext context;
    @Autowired private ArtistDao artists;
    @Autowired private AlbumDao albums;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void starredOperationsPreserveClientContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile directory = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower", library.get(0).getId());
            MediaFile albumDir = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFiles.getChildrenOf(albumDir.getPath(), albumDir.getFolderId(), true).stream().filter(MediaFile::isFile).findFirst().orElseThrow();
            assertNotNull(directory);
            artists.createOrUpdateArtist(new Artist(0, "Starred contract", 1, Instant.now(), true, library.get(0).getId()));
            Artist artist = artists.getArtist("Starred contract");
            albums.createOrUpdateAlbum(new Album(0, "starred-contract", "Starred album", "Starred contract", 1, 10,
                    null, null, 0, null, null, Instant.now(), Instant.now(), true, library.get(0).getId(), null));
            Album album = albums.getAlbum("Starred contract", "Starred album");
            security.createUser(new User("starred-reader", null), "secret", "starred test");
            String reader = "starred-reader:secret";
            int songId = song.getId(), albumId = album.getId(), artistId = artist.getId();
            for (String suffix : List.of("", ".view")) {
                assertOk(call("star" + suffix, "", null));
                assertError(call("star" + suffix, "id=2147483647", null), 70);
                assertError(call("star" + suffix, "albumId=2147483647", null), 70);
                assertError(call("star" + suffix, "artistId=2147483647", null), 70);
                assertEquals(400, call("getStarred" + suffix, "musicFolderId=bad", null).statusCode());
                assertEquals(400, call("getStarred2" + suffix, "musicFolderId=bad", null).statusCode());
            }
            String all = "id=" + directory.getId() + "&id=" + albumDir.getId() + "&id=" + songId + "&albumId=" + albumId + "&artistId=" + artistId;
            assertOk(post("star.view", all, null));
            HttpResponse<String> pageDataResponse = raw("starredPage", "", null, null, false);
            assertEquals(pageDataResponse.body(), 200, pageDataResponse.statusCode());
            JsonNode pageData = mapper.readTree(pageDataResponse.body());
            assertId(pageData.path("artists"), directory.getId());
            assertId(pageData.path("albums"), albumDir.getId());
            assertId(pageData.path("songs"), songId);
            assertTrue(pageData.path("videos").isArray());
            assertFalse(pageData.path("artists").get(0).path("name").asText().isBlank());
            assertFalse(pageData.path("albums").get(0).path("name").asText().isBlank());
            assertFalse(pageData.path("songs").get(0).path("title").asText().isBlank());
            assertTrue(pageData.path("streamAllowed").asBoolean());
            assertTrue(pageData.path("coverArtSize").asInt() > 0);
            assertFalse(pageData.has("user"));
            assertFalse(pageData.has("player"));
            JsonNode old = body(call("getStarred", "musicFolderId=" + library.get(0).getId(), null)).path("starred");
            assertId(old.path("artist"), directory.getId());
            assertId(old.path("album"), albumDir.getId());
            assertId(old.path("song"), songId);
            JsonNode id3 = body(call("getStarred2.view", "musicFolderId=" + library.get(0).getId(), null)).path("starred2");
            assertId(id3.path("artist"), artistId);
            assertId(id3.path("album"), albumId);
            assertId(id3.path("song"), songId);
            assertEquals(0, body(call("getStarred2", "", reader)).path("starred2").path("song").size());
            assertOk(call("star", "id=" + songId, reader));
            var readerSettings = settings.getUserSettings("starred-reader");
            readerSettings.setPartyModeEnabled(true);
            settings.updateUserSettings(readerSettings);
            JsonNode readerPage = mapper.readTree(raw("starredPage", "", reader, null, false).body());
            assertEquals(0, readerPage.path("artists").size());
            assertEquals(0, readerPage.path("albums").size());
            assertId(readerPage.path("songs"), songId);
            assertTrue(readerPage.path("partyModeEnabled").asBoolean());
            assertOk(call("unstar", all, null));
            JsonNode emptyPage = mapper.readTree(raw("starredPage", "", null, null, false).body());
            assertEquals(0, emptyPage.path("artists").size());
            assertEquals(0, emptyPage.path("albums").size());
            assertEquals(0, emptyPage.path("songs").size());
            assertEquals(0, emptyPage.path("videos").size());
            assertEquals(0, body(call("getStarred2", "", null)).path("starred2").path("song").size());
            assertId(body(call("getStarred2", "", reader)).path("starred2").path("song"), songId);
            assertOk(call("star", "id=" + songId + "&id=bad&albumId=" + albumId, null));
            JsonNode malformed = body(call("getStarred2", "", null)).path("starred2");
            assertEquals(0, malformed.path("song").size());
            assertId(malformed.path("album"), albumId);
            assertError(call("star", "id=" + songId + "&id=2147483647&artistId=" + artistId, null), 70);
            JsonNode partial = body(call("getStarred2", "", null)).path("starred2");
            assertId(partial.path("song"), songId);
            assertEquals(0, partial.path("artist").size());
            HttpResponse<String> xml = raw("getStarred.view", "", null, null, false);
            assertTrue(xml.body(), xml.body().contains("<starred>"));
            HttpResponse<String> jsonp = raw("getStarred2.view", "f=jsonp&callback=client.starred", null, null, false);
            assertTrue(jsonp.body(), jsonp.body().startsWith("client.starred("));
            assertEquals(403, post("star", "id=" + songId, null).statusCode());
            assertOk(post("unstar.view", "id=" + songId + "&albumId=" + albumId, null));
            HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
            String loginBody = browser(browser, "/login", null, null).body();
            Matcher csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(loginBody);
            assertTrue(loginBody, csrf.find());
            assertEquals(302, browser(browser, "/login", "j_username=admin&j_password=admin&_csrf=" + enc(csrf.group(1)), null).statusCode());
            for (String path : List.of("/starred", "/starred.view")) {
                HttpResponse<String> page = browser(browser, path, null, null);
                assertEquals(page.body(), 200, page.statusCode());
                assertTrue(page.body().contains("/airsonic/rest/starredPage"));
                assertFalse(page.body().contains("${model."));
            }
            assertFalse(context.containsBean("starredController"));
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    private void assertId(JsonNode items, int id) {
        for (JsonNode item : items) if (item.path("id").asInt() == id) return;
        fail("Missing id " + id + " in " + items);
    }

    private HttpResponse<String> call(String endpoint, String query, String basic) throws Exception {
        return raw(endpoint, "f=json&" + query, basic, null, false);
    }
    private HttpResponse<String> browser(HttpClient browser, String path, String form, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (form != null) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form));
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private String enc(String value) { return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8); }
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
