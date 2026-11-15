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
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.metadata.MetaData;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
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
public class TagEditIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private ApplicationContext context;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao files;
    @Autowired private SecurityService security;
    @Autowired private MetaDataParserFactory parsers;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void editsActualFileAndEnforcesSessionCsrfAndFolderPermissions() throws Exception {
        Path temp = Files.createTempDirectory("airsonic-tags-");
        Path albumDir = Files.createDirectories(temp.resolve("Artist").resolve("Album"));
        Path source = MusicFolderTestData.resolveMusicFolderPath().resolve("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes/Sixteen Horsepower - 01 I Seen What I Saw.mp3");
        Path songPath = Files.copy(source, albumDir.resolve("song.mp3"));
        MusicFolder own = new MusicFolder(1, temp, "Tags", MusicFolder.Type.MEDIA, true, Instant.now());
        MusicFolder other = MusicFolderTestData.getTestMusicFolders().get(1);
        UUID cleanup = ScanningTestUtils.before(List.of(own, other), folders, scanner);
        try {
            MediaFile song = files.getMediaFile("Artist/Album/song.mp3", own.getId());
            MediaFile album = files.getMediaFile("Artist/Album", own.getId());
            assertNotNull(song); assertNotNull(album);
            MetaData before = parsers.getParser(songPath).getRawMetaData(songPath);
            User editor = new User("tag-editor", null);
            editor.setRoles(Set.of(User.Role.COVERART, User.Role.STREAM));
            security.createUser(editor, "secret", "test");
            folders.setMusicFoldersForUser(editor.getUsername(), Set.of(own.getId()));
            User denied = new User("tag-denied", null);
            denied.setRoles(Set.of(User.Role.COVERART, User.Role.STREAM));
            security.createUser(denied, "secret", "test");
            folders.setMusicFoldersForUser(denied.getUsername(), Set.of(other.getId()));
            security.createUser(new User("tag-reader", null), "secret", "test");

            HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(send(browser, "/login", null, null).body());
            assertTrue(token.find());
            assertEquals(302, send(browser, "/login", "j_username=tag-editor&j_password=secret&_csrf=" + enc(token.group(1)), null).statusCode());
            HttpResponse<String> page = send(browser, "/editTags?id=" + album.getId(), null, null);
            assertEquals(page.body(), 200, page.statusCode());
            assertTrue(page.body().contains("/airsonic/script/subsonic-tag-edit.js"));
            assertTrue(page.body().contains("/airsonic/rest/tagEditor"));
            assertFalse(context.containsBean("editTagsController"));
            assertFalse(page.body().contains("/app/tags/edit"));
            HttpResponse<String> editorData = send(browser, "/rest/tagEditor?id=" + album.getId(), null, null);
            assertEquals(editorData.body(), 200, editorData.statusCode());
            JsonNode editorJson = mapper.readTree(editorData.body());
            assertEquals(album.getId().intValue(), editorJson.path("id").asInt());
            assertEquals(song.getArtist(), editorJson.path("defaultArtist").asText());
            assertEquals(song.getAlbumName(), editorJson.path("defaultAlbum").asText());
            assertEquals(song.getId().intValue(), editorJson.path("songs").get(0).path("id").asInt());
            assertEquals("song", editorJson.path("songs").get(0).path("fileName").asText());
            assertEquals(1, editorJson.path("songs").get(0).path("suggestedTrack").asInt());
            assertFalse(editorJson.path("allGenres").isEmpty());
            assertEquals(400, send(browser, "/rest/tagEditor?id=not-a-number", null, null).statusCode());
            assertEquals(400, send(browser, "/rest/tagEditor?id=" + song.getId(), null, null).statusCode());
            HttpClient deniedBrowser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            String deniedLoginToken = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(send(deniedBrowser, "/login", null, null).body()).results().findFirst().orElseThrow().group(1);
            assertEquals(302, send(deniedBrowser, "/login", "j_username=tag-denied&j_password=secret&_csrf=" + enc(deniedLoginToken), null).statusCode());
            assertEquals(403, send(deniedBrowser, "/rest/tagEditor?id=" + album.getId(), null, null).statusCode());
            token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(page.body());
            assertTrue(token.find());
            String csrf = token.group(1);
            String fields = "f=json&c=airsonic-web&id=" + song.getId() + "&artist=" + enc("Artist & Тест")
                    + "&album=New+Album&title=New+Title&track=7&year=2020&genre=Jazz";
            assertEquals(403, send(browser, "/rest/editTags", fields, null).statusCode());
            assertEquals("UPDATED", body(send(browser, "/rest/editTags", fields, csrf)).path("tagEdit").path("result").asText());
            MetaData after = parsers.getParser(songPath).getRawMetaData(songPath);
            assertEquals("Artist & Тест", after.getArtist());
            assertEquals("New Album", after.getAlbumName());
            assertEquals("New Title", after.getTitle());
            assertEquals(Integer.valueOf(7), after.getTrackNumber());
            assertEquals(Integer.valueOf(2020), after.getYear());
            assertEquals("Jazz", after.getGenre());
            assertEquals(before.getAlbumArtist(), after.getAlbumArtist());
            assertEquals("SKIPPED", body(send(browser, "/rest/editTags", fields, csrf)).path("tagEdit").path("result").asText());
            assertEquals(10, body(send(browser, "/rest/editTags", "f=json", csrf)).path("error").path("code").asInt());
            assertEquals("failed", body(send(browser, "/rest/editTags", fields.replace("track=7", "track=invalid"), csrf)).path("status").asText());
            String auth = "v=" + TestCaseUtils.restApiVersion() + "&c=test&f=json&";
            HttpClient external = HttpClient.newHttpClient();
            assertEquals(50, body(send(external, "/rest/editTags.view", auth + "u=tag-reader&p=secret&id=" + song.getId(), null)).path("error").path("code").asInt());
            assertEquals(50, body(send(external, "/rest/editTags.view", auth + "u=tag-denied&p=secret&id=" + song.getId(), null)).path("error").path("code").asInt());
            HttpResponse<String> xml = send(external, "/rest/editTags.view",
                    "v=" + TestCaseUtils.restApiVersion() + "&u=tag-editor&p=secret&" + fields.replace("f=json", "f=xml"), null);
            assertEquals(xml.body(), 200, xml.statusCode());
            assertTrue(xml.body(), xml.body().contains("<tagEdit result=\"SKIPPED\""));
            assertEquals("New Title", parsers.getParser(songPath).getRawMetaData(songPath).getTitle());
            assertFalse(context.containsBean("tagWSController"));
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
