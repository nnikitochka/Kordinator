package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SecurityService;
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

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class MediaFileCommentIntegrationTest {

    @Autowired
    private org.airsonic.player.runtime.DaoGraph daoGraph;

    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private ApplicationContext context;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;

    @Autowired private SecurityService security;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void updatesCommentsWithSessionCsrfAndFolderPermissions() throws Exception {
        Path temp = Files.createTempDirectory("airsonic-tags-");
        Path albumDir = Files.createDirectories(temp.resolve("Artist").resolve("Album"));
        Path source = MusicFolderTestData.resolveMusicFolderPath().resolve("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes/Sixteen Horsepower - 01 I Seen What I Saw.mp3");
        Path songPath = Files.copy(source, albumDir.resolve("song.mp3"));
        MusicFolder own = new MusicFolder(1, temp, "Tags", MusicFolder.Type.MEDIA, true, Instant.now());
        MusicFolder other = MusicFolderTestData.getTestMusicFolders().get(1);
        UUID cleanup = ScanningTestUtils.before(List.of(own, other), folders, scanner);
        try {
            MediaFile song = daoGraph.getMediaFileDao().getMediaFile("Artist/Album/song.mp3", own.getId());
            MediaFile album = daoGraph.getMediaFileDao().getMediaFile("Artist/Album", own.getId());
            assertNotNull(song); assertNotNull(album);
            User editor = new User("tag-editor", null);
            editor.setRoles(Set.of(User.Role.COMMENT, User.Role.STREAM));
            security.createUser(editor, "secret", "test");
            folders.setMusicFoldersForUser(editor.getUsername(), Set.of(own.getId()));
            User denied = new User("tag-denied", null);
            denied.setRoles(Set.of(User.Role.COMMENT, User.Role.STREAM));
            security.createUser(denied, "secret", "test");
            folders.setMusicFoldersForUser(denied.getUsername(), Set.of(other.getId()));
            security.createUser(new User("tag-reader", null), "secret", "test");

            HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(send(browser, "/login", null, null).body());
            assertTrue(token.find());
            assertEquals(302, send(browser, "/login", "j_username=tag-editor&j_password=secret&_csrf=" + enc(token.group(1)), null).statusCode());
            HttpResponse<String> page = send(browser, "/main.view?id=" + album.getId(), null, null);
            assertEquals(page.body(), 200, page.statusCode());
            assertTrue(page.body().contains("/airsonic/script/media-file-comment.js?v=1"));
            assertFalse(page.body().contains("setMusicFileInfo"));
            token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(page.body());
            assertTrue(token.find());
            String csrf = token.group(1);
            String path = "/rest/setMediaFileComment";
            String comment = "Тест & <script>alert('x')</script>\nsecond line";
            String fields = "f=json&c=airsonic-web&id=" + album.getId() + "&comment=" + enc(comment);
            assertEquals(403, send(browser, path, fields, null).statusCode());
            assertEquals("ok", body(send(browser, path, fields, csrf)).path("status").asText());
            assertEquals(comment, daoGraph.getMediaFileDao().getMediaFile(album.getId()).getComment());
            assertEquals("ok", body(send(browser, path, fields.replace("id=" + album.getId(), "id=" + song.getId()), csrf)).path("status").asText());
            assertEquals(comment, daoGraph.getMediaFileDao().getMediaFile(song.getId()).getComment());
            assertEquals(10, body(send(browser, path, "f=json&id=" + album.getId(), csrf)).path("error").path("code").asInt());
            assertEquals(10, body(send(browser, path, "f=json&comment=x", csrf)).path("error").path("code").asInt());
            assertEquals(0, body(send(browser, path, "f=json&id=bad&comment=x", csrf)).path("error").path("code").asInt());
            assertEquals(70, body(send(browser, path, "f=json&id=2147483647&comment=x", csrf)).path("error").path("code").asInt());
            // Explicitly clearing a comment is distinct from omitting the parameter.
            HttpResponse<String> xml = send(browser, path, "id=" + album.getId() + "&comment=", csrf);
            assertTrue(xml.body(), xml.body().contains("status=\"ok\""));
            assertEquals("", daoGraph.getMediaFileDao().getMediaFile(album.getId()).getComment());
            assertTrue(xml.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            editor.setRoles(Set.of(User.Role.STREAM));
            security.updateUser(editor);
            assertEquals(50, body(send(browser, path, fields, csrf)).path("error").path("code").asInt());
            assertEquals("", daoGraph.getMediaFileDao().getMediaFile(album.getId()).getComment());
            assertEquals(405, send(browser, path, null, null).statusCode());
            for (String old : List.of("/setMusicFileInfo", "/setMusicFileInfo.view")) {
                assertEquals(404, send(browser, old, fields, csrf).statusCode());
            }
            assertFalse(context.containsBean("setMusicFileInfoController"));
            for (String username : List.of("tag-reader", "tag-denied")) {
                HttpClient deniedBrowser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
                Matcher login = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(send(deniedBrowser, "/login", null, null).body());
                assertTrue(login.find());
                send(deniedBrowser, "/login", "j_username=" + username + "&j_password=secret&_csrf=" + enc(login.group(1)), null);
                Matcher csrfMatcher = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(send(deniedBrowser, "/top.view", null, null).body());
                assertTrue(csrfMatcher.find());
                assertEquals(50, body(send(deniedBrowser, path, fields, csrfMatcher.group(1))).path("error").path("code").asInt());
                assertEquals("", daoGraph.getMediaFileDao().getMediaFile(album.getId()).getComment());
            }
            assertEquals(403, send(HttpClient.newHttpClient(), path, fields, null).statusCode());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
            try (var walk = Files.walk(temp)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }
    private String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private HttpResponse<String> send(HttpClient client, String path, String form, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (form != null) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
}
