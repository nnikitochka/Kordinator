package org.airsonic.player.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class WebsiteStreamIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private org.airsonic.player.service.JWTSecurityService jwt;
    @Autowired private org.airsonic.player.service.PlayerService players;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void servesWebsiteAndSignedStreamsWithoutSpringController() throws Exception {
        assertFalse(context.containsBean("streamController"));
        var library = MusicFolderTestData.getTestMusicFolders();
        var cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            var album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            var song = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true).get(0);
            byte[] expected = java.nio.file.Files.readAllBytes(song.getFullPath(library.get(0).getPath()));
            HttpClient admin = login("admin", "admin");
            String query = "format=raw&id=" + song.getId();
            for (String endpoint : List.of("stream", "stream/player.mp3", "ext/stream", "ext/stream/player.mp3")) {
                String path = endpoint.startsWith("ext/") ? "/" + jwt.addJWTToken("admin", endpoint + "?" + query) : "/" + endpoint + "?" + query;
                HttpResponse<byte[]> full = audio(admin, path, "GET", null, null);
                assertEquals(endpoint, 200, full.statusCode()); assertArrayEquals(expected, full.body());
                assertEquals(expected.length, full.headers().firstValueAsLong("Content-Length").orElse(-1));
                assertEquals("bytes", full.headers().firstValue("Accept-Ranges").orElse(""));
                var partial = audio(admin, path, "GET", "Range", "bytes=3-12");
                assertEquals(endpoint, 206, partial.statusCode()); assertArrayEquals(java.util.Arrays.copyOfRange(expected, 3, 13), partial.body());
                assertEquals("bytes 3-12/" + expected.length, partial.headers().firstValue("Content-Range").orElse(""));
                var suffix = audio(admin, path, "GET", "Range", "bytes=-10");
                assertEquals(206, suffix.statusCode()); assertArrayEquals(java.util.Arrays.copyOfRange(expected, expected.length - 10, expected.length), suffix.body());
                var multipart = audio(admin, path, "GET", "Range", "bytes=0-2,10-12");
                assertEquals(206, multipart.statusCode()); assertTrue(multipart.headers().firstValue("Content-Type").orElse("").startsWith("multipart/byteranges"));
                var invalid = audio(admin, path, "GET", "Range", "bytes=999999999-");
                assertEquals(416, invalid.statusCode()); assertEquals("bytes */" + expected.length, invalid.headers().firstValue("Content-Range").orElse(""));
                var head = audio(admin, path, "HEAD", null, null);
                assertEquals(200, head.statusCode()); assertEquals(0, head.body().length);
                assertEquals(expected.length, head.headers().firstValueAsLong("Content-Length").orElse(-1));
                var headRange = audio(admin, path, "HEAD", "Range", "bytes=0-9");
                assertEquals(206, headRange.statusCode()); assertEquals(10, headRange.headers().firstValueAsLong("Content-Length").orElse(-1));
                assertEquals(304, audio(admin, path, "GET", "If-None-Match", full.headers().firstValue("ETag").orElseThrow()).statusCode());
                assertEquals(304, audio(admin, path, "GET", "If-Modified-Since", full.headers().firstValue("Last-Modified").orElseThrow()).statusCode());
                var icy = audio(admin, path, "GET", "icy-metadata", "1");
                assertEquals(200, icy.statusCode()); assertTrue(icy.headers().firstValue("icy-metaint").isPresent());
            }
            if (song.getDuration() != null && song.getDuration() > 2) {
                int offset = (int) Math.round(expected.length / song.getDuration());
                var shifted = audio(admin, "/stream?" + query + "&offsetSeconds=1", "GET", "Range", "bytes=0-9");
                assertEquals(206, shifted.statusCode()); assertArrayEquals(java.util.Arrays.copyOfRange(expected, offset, offset + 10), shifted.body());
            }
            for (String invalid : List.of("id=bad", query + "&maxBitRate=x", query + "&offsetSeconds=x", query + "&player=bad"))
                assertEquals(invalid, 400, audio(admin, "/stream?" + invalid, "GET", null, null).statusCode());
            for (String method : List.of("POST", "PUT", "DELETE", "PATCH")) {
                assertEquals(405, admin.send(HttpRequest.newBuilder(URI.create(base() + "/stream?" + query)).header("X-CSRF-TOKEN", csrf(admin))
                        .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray()).statusCode());
            }
            User denied = new User("website-no-stream", null); security.createUser(denied, "secret", "test");
            User restricted = new User("website-restricted", null); restricted.setRoles(java.util.Set.of(User.Role.STREAM));
            security.createUser(restricted, "secret", "test");
            folders.setMusicFoldersForUser(restricted.getUsername(), java.util.Set.of(library.get(1).getId()));
            assertEquals(403, audio(login(denied.getUsername(), "secret"), "/stream?" + query, "GET", null, null).statusCode());
            assertEquals(403, audio(login(restricted.getUsername(), "secret"), "/stream?" + query, "GET", null, null).statusCode());
            // Public-share tokens intentionally permit playback without the account's STREAM role or folder membership.
            folders.setMusicFoldersForUser(denied.getUsername(), java.util.Set.of(library.get(1).getId()));
            String publicUrl = "/" + jwt.addJWTToken(denied.getUsername(), "ext/stream?" + query);
            assertArrayEquals(expected, audio(client, publicUrl, "GET", null, null).body());
            String signed = "/" + jwt.addJWTToken("admin", "ext/stream?" + query);
            assertArrayEquals(expected, audio(client, signed, "GET", null, null).body());
            String expired = "/" + jwt.addJWTToken("admin", org.springframework.web.util.UriComponentsBuilder.fromUriString("ext/stream?" + query), java.time.Instant.now().minusSeconds(60)).build().toUriString();
            for (String bad : List.of("/ext/stream?" + query, "/ext/stream?" + query + "&jwt=invalid", expired, signed.replace("/ext/stream?", "/ext/stream/other?"))) {
                var rejected = audio(client, bad, "GET", null, null); assertTrue(bad, rejected.statusCode() >= 300);
            }
            var player = new org.airsonic.player.domain.Player(); player.setUsername("admin"); players.createPlayer(player);
            player.getPlayQueue().addFiles(false, List.of(song));
            String live = "/" + jwt.addJWTToken("admin", "ext/stream?player=" + player.getId() + "&suffix=.mp3");
            var liveResponse = client.send(HttpRequest.newBuilder(URI.create(base() + live)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, liveResponse.statusCode());
            try (var input = liveResponse.body()) { assertArrayEquals(java.util.Arrays.copyOf(expected, 10), input.readNBytes(10)); }
        } finally { ScanningTestUtils.after(cleanup, folders); folders.expunge(); }
    }
    private HttpResponse<byte[]> audio(HttpClient browser, String path, String method, String header, String value) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + path)).timeout(java.time.Duration.ofSeconds(30)).method(method, HttpRequest.BodyPublishers.noBody());
        if (header != null) request.header(header, value);
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private String csrf(HttpClient browser) throws Exception {
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body()); assertTrue(token.find()); return token.group(1);
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
}
