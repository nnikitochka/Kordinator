package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.PlaylistService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.util.MusicFolderTestData;
import java.util.List;
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
public class PlaylistImportIntegrationTest {

    @Autowired
    private org.airsonic.player.runtime.DaoGraph daoGraph;

    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private PlaylistService playlists;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;

    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void importFilesThroughRestWithoutSpringController() throws Exception {
        User member = new User("import-member", null); member.setRoles(Set.of(User.Role.STREAM));
        security.createUser(member, "secret", "test");
        assertFalse(context.containsBean("importPlaylistController"));
        var library = MusicFolderTestData.getTestMusicFolders();
        var cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            folders.setMusicFoldersForUser(member.getUsername(), library.stream().map(org.airsonic.player.domain.MusicFolder::getId).toList());
            var album = daoGraph.getMediaFileDao().getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            List<MediaFile> songs = daoGraph.getMediaFileDao().getChildrenOf(album.getPath(), album.getFolderId(), true);
            assertTrue(songs.size() >= 2);
            byte[] content = ("#EXTM3U\n" + songs.get(0).getPath() + "\n" + songs.get(1).getPath() + "\n" + songs.get(0).getPath() + "\n").getBytes(StandardCharsets.UTF_8);
            HttpClient browser = login(member.getUsername(), "secret");
            String token = csrf(browser);
            for (String page : new String[]{"/importPlaylist", "/importPlaylist.view"}) {
                var result = get(browser, page);
                assertEquals(200, result.statusCode());
                assertTrue(result.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
                assertTrue(result.body().contains("/airsonic/rest/importPlaylist"));
                assertTrue(result.body().contains("bindPlaylistImport("));
                assertFalse(result.body().contains("${"));
                assertEquals(200, browser.send(HttpRequest.newBuilder(URI.create(base() + page)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
                assertEquals(405, post(browser, page, "_csrf=" + encode(token)).statusCode());
                assertEquals(401, get(client, page).statusCode());
            }
            int before = playlists.getAllPlaylists().size();
            assertEquals(403, multipart(browser, "?f=json", null, "file", "list.m3u", content).statusCode());
            assertEquals(403, sendMultipart(client, "?f=json", null, part("file", "list.m3u", content)).statusCode());
            HttpClient anonymous = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
            Matcher anonymousToken = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(get(anonymous, "/login").body());
            assertTrue(anonymousToken.find());
            assertEquals("failed", parse(sendMultipart(anonymous, "?f=json", anonymousToken.group(1), part("file", "list.m3u", content))).path("status").asText());
            assertEquals(405, get(browser, "/rest/importPlaylist?f=json").statusCode());
            for (String method : new String[]{"PUT", "DELETE", "PATCH"}) {
                assertEquals(405, browser.send(HttpRequest.newBuilder(URI.create(base() + "/rest/importPlaylist"))
                    .header("X-CSRF-TOKEN", token).method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            for (byte[] invalid : new byte[][]{new byte[0], "invalid file".getBytes(StandardCharsets.UTF_8), new byte[5 * 1024 * 1024 + 1]}) {
                error(upload(browser, token, "file", "bad.m3u", invalid), 0);
            }
            error(upload(browser, token, "wrong", "list.m3u", content), 0);
            error(upload(browser, token, "file", "", content), 0);
            error(parse(sendMultipart(browser, "?f=json", token)), 0);
            error(parse(sendMultipart(browser, "?f=json", token, part("file", "one.m3u", content), part("file", "two.m3u", content))), 0);
            error(parse(post(browser, "/rest/importPlaylist?f=json", "_csrf=" + encode(token))), 0);
            error(upload(browser, token, "file", "missing.m3u", "#EXTM3U\n/nonexistent/song.mp3\n".getBytes(StandardCharsets.UTF_8)), 0);
            assertEquals(before, playlists.getAllPlaylists().size());
            var response = multipart(browser, "?f=json&username=admin", token, "file", "../../Музыка & <test>.m3u8", content);
            assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            JsonNode imported = parse(response); ok(imported);
            JsonNode info = imported.path("playlistImport");
            assertEquals("Музыка & <test>", info.path("name").asText());
            int id = info.path("id").asInt();
            assertEquals(member.getUsername(), playlists.getPlaylist(id).getUsername());
            assertEquals("Музыка & <test>.m3u8", playlists.getPlaylist(id).getImportedFrom());
            assertTrue(playlists.getPlaylist(id).getShared());
            assertEquals(List.of(songs.get(0).getId(), songs.get(1).getId(), songs.get(0).getId()),
                playlists.getFilesInPlaylist(id).stream().map(MediaFile::getId).toList());
            var standard = parse(get(browser, "/rest/getPlaylist?f=json&id=" + id)); ok(standard);
            assertEquals(3, standard.path("playlist").path("entry").size());
            assertEquals(member.getUsername(), standard.path("playlist").path("owner").asText());
            // Default XML and admin access use the same extension, leaving standard playlist endpoints intact.
            var admin = login("admin", "admin");
            String pls = "[playlist]\nNumberOfEntries=1\nFile1=" + songs.get(1).getPath() + "\nVersion=2\n";
            var xml = multipart(admin, "?c=test", csrf(admin), "file", "Admin & list.pls", pls.getBytes(StandardCharsets.UTF_8));
            var factory = javax.xml.parsers.DocumentBuilderFactory.newDefaultNSInstance();
            var doc = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml.body())));
            assertEquals("ok", doc.getDocumentElement().getAttribute("status"));
            var row = (org.w3c.dom.Element) doc.getElementsByTagNameNS("http://subsonic.org/restapi", "playlistImport").item(0);
            assertEquals("Admin & list", row.getAttribute("name"));
            assertEquals("admin", playlists.getPlaylist(Integer.parseInt(row.getAttribute("id"))).getUsername());
            assertEquals(before + 2, playlists.getAllPlaylists().size());
        } finally { ScanningTestUtils.after(cleanup, folders); }
    }

    private String csrf(HttpClient browser) throws Exception {
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body()); assertTrue(token.find()); return token.group(1);
    }
    private JsonNode upload(HttpClient browser, String token, String field, String name, byte[] data) throws Exception {
        return parse(multipart(browser, "?f=json&username=admin", token == null ? csrf(browser) : token, field, name, data));
    }
    private JsonNode parse(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode()); return mapper.readTree(response.body()).path("subsonic-response");
    }
    private HttpResponse<String> multipart(HttpClient browser, String query, String token, String field, String name, byte[] data) throws Exception {
        return sendMultipart(browser, query, token, part(field, name, data));
    }
    private byte[] part(String field, String name, byte[] bytes) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(("--PlaylistImportBoundary\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\"" + name + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes); out.write("\r\n".getBytes(StandardCharsets.UTF_8)); return out.toByteArray();
    }
    private HttpResponse<String> sendMultipart(HttpClient browser, String query, String token, byte[]... parts) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); for (byte[] part : parts) out.write(part);
        out.write("--PlaylistImportBoundary--\r\n".getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder request = HttpRequest.newBuilder().timeout(java.time.Duration.ofSeconds(30)).uri(URI.create(base() + "/rest/importPlaylist" + query))
                .header("Content-Type", "multipart/form-data; boundary=PlaylistImportBoundary");
        if (token != null) request.header("X-CSRF-TOKEN", token);
        return browser.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpClient login(String username, String password) throws Exception {
        HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(get(browser, "/login").body());
        assertTrue(token.find());
        assertEquals(302, post(browser, "/login", "j_username=" + username + "&j_password=" + password + "&_csrf=" + encode(token.group(1))).statusCode());
        return browser;
    }
    private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(HttpRequest.newBuilder().timeout(java.time.Duration.ofSeconds(30)).uri(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient browser, String path, String form) throws Exception {
        return browser.send(HttpRequest.newBuilder().timeout(java.time.Duration.ofSeconds(30)).uri(URI.create(base() + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String base() { return "http://localhost:" + port + "/airsonic"; }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private void ok(JsonNode body) { assertEquals(body.toString(), "ok", body.path("status").asText()); }
    private void error(JsonNode body, int code) {
        assertEquals(body.toString(), "failed", body.path("status").asText());
        assertEquals(body.toString(), code, body.path("error").path("code").asInt());
    }
}
