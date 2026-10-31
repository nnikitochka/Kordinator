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
import org.airsonic.player.service.BookmarkService;
import org.airsonic.player.util.StringUtil;
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
public class BookmarksIntegrationTest {
    @ClassRule public static final HomeRule homeRule = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    @Autowired private BookmarkService bookmarks;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void bookmarkOperationsPreserveClientContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true).stream().filter(MediaFile::isFile).findFirst().orElseThrow();
            song.setDuration(1000.0);
            mediaFiles.createOrUpdateMediaFile(song, ignored -> { });
            int id = song.getId();
            String oldId = StringUtil.utf8HexEncode(song.getFullPath(library.get(0).getPath()).toString());
            security.createUser(new User("bookmark-reader", null), "secret", "bookmark test");
            String reader = "bookmark-reader:secret";
            for (String suffix : List.of("", ".view")) {
                assertError(call("createBookmark" + suffix, "", null), 10);
                assertError(call("createBookmark" + suffix, "id=" + id, null), 10);
                assertError(call("deleteBookmark" + suffix, "", null), 10);
                assertEquals(400, call("createBookmark" + suffix, "id=bad&position=1", null).statusCode());
                assertEquals(400, call("createBookmark" + suffix, "id=" + id + "&position=bad", null).statusCode());
                assertEquals(400, call("deleteBookmark" + suffix, "id=bad", null).statusCode());
            }
            assertOk(call("createBookmark", "id=2147483647&position=100", null));
            assertOk(post("createBookmark.view", "id=" + id + "&position=123456&comment=First", null));
            JsonNode entry = body(call("getBookmarks", "", null)).path("bookmarks").path("bookmark").get(0);
            assertEquals(123456L, entry.path("position").asLong());
            assertEquals("admin", entry.path("username").asText());
            assertEquals("First", entry.path("comment").asText());
            assertTrue(entry.hasNonNull("created"));
            assertTrue(entry.hasNonNull("changed"));
            assertEquals(String.valueOf(id), entry.path("entry").path("id").asText());
            assertEquals(0, body(call("getBookmarks.view", "", reader)).path("bookmarks").path("bookmark").size());
            assertOk(call("createBookmark", "id=" + id + "&position=1000&comment=Other", reader));
            assertEquals(1, body(call("getBookmarks", "", reader)).path("bookmarks").path("bookmark").size());
            assertOk(call("deleteBookmark", "id=" + id, reader));
            assertNotNull(bookmarks.getBookmark("admin", id));
            assertNull(bookmarks.getBookmark("bookmark-reader", id));
            assertOk(call("createBookmark", "id=" + oldId + "&position=400000&comment=Updated", null));
            assertEquals(400000, bookmarks.getBookmark("admin", id).getPositionMillis());
            assertEquals("Updated", bookmarks.getBookmark("admin", id).getComment());
            HttpResponse<String> xml = raw("getBookmarks.view", "", null, null, false);
            assertEquals(200, xml.statusCode());
            assertTrue(xml.body(), xml.body().contains("<bookmarks>"));
            HttpResponse<String> jsonp = raw("getBookmarks.view", "f=jsonp&callback=client.bookmarks", null, null, false);
            assertTrue(jsonp.body(), jsonp.body().startsWith("client.bookmarks("));
            assertEquals(403, post("deleteBookmark", "id=" + id, null).statusCode());
            assertOk(post("deleteBookmark.view", "id=" + id, null));
            assertNull(bookmarks.getBookmark("admin", id));
            assertOk(call("createBookmark", "id=" + id + "&position=10000", null));
            assertNotNull(bookmarks.getBookmark("admin", id));
            assertOk(call("createBookmark", "id=" + id + "&position=950000", null));
            assertNull(bookmarks.getBookmark("admin", id));
            assertOk(call("createBookmark", "id=" + id + "&position=1000", null));
            assertOk(call("deleteBookmark", "id=" + oldId, null));
            assertNull(bookmarks.getBookmark("admin", id));
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
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
