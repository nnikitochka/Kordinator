package org.airsonic.player.ktor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.api.ScanningTestUtils;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
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
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
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
    public void missingKtorRouteReturnsNotFound() throws Exception {
        HttpResponse<String> response = get("/internal/ktor/missing", true);

        assertEquals(response.body(), 404, response.statusCode());
    }

    @Test
    public void existingSubsonicRoutesStillWork() throws Exception {
        for (String path : List.of("/rest/ping", "/rest/ping.view")) {
            HttpResponse<String> response = get(path + "?v=1.15.0&c=ktor-test&f=json", true);

            assertEquals(path + ": " + response.body(), 200, response.statusCode());
            assertNotNull(response.body());
            assertEquals("ok", new ObjectMapper().readTree(response.body())
                    .path("subsonic-response").path("status").asText());
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
