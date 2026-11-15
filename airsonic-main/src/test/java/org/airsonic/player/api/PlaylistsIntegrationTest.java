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
import org.airsonic.player.service.PlaylistService;
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
public class PlaylistsIntegrationTest {
    @ClassRule public static final HomeRule homeRule = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    @Autowired private PlaylistService playlists;
    @Autowired private SettingsService settings;
    @Autowired private org.airsonic.player.service.PlayerService players;
    @Autowired private org.airsonic.player.service.MediaFileService mediaFileService;
    @Autowired private org.springframework.context.ApplicationContext context;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void playlistOperationsPreserveClientContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            List<MediaFile> songs = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true);
            assertTrue(songs.size() >= 2);
            int a = songs.get(0).getId(), b = songs.get(1).getId();
            security.createUser(new User("playlist-reader", null), "secret", "playlist test");
            folders.setMusicFoldersForUser("playlist-reader", List.of(library.get(1).getId()));
            String reader = "playlist-reader:secret";

            assertError(call("createPlaylist", "", null), 10);
            assertError(call("getPlaylist", "", null), 10);
            assertError(call("updatePlaylist", "", null), 10);
            assertError(call("deletePlaylist", "", null), 10);
            assertError(call("getPlaylist", "id=2147483647", null), 70);
            assertError(call("createPlaylist", "playlistId=2147483647", null), 70);
            assertError(call("updatePlaylist", "playlistId=2147483647", null), 70);
            assertError(call("deletePlaylist", "id=2147483647", null), 70);
            assertEquals(400, call("getPlaylist", "id=bad", null).statusCode());
            assertEquals(400, call("createPlaylist", "playlistId=bad", null).statusCode());
            assertEquals(400, call("updatePlaylist", "playlistId=bad", null).statusCode());
            assertEquals(400, call("deletePlaylist", "id=bad", null).statusCode());

            assertOk(post("createPlaylist.view", "name=MyPlaylist&songId=" + a + "&songId=" + b + "&songId=" + a, null));
            JsonNode listing = body(call("getPlaylists", "", null)).path("playlists").path("playlist");
            assertEquals(1, listing.size());
            int id = listing.get(0).path("id").asInt();
            assertEquals("MyPlaylist", listing.get(0).path("name").asText());
            assertEquals("admin", listing.get(0).path("owner").asText());
            assertEquals(3, listing.get(0).path("songCount").asInt());
            assertFalse(listing.get(0).path("public").asBoolean());
            JsonNode entries = body(call("getPlaylist.view", "id=" + id, null)).path("playlist").path("entry");
            assertEquals(3, entries.size());
            assertEquals(String.valueOf(a), entries.get(0).path("id").asText());
            assertEquals(String.valueOf(b), entries.get(1).path("id").asText());
            assertEquals(String.valueOf(a), entries.get(2).path("id").asText());
            assertError(call("getPlaylist", "id=" + id, reader), 50);
            assertError(call("getPlaylists", "username=playlist-reader", reader), 50);
            assertError(call("updatePlaylist", "playlistId=" + id + "&name=Forbidden", reader), 50);
            assertError(call("createPlaylist", "playlistId=" + id, reader), 50);
            assertError(call("deletePlaylist", "id=" + id, reader), 50);

            assertOk(post("updatePlaylist.view", "playlistId=" + id + "&name=Renamed&comment=Comment&public=YES&songIndexToRemove=0&songIndexToRemove=2&songIndexToRemove=0&songIdToAdd=" + a, null));
            JsonNode updated = body(call("getPlaylist", "id=" + id, null)).path("playlist");
            assertEquals("Renamed", updated.path("name").asText());
            assertEquals("Comment", updated.path("comment").asText());
            assertTrue(updated.path("public").asBoolean());
            assertEquals(2, updated.path("entry").size());
            assertEquals(String.valueOf(b), updated.path("entry").get(0).path("id").asText());
            assertEquals(String.valueOf(a), updated.path("entry").get(1).path("id").asText());
            JsonNode filtered = body(call("getPlaylist", "id=" + id, reader)).path("playlist");
            assertEquals(0, filtered.path("entry").size());
            assertEquals(2, filtered.path("songCount").asInt());
            assertEquals(1, body(call("getPlaylists.view", "username=playlist-reader", null)).path("playlists").path("playlist").size());

            assertOk(call("updatePlaylist", "playlistId=" + id + "&songIndexToRemove=0&songIndexToRemove=bad&songIdToAdd=" + a + "&songIdToAdd=bad", null));
            assertEquals(2, playlists.getFilesInPlaylist(id).size());
            assertOk(call("createPlaylist", "playlistId=" + id + "&songId=" + a + "&songId=bad", null));
            assertEquals(0, playlists.getFilesInPlaylist(id).size());
            assertOk(call("createPlaylist", "playlistId=" + id + "&songId=" + b + "&songId=2147483647", null));
            assertEquals(1, playlists.getFilesInPlaylist(id).size());
            assertEquals(b, playlists.getFilesInPlaylist(id).get(0).getId().intValue());
            HttpResponse<String> xml = raw("getPlaylist.view", "id=" + id, null, null, false);
            assertEquals(200, xml.statusCode());
            assertTrue(xml.body(), xml.body().contains("<playlist "));
            assertFalse(xml.body(), xml.body().contains("playlistIndex="));
            assertFalse(xml.body(), xml.body().contains("present="));
            assertFalse(xml.body(), xml.body().contains("formattedFileSize="));
            HttpResponse<String> jsonp = raw("getPlaylists.view", "f=jsonp&callback=client.lists", null, null, false);
            assertTrue(jsonp.body(), jsonp.body().startsWith("client.lists("));
            assertEquals(403, post("deletePlaylist", "id=" + id, null).statusCode());
            assertOk(post("deletePlaylist.view", "id=" + id, null));
            assertError(call("getPlaylist", "id=" + id, null), 70);
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    @Test
    public void websitePlaylistOperationsUseSessionRestAndPreserveUnavailableOccurrences() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        List<Integer> created = new java.util.ArrayList<>();
        org.airsonic.player.domain.Player player = new org.airsonic.player.domain.Player();
        player.setUsername("admin");
        players.createPlayer(player);
        try {
            assertFalse(context.containsBean("playlistWSController"));
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            List<MediaFile> songs = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true);
            MediaFile a = songs.get(0), b = songs.get(1);
            HttpClient browser = HttpClient.newBuilder()
                    .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<String> login = browser(browser, "/login", null, null);
            java.util.regex.Matcher token = java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
            assertTrue(login.body(), token.find());
            assertEquals(302, browser(browser, "/login", "j_username=admin&j_password=admin&_csrf=" + encode(token.group(1)), null).statusCode());
            HttpResponse<String> page = browser(browser, "/playlists.view", null, null);
            assertEquals(page.body(), 200, page.statusCode());
            assertTrue(page.body().contains("/airsonic/rest/playlistsPageSettings"));
            assertFalse(page.body().contains("${model."));
            JsonNode listPageSettings = mapper.readTree(browser(browser, "/rest/playlistsPageSettings", null, null).body());
            assertEquals("admin", listPageSettings.path("username").asText());
            assertEquals(settings.getUserSettings("admin").getViewAsList(), listPageSettings.path("viewAsList").asBoolean());
            assertEquals(settings.getUserSettings("admin").getPaginationSizePlaylist(), listPageSettings.path("initialPaginationSize").asInt());
            token = java.util.regex.Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(page.body());
            assertTrue(page.body(), token.find());
            String csrf = token.group(1);
            String form = "f=json&c=airsonic-web";
            assertEquals(403, browser(browser, "/rest/createPlaylist", form + "&source=empty", null).statusCode());
            JsonNode empty = body(browser(browser, "/rest/createPlaylist", form + "&source=empty&includePlaylist=true", csrf));
            assertEquals("ok", empty.path("status").asText());
            int id = empty.path("playlist").path("id").asInt();
            assertTrue(id > 0);
            created.add(id);
            assertFalse(empty.path("playlist").path("name").asText().isEmpty());
            assertFalse(empty.path("playlist").path("public").asBoolean());
            String update = form + "&playlistId=" + id + "&includeUnavailable=true";
            assertOk(browser(browser, "/rest/updatePlaylist", update + "&name=" + encode("Плейлист & <test>") + "&comment=&public=true&songIdToAdd=" + a.getId() + "&songIdToAdd=" + b.getId() + "&songIdToAdd=" + a.getId(), csrf));
            b.setPresent(false);
            mediaFileService.updateMediaFile(b);
            JsonNode details = body(browser(browser, "/rest/getPlaylist", form + "&id=" + id + "&includeEntryDetails=true", csrf)).path("playlist");
            assertEquals("Плейлист & <test>", details.path("name").asText());
            assertEquals(3, details.path("entry").size());
            assertTrue(details.path("entry").get(0).path("present").asBoolean());
            assertFalse(details.path("entry").get(1).path("present").asBoolean());
            assertEquals(2, details.path("entry").get(2).path("playlistIndex").asInt());
            assertTrue(details.path("entry").get(0).has("formattedFileSize"));
            JsonNode external = body(call("getPlaylist", "id=" + id, null)).path("playlist").path("entry");
            assertEquals(2, external.size());
            for (String field : List.of("present", "playlistIndex", "albumArtist", "lastPlayed", "lastScanned", "changed", "formattedFileSize", "formattedBitRate")) {
                assertFalse(field, external.get(0).has(field));
            }
            assertOk(browser(browser, "/rest/updatePlaylist", update + "&songIndexOrder=2&songIndexOrder=0&songIndexOrder=1", csrf));
            assertEquals(List.of(a.getId(), a.getId(), b.getId()), playlists.getFilesInPlaylist(id, true).stream().map(MediaFile::getId).toList());
            for (String badOrder : List.of("0&songIndexOrder=0&songIndexOrder=2", "0&songIndexOrder=1", "0&songIndexOrder=1&songIndexOrder=3", "0&songIndexOrder=1&songIndexOrder=bad")) {
                assertError(browser(browser, "/rest/updatePlaylist", update + "&name=ShouldNotChange&songIndexOrder=" + badOrder, csrf), 0);
                assertEquals("Плейлист & <test>", playlists.getPlaylist(id).getName());
                assertEquals(List.of(a.getId(), a.getId(), b.getId()), playlists.getFilesInPlaylist(id, true).stream().map(MediaFile::getId).toList());
            }
            assertError(browser(browser, "/rest/updatePlaylist", update + "&songIndexToRemove=9", csrf), 0);
            assertOk(browser(browser, "/rest/updatePlaylist", update + "&songIndexToRemove=0&songIndexToRemove=0", csrf));
            assertEquals(List.of(a.getId(), b.getId()), playlists.getFilesInPlaylist(id, true).stream().map(MediaFile::getId).toList());
            assertOk(browser(browser, "/rest/updatePlaylist", update + "&songIdToAdd=" + a.getId(), csrf));
            assertEquals(List.of(a.getId(), b.getId(), a.getId()), playlists.getFilesInPlaylist(id, true).stream().map(MediaFile::getId).toList());
            b.setPresent(true);
            mediaFileService.updateMediaFile(b);
            assertOk(call("star", "id=" + a.getId(), null));
            JsonNode starred = body(browser(browser, "/rest/createPlaylist", form + "&source=starred&includePlaylist=true", csrf));
            int starredId = starred.path("playlist").path("id").asInt();
            created.add(starredId);
            assertTrue(playlists.getFilesInPlaylist(starredId).stream().anyMatch(file -> file.getId().equals(a.getId())));
            players.getPlayerById(player.getId()).getPlayQueue().addFiles(false, a, b, a);
            JsonNode queue = body(browser(browser, "/rest/createPlaylist", form + "&source=playQueue&playerId=" + player.getId() + "&includePlaylist=true", csrf));
            int queueId = queue.path("playlist").path("id").asInt();
            assertTrue(queue.toString(), queueId > 0);
            created.add(queueId);
            assertEquals(List.of(a.getId(), b.getId(), a.getId()), playlists.getFilesInPlaylist(queueId).stream().map(MediaFile::getId).toList());
            String reader = "playlist-web-reader:secret";
            security.createUser(new User("playlist-web-reader", null), "secret", "playlist website test");
            assertEquals(0, body(call("getPlaylists", "writableOnly=true", reader)).path("playlists").path("playlist").size());
            folders.setMusicFoldersForUser("playlist-web-reader", List.of(library.get(1).getId()));
            JsonNode restricted = body(call("getPlaylist", "id=" + id + "&includeEntryDetails=true", reader)).path("playlist").path("entry");
            assertEquals(3, restricted.size());
            for (int index = 0; index < restricted.size(); index++) {
                JsonNode entry = restricted.get(index);
                assertEquals(index, entry.path("playlistIndex").asInt());
                assertFalse(entry.path("present").asBoolean());
                assertEquals("", entry.path("title").asText());
                assertFalse(entry.has("path"));
                assertFalse(entry.has("album"));
            }
            assertError(call("updatePlaylist", "playlistId=" + id + "&songIndexOrder=0&songIndexOrder=1&songIndexOrder=2", reader), 50);
            assertError(call("createPlaylist", "source=playQueue&playerId=" + player.getId(), reader), 50);
            assertError(call("createPlaylist", "source=playQueue&playerId=2147483647", null), 70);
            assertError(call("createPlaylist", "source=bad", null), 0);
            JsonNode pageSettings = mapper.readTree(browser(browser, "/rest/playlistPageSettings?id=" + id, null, null).body());
            assertEquals(id, pageSettings.path("playlistId").asInt());
            assertTrue(pageSettings.path("createdLabel").asText().contains("admin"));
            assertTrue(pageSettings.path("visibility").has("trackNumberVisible"));
            for (String path : List.of("/left.view", "/playlists.view", "/playlist.view?id=" + id,
                    "/main.view?id=" + album.getId(), "/playQueue.view", "/starred.view")) {
                HttpResponse<String> rendered = browser(browser, path, null, null);
                assertEquals(rendered.body(), 200, rendered.statusCode());
                assertTrue(path, rendered.body().contains("/airsonic/script/subsonic-playlists.js"));
                assertFalse(path, rendered.body().contains("/app/playlists/"));
                if (path.startsWith("/playlist.view")) {
                    assertTrue(rendered.body().contains("/airsonic/rest/playlistPageSettings"));
                    assertTrue(rendered.body().contains("/airsonic/script/cover-art-renderer.js?v=1"));
                    assertTrue(rendered.body().contains("id=\"playlistCoverArt\""));
                    assertFalse(rendered.body().contains("coverArt.jsp"));
                    assertFalse(context.containsBean("coverArtJspController"));
                    assertFalse(rendered.body().contains("${model."));
                }
            }
            assertOk(browser(browser, "/rest/deletePlaylist", form + "&id=" + id, csrf));
            assertError(call("getPlaylist", "id=" + id, null), 70);
            created.remove(Integer.valueOf(id));
        } finally {
            for (int id : created) playlists.deletePlaylist(id);
            players.removePlayerById(player.getId());
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    private String encode(String value) { return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private HttpResponse<String> browser(HttpClient browser, String path, String form, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path)).timeout(Duration.ofSeconds(10));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (form != null) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form));
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
