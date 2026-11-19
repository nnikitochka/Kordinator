package org.airsonic.player.ktor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.api.ScanningTestUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Avatar;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.util.HomeRule;
import org.airsonic.player.util.MusicFolderTestData;
import org.airsonic.player.util.StringUtil;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class KtorServletIntegrationTest {

    @ClassRule
    public static final HomeRule homeRule = new HomeRule();

    @LocalServerPort
    private int port;

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private MediaScannerService mediaScannerService;

    @Autowired
    private MediaFileDao mediaFileDao;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Test
    public void ktorRespondsOnTheExistingServer() throws Exception {
        HttpResponse<String> response = get("/internal/ktor/ping", true);

        assertEquals(response.body(), 200, response.statusCode());
        assertEquals("pong", response.body());
    }

    @Test
    public void ktorRemainsProtectedByExistingSecurity() throws Exception {
        HttpResponse<String> response = get("/internal/ktor/ping", false);

        assertEquals(response.body(), 302, response.statusCode());
        String location = response.headers().firstValue("location").orElseThrow();
        assertTrue(location, URI.create(location).getPath().endsWith("/airsonic/login"));
    }

    @Test
    public void removedWebsitePagesReturnNotFound() throws Exception {
        for (String path : List.of("/", "/index", "/login", "/logout", "/recover", "/accessDenied",
                "/notFound", "/home", "/main", "/left", "/right", "/more", "/search", "/starred",
                "/bookmarks", "/playlist", "/playlists", "/playQueue", "/nowPlaying", "/editTags",
                "/changeCoverArt", "/lyrics", "/userSettings", "/settings", "/status", "/ext/share",
                "/rest/downloadMedia", "/rest/getWebsiteSettings", "/rest/updateWebsiteSettings",
                "/rest/getWebsiteUserAdministration", "/rest/saveWebsiteUser", "/stream", "/hls/hls.m3u8",
                "/hls/segment/segment.ts")) {
            HttpResponse<String> response = get(path, true);
            assertEquals(path + " should be removed", 404, response.statusCode());
            assertTrue(path + " should not redirect", response.headers().firstValue("location").isEmpty());
        }
    }

    @Test
    public void missingKtorRouteReturnsNotFound() throws Exception {
        HttpResponse<String> response = get("/internal/ktor/missing", true);

        assertEquals(response.body(), 404, response.statusCode());
    }

    @Test
    public void pingPreservesClientContract() throws Exception {
        for (String path : List.of("/rest/ping", "/rest/ping.view")) {
            HttpResponse<String> json = get(path + "?" + credentials() + "&f=json", false);
            subsonic(json);
            assertTrue(json.headers().firstValue("content-type").orElseThrow().startsWith("application/json"));
            subsonic(get(path + "?f=json", true));

            HttpResponse<String> xml = get(path + "?" + credentials(), false);
            assertEquals(200, xml.statusCode());
            assertTrue(xml.body(), xml.body().contains("status=\"ok\""));
            assertTrue(xml.headers().firstValue("content-type").orElseThrow().startsWith("text/xml"));

            HttpResponse<String> jsonp = get(path + "?" + credentials() + "&f=jsonp&callback=client.ping", false);
            assertEquals(200, jsonp.statusCode());
            assertTrue(jsonp.body(), jsonp.body().startsWith("client.ping("));
            assertTrue(jsonp.body(), jsonp.body().endsWith(");"));
            assertTrue(jsonp.headers().firstValue("content-type").orElseThrow().startsWith("application/javascript"));

            assertEquals(10, responseBody(get(path + "?f=json", false)).path("error").path("code").asInt());
            assertEquals(40, responseBody(get(path + "?v=" + TestCaseUtils.restApiVersion()
                    + "&c=ktor-test&u=admin&p=wrong&f=json", false)).path("error").path("code").asInt());
            assertEquals(30, responseBody(get(path + "?v=999.0.0&c=ktor-test&u=admin&p=admin&f=json", false))
                    .path("error").path("code").asInt());
        }
        subsonic(post("/rest/ping.view", credentials() + "&f=json"));
        assertEquals(403, post("/rest/ping", credentials() + "&f=json").statusCode());

        HttpRequest preflight = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/airsonic/rest/ping"))
                .header("Origin", "https://example.com")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> cors = client.send(preflight, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, cors.statusCode());
        assertTrue(cors.headers().firstValue("access-control-allow-origin").isPresent());
    }

    @Test
    public void imageRoutesRemainAvailableThroughKtorServlet() throws Exception {
        List<MusicFolder> folders = MusicFolderTestData.getTestMusicFolders();
        UUID cleanupId = ScanningTestUtils.before(folders, mediaFolderService, mediaScannerService);
        Avatar previousAvatar = settingsService.getCustomAvatar("admin");
        try {
            MediaFile album = mediaFileDao.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", folders.get(0).getId());
            Path image = folders.get(0).getPath().resolve("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes/cover.jpg");
            byte[] expected = Files.readAllBytes(image);
            settingsService.setCustomAvatar(new Avatar(0, "test", Instant.now(), "image/jpeg", 1, 1, image), "admin");
            String legacyId = StringUtil.utf8HexEncode(album.getFullPath(folders.get(0).getPath()).toString());
            for (String suffix : List.of("", ".view")) {
                HttpResponse<byte[]> coverArt = getBytes("/rest/getCoverArt" + suffix + "?" + credentials() + "&id=" + album.getId());
                assertEquals(200, coverArt.statusCode());
                assertEquals("image/jpeg", coverArt.headers().firstValue("content-type").orElseThrow());
                assertArrayEquals(expected, coverArt.body());
                assertArrayEquals(expected, getBytes("/rest/getCoverArt" + suffix + "?" + credentials() + "&id=" + legacyId).body());

                HttpResponse<byte[]> avatar = getBytes("/rest/getAvatar" + suffix + "?" + credentials() + "&username=admin&forceCustom=true");
                assertEquals(200, avatar.statusCode());
                assertEquals("image/jpeg", avatar.headers().firstValue("content-type").orElseThrow());
                assertArrayEquals(expected, avatar.body());
                assertEquals(404, getBytes("/rest/getAvatar" + suffix + "?" + credentials()).statusCode());
            }
        } finally {
            settingsService.setCustomAvatar(previousAvatar, "admin");
            ScanningTestUtils.after(cleanupId, mediaFolderService);
            mediaFolderService.expunge();
        }
    }

    private HttpResponse<byte[]> getBytes(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/airsonic" + path))
                .timeout(Duration.ofSeconds(10)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    public void scanEndpointActuallyIndexesLibrary() throws Exception {
        List<MusicFolder> folders = MusicFolderTestData.getTestMusicFolders();
        folders.forEach(mediaFolderService::createMusicFolder);
        try {
            JsonNode started = subsonic(get("/rest/startScan?" + credentials() + "&f=json", false));
            assertTrue(started.has("scanStatus"));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (mediaScannerService.isScanning() && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            assertTrue("Scan did not finish", !mediaScannerService.isScanning());
            JsonNode status = subsonic(get("/rest/getScanStatus?" + credentials() + "&f=json", false))
                    .path("scanStatus");
            assertTrue(status.toString(), !status.path("scanning").asBoolean());
            assertTrue(status.toString(), status.path("count").asInt() > 0);
            assertNotNull(mediaFileDao.getMediaFile(
                    "_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", folders.get(0).getId()));
        } finally {
            if (!mediaScannerService.isScanning()) {
                folders.forEach(folder -> mediaFolderService.deleteMusicFolder(folder.getId()));
                mediaFolderService.expunge();
            }
        }
    }

    @Test
    public void musicFolderRoutesPreserveClientContract() throws Exception {
        List<MusicFolder> folders = MusicFolderTestData.getTestMusicFolders();
        UUID cleanupId = ScanningTestUtils.before(folders, mediaFolderService, mediaScannerService);
        try {
            for (String suffix : List.of("", ".view")) {
                String path = "/rest/getMusicFolders" + suffix;
                HttpResponse<String> json = get(path + "?" + credentials() + "&f=json", false);
                JsonNode musicFolders = subsonic(json).path("musicFolders").path("musicFolder");
                assertEquals(json.body(), folders.get(0).getId().intValue(), musicFolders.path(0).path("id").asInt());
                assertEquals(json.body(), folders.get(0).getName(), musicFolders.path(0).path("name").asText());
                assertTrue(json.headers().firstValue("content-type").orElseThrow().startsWith("application/json"));

                HttpResponse<String> xml = get(path + "?c=ktor-test", true);
                assertEquals(xml.body(), 200, xml.statusCode());
                assertTrue(xml.body(), xml.body().contains("<musicFolders>"));
                assertTrue(xml.body(), xml.body().contains("id=\"" + folders.get(0).getId() + "\""));
                assertTrue(xml.headers().firstValue("content-type").orElseThrow().startsWith("text/xml"));
            }

            String form = credentials() + "&f=json";
            JsonNode posted = subsonic(post("/rest/getMusicFolders.view", form)).path("musicFolders").path("musicFolder");
            assertEquals(folders.get(0).getName(), posted.path(0).path("name").asText());
            assertEquals(403, post("/rest/getMusicFolders", form).statusCode());
        } finally {
            ScanningTestUtils.after(cleanupId, mediaFolderService);
            mediaFolderService.expunge();
        }
    }

    @Test
    public void searchRoutesPreserveClientContract() throws Exception {
        List<MusicFolder> folders = MusicFolderTestData.getTestMusicFolders();
        UUID cleanupId = ScanningTestUtils.before(folders, mediaFolderService, mediaScannerService);
        try {
            // Rebuild ID3 indexes using the artist/album IDs assigned during the initial import.
            TestCaseUtils.execScan(mediaScannerService);
            for (String endpoint : List.of("search", "search2", "search3")) {
                String result = endpoint.equals("search") ? "searchResult" : "searchResult" + endpoint.charAt(6);
                String items = endpoint.equals("search") ? "match" : "song";
                String query = endpoint.equals("search") ? "any=Reed%20Neck%20Reel" : "query=Reed%20Neck%20Reel";
                for (String suffix : List.of("", ".view")) {
                    String path = "/rest/" + endpoint + suffix;
                    // Subsonic credentials in URL parameters, without HTTP Basic auth.
                    HttpResponse<String> json = get(path + "?" + credentials() + "&f=json&" + query, false);
                    JsonNode song = subsonic(json).path(result).path(items).path(0);
                    assertEquals(json.body(), "Reed Neck Reel", song.path("title").asText());
                    assertEquals("#A58866", song.path("accentColor").asText());
                    assertTrue(json.headers().firstValue("content-type").orElseThrow().startsWith("application/json"));

                    HttpResponse<String> xml = get(path + "?c=ktor-test&" + query, true);
                    assertEquals(xml.body(), 200, xml.statusCode());
                    assertTrue(xml.body(), xml.body().contains("<" + result));
                    assertTrue(xml.body(), xml.body().contains("title=\"Reed Neck Reel\""));
                    String xmlType = xml.headers().firstValue("content-type").orElseThrow();
                    assertTrue(xmlType, xmlType.startsWith("text/xml"));
                }

                // The legacy .view API accepts POST forms without a CSRF token.
                String form = credentials() + "&f=json&" + query;
                JsonNode posted = subsonic(post("/rest/" + endpoint + ".view", form));
                assertEquals("Reed Neck Reel", posted.path(result).path(items).path(0).path("title").asText());
                // Keep the existing CSRF protection for POST without .view.
                assertEquals(403, post("/rest/" + endpoint, form).statusCode());

                String pagination = endpoint.equals("search") ? "count=0" : "songCount=0";
                JsonNode empty = subsonic(get("/rest/" + endpoint + "?" + credentials()
                        + "&f=json&" + query + "&" + pagination, false));
                assertEquals(empty.toString(), 0, empty.path(result).path(items).size());
                String offset = endpoint.equals("search") ? "offset=1" : "songOffset=1";
                JsonNode skipped = subsonic(get("/rest/" + endpoint + "?" + credentials()
                        + "&f=json&" + query + "&" + offset, false));
                assertEquals(skipped.toString(), 0, skipped.path(result).path(items).size());
            }

            for (String endpoint : List.of("search2", "search3")) {
                String path = "/rest/" + endpoint + "?" + credentials() + "&f=json&query=Reed%20Neck%20Reel";
                String result = "searchResult" + endpoint.charAt(6);
                assertEquals(1, subsonic(get(path + "&musicFolderId=" + folders.get(0).getId(), false))
                        .path(result).path("song").size());
                assertEquals(0, subsonic(get(path + "&musicFolderId=" + folders.get(1).getId(), false))
                        .path(result).path("song").size());
                // Malformed pagination used to fall back to its default value.
                assertEquals(1, subsonic(get(path + "&songCount=invalid", false)).path(result).path("song").size());
                JsonNode error = responseBody(get(path + "&musicFolderId=invalid", false));
                assertEquals("failed", error.path("status").asText());
                assertEquals(10, error.path("error").path("code").asInt());
            }

            HttpResponse<String> jsonp = get("/rest/search3.view?" + credentials()
                    + "&f=jsonp&callback=client.search&query=Reed%20Neck%20Reel", false);
            assertEquals(200, jsonp.statusCode());
            assertTrue(jsonp.body(), jsonp.body().startsWith("client.search("));
            assertTrue(jsonp.body(), jsonp.body().endsWith(");"));
            assertTrue(jsonp.headers().firstValue("content-type").orElseThrow().startsWith("application/javascript"));

            // HTTP Basic auth also supports clients that omit the Subsonic client/version parameters.
            subsonic(get("/rest/search3?f=json&query=Reed%20Neck%20Reel", true));

            JsonNode encoded = subsonic(get("/rest/search3?" + credentials() + "&f=json&queryUtf8Hex="
                    + StringUtil.utf8HexEncode("Reed Neck Reel"), false));
            assertEquals("Reed Neck Reel", encoded.path("searchResult3").path("song").path(0).path("title").asText());
            // Preserve the decoding filter's preference for a regular parameter if both forms exist.
            JsonNode preferred = subsonic(get("/rest/search3?" + credentials()
                    + "&f=json&query=Reed%20Neck%20Reel&queryUtf8Hex=" + StringUtil.utf8HexEncode("missing-song"), false));
            assertEquals(1, preferred.path("searchResult3").path("song").size());

            for (String endpoint : List.of("search2", "search3")) {
                String result = "searchResult" + endpoint.charAt(6);
                JsonNode artists = subsonic(get("/rest/" + endpoint + "?" + credentials()
                        + "&f=json&query=Horsepower", false)).path(result).path("artist");
                assertTrue(endpoint + ": " + artists, artists.size() > 0);
                JsonNode albums = subsonic(get("/rest/" + endpoint + "?" + credentials()
                        + "&f=json&query=Sackcloth", false)).path(result).path("album");
                assertTrue(endpoint + ": " + albums, albums.size() > 0);
            }
        } finally {
            ScanningTestUtils.after(cleanupId, mediaFolderService);
            mediaFolderService.expunge();
        }
    }

    @Test
    public void searchRoutesKeepAuthenticationAndProtocolChecks() throws Exception {
        for (String endpoint : List.of("search", "search2", "search3")) {
            for (String suffix : List.of("", ".view")) {
                String path = "/rest/" + endpoint + suffix + "?f=json";
                JsonNode missing = responseBody(get(path, false));
                assertEquals("failed", missing.path("status").asText());
                assertEquals(10, missing.path("error").path("code").asInt());
                JsonNode wrong = responseBody(get(path + "&v=" + TestCaseUtils.restApiVersion()
                        + "&c=ktor-test&u=admin&p=wrong", false));
                assertEquals(40, wrong.path("error").path("code").asInt());
                JsonNode incompatible = responseBody(get(path + "&v=999.0.0&c=ktor-test&u=admin&p=admin", false));
                assertEquals(30, incompatible.path("error").path("code").asInt());
            }
        }
    }

    private String credentials() {
        return "v=" + URLEncoder.encode(TestCaseUtils.restApiVersion(), StandardCharsets.UTF_8)
                + "&c=ktor-test&u=admin&p=admin";
    }

    private JsonNode subsonic(HttpResponse<String> response) throws Exception {
        JsonNode body = responseBody(response);
        assertEquals(response.body(), "ok", body.path("status").asText());
        assertEquals(TestCaseUtils.restApiVersion(), body.path("version").asText());
        return body;
    }

    private JsonNode responseBody(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return new ObjectMapper().readTree(response.body()).path("subsonic-response");
    }

    private HttpResponse<String> post(String path, String form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/airsonic" + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, boolean authenticated) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/airsonic" + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "text/html");
        if (authenticated) {
            String credentials = Base64.getEncoder()
                    .encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8));
            request.header("Authorization", "Basic " + credentials);
        }
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
