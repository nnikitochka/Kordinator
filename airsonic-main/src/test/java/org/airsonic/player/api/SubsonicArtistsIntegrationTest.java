package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.util.HomeRule;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.xml.sax.InputSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The same HTTP contract is checked before and after the controller's migration to Ktor. */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SubsonicArtistsIntegrationTest {
    @ClassRule
    public static final HomeRule homeRule = new HomeRule();

    @LocalServerPort
    private int port;

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private ArtistDao artistDao;

    private List<MusicFolder> testFolders;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Before
    public void createMusicFolders() {
        // Real directories, but no scan: artists come from the database directly.
        // createMusicFolder assigns database ids, so the created instances carry the ids to use below.
        testFolders = MusicFolderTestData.getTestMusicFolders();
        testFolders.forEach(mediaFolderService::createMusicFolder);
    }

    @Test
    public void preservesArtistsContract() throws Exception {
        int folderId = testFolders.get(0).getId();
        try {
            // No artists yet: the index must be empty.
            JsonNode empty = body(send("/rest/getArtists?" + credentials() + "&f=json", null, false));
            assertEquals("ok", empty.path("status").asText());
            assertEquals(0, empty.path("artists").path("index").size());

            Artist alpha = insertArtist("Alpha", folderId);
            Artist bravo = insertArtist("Bravo", folderId);

            for (String suffix : List.of("", ".view")) {
                String path = "/rest/getArtists" + suffix;
                HttpResponse<String> json = send(path + "?" + credentials() + "&f=json", null, false);
                assertTrue(json.headers().firstValue("content-type").orElseThrow().startsWith("application/json"));
                JsonNode response = body(json);
                assertEquals("ok", response.path("status").asText());
                assertEquals(TestCaseUtils.restApiVersion(), response.path("version").asText());
                JsonNode artists = response.path("artists");
                assertTrue(!artists.path("ignoredArticles").asText().isEmpty());
                JsonNode indexes = artists.path("index");
                assertEquals(2, indexes.size());
                assertEquals("A", indexes.path(0).path("name").asText());
                assertEquals("B", indexes.path(1).path("name").asText());
                assertEquals("Alpha", indexes.path(0).path("artist").path(0).path("name").asText());
                assertEquals(String.valueOf(alpha.getId()), indexes.path(0).path("artist").path(0).path("id").asText());
                assertEquals(3, indexes.path(0).path("artist").path(0).path("albumCount").asInt());
                assertEquals("Bravo", indexes.path(1).path("artist").path(0).path("name").asText());

                HttpResponse<String> xml = send(path, null, true);
                assertEquals(xml.body(), 200, xml.statusCode());
                assertTrue(xml.headers().firstValue("content-type").orElseThrow().startsWith("text/xml"));
                var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(new InputSource(new StringReader(xml.body())));
                assertEquals("ok", document.getDocumentElement().getAttribute("status"));
                assertEquals(1, document.getElementsByTagName("artists").getLength());
                assertEquals(2, document.getElementsByTagName("index").getLength());
                assertEquals("A", document.getElementsByTagName("index").item(0).getAttributes()
                        .getNamedItem("name").getNodeValue());
                var artistNodes = document.getElementsByTagName("artist");
                assertEquals(2, artistNodes.getLength());
                assertEquals("Alpha", artistNodes.item(0).getAttributes().getNamedItem("name").getNodeValue());
                assertEquals(String.valueOf(alpha.getId()), artistNodes.item(0).getAttributes()
                        .getNamedItem("id").getNodeValue());
                assertEquals("3", artistNodes.item(0).getAttributes().getNamedItem("albumCount").getNodeValue());
                assertEquals("Bravo", artistNodes.item(1).getAttributes().getNamedItem("name").getNodeValue());
            }

            HttpResponse<String> jsonp = send("/rest/getArtists?" + credentials() + "&f=jsonp&callback=client.artists",
                    null, false);
            assertEquals(200, jsonp.statusCode());
            assertTrue(jsonp.headers().firstValue("content-type").orElseThrow().startsWith("application/javascript"));
            assertTrue(jsonp.body(), jsonp.body().startsWith("client.artists("));
            assertTrue(jsonp.body(), jsonp.body().endsWith(");"));
            assertEquals("Alpha", mapper.readTree(
                            jsonp.body().substring("client.artists(".length(), jsonp.body().length() - 2))
                    .path("subsonic-response").path("artists").path("index").path(0).path("artist").path(0)
                    .path("name").asText());

            // The legacy .view API accepts POST forms without a CSRF token.
            JsonNode posted = body(send("/rest/getArtists.view", credentials() + "&f=json", false));
            assertEquals("Alpha", posted.path("artists").path("index").path(0).path("artist").path(0)
                    .path("name").asText());
            // Keep the existing CSRF protection for POST without .view.
            assertEquals(403, send("/rest/getArtists", credentials() + "&f=json", false).statusCode());

            for (String suffix : List.of("", ".view")) {
                String path = "/rest/getArtists" + suffix + "?f=json";
                assertError(send(path, null, false), 10);
                assertError(send(path + "&v=" + TestCaseUtils.restApiVersion() + "&c=test&u=admin&p=wrong", null, false), 40);
                assertError(send(path + "&v=999.0.0&c=test&u=admin&p=admin", null, false), 30);
                // Parameter authentication.
                JsonNode authed = body(send(path + "&v=" + TestCaseUtils.restApiVersion() + "&c=test&u=admin&p=admin",
                        null, false));
                assertEquals("ok", authed.path("status").asText());
                // Basic authentication.
                JsonNode basic = body(send(path + "&v=" + TestCaseUtils.restApiVersion() + "&c=test", null,
                        "admin:admin"));
                assertEquals("ok", basic.path("status").asText());
                assertEquals(401, send(path + "&v=" + TestCaseUtils.restApiVersion() + "&c=test", null,
                        "admin:wrongpassword").statusCode());
            }
        } finally {
            testFolders.forEach(folder -> mediaFolderService.deleteMusicFolder(folder.getId()));
        }
    }

    private Artist insertArtist(String name, int folderId) {
        Artist artist = new Artist(0, name, 3, Instant.now(), true, folderId);
        artistDao.createOrUpdateArtist(artist);
        return artist;
    }

    private void assertError(HttpResponse<String> response, int code) throws Exception {
        JsonNode result = body(response);
        assertEquals("failed", result.path("status").asText());
        assertEquals(code, result.path("error").path("code").asInt());
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }

    private String credentials() {
        return "v=" + TestCaseUtils.restApiVersion() + "&c=artists-test&u=admin&p=admin";
    }

    private HttpResponse<String> send(String path, String form, boolean basicAuth) throws Exception {
        return send(path, form, basicAuth ? "admin:admin" : null);
    }

    private HttpResponse<String> send(String path, String form, String basicUserPass) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/airsonic" + path))
                .timeout(Duration.ofSeconds(10));
        if (basicUserPass != null) {
            request.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(basicUserPass.getBytes(StandardCharsets.UTF_8)));
        }
        if (form != null) {
            request.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
