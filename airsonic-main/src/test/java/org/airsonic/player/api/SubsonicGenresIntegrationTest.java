package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.Genre;
import org.airsonic.player.util.HomeRule;
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
public class SubsonicGenresIntegrationTest {
    @ClassRule
    public static final HomeRule homeRule = new HomeRule();

    @LocalServerPort
    private int port;

    @Autowired
    private MediaFileDao mediaFileDao;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Before
    public void prepareGenres() {
        // No scanner or shared media paths: both counts and ordering come from the real database.
        mediaFileDao.updateGenres(List.of(new Genre("Ambient", 7, 1), new Genre("Рок & Jazz", 3, 2)));
    }

    @Test
    public void preservesFormatsPathsAndPostBehavior() throws Exception {
        for (String suffix : List.of("", ".view")) {
            String path = "/rest/getGenres" + suffix;
            HttpResponse<String> json = send(path + "?" + credentials() + "&f=json", null, false);
            assertTrue(json.headers().firstValue("content-type").orElseThrow().startsWith("application/json"));
            assertGenres(body(json));

            // Basic auth with no c/v parameters must keep working too.
            HttpResponse<String> xml = send(path, null, true);
            assertEquals(xml.body(), 200, xml.statusCode());
            assertTrue(xml.headers().firstValue("content-type").orElseThrow().startsWith("text/xml"));
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml.body())));
            assertEquals("ok", document.getDocumentElement().getAttribute("status"));
            var genres = document.getElementsByTagName("genre");
            assertEquals(2, genres.getLength());
            assertEquals("Рок & Jazz", genres.item(0).getTextContent());
            assertEquals("3", genres.item(0).getAttributes().getNamedItem("songCount").getNodeValue());
            assertEquals("2", genres.item(0).getAttributes().getNamedItem("albumCount").getNodeValue());

            HttpResponse<String> jsonp = send(path + "?" + credentials() + "&f=jsonp&callback=client.genres", null, false);
            assertEquals(200, jsonp.statusCode());
            assertTrue(jsonp.headers().firstValue("content-type").orElseThrow().startsWith("application/javascript"));
            assertTrue(jsonp.body(), jsonp.body().startsWith("client.genres("));
            assertTrue(jsonp.body(), jsonp.body().endsWith(");"));
            assertGenres(mapper.readTree(jsonp.body().substring("client.genres(".length(), jsonp.body().length() - 2))
                    .path("subsonic-response"));
        }

        assertGenres(body(send("/rest/getGenres.view", credentials() + "&f=json", false)));
        assertEquals(403, send("/rest/getGenres", credentials() + "&f=json", false).statusCode());
    }

    @Test
    public void preservesEmptyLibraryResponse() throws Exception {
        mediaFileDao.updateGenres(List.of());
        JsonNode response = body(send("/rest/getGenres?" + credentials() + "&f=json", null, false));
        assertEquals("ok", response.path("status").asText());
        assertEquals(0, response.path("genres").path("genre").size());
        HttpResponse<String> xml = send("/rest/getGenres.view", null, true);
        assertEquals(200, xml.statusCode());
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml.body())));
        assertEquals(1, document.getElementsByTagName("genres").getLength());
        assertEquals(0, document.getElementsByTagName("genre").getLength());
    }

    @Test
    public void preservesAuthenticationAndProtocolErrors() throws Exception {
        for (String suffix : List.of("", ".view")) {
            String path = "/rest/getGenres" + suffix + "?f=json";
            assertError(send(path, null, false), 10);
            assertError(send(path + "&v=" + TestCaseUtils.restApiVersion() + "&c=test&u=admin&p=wrong", null, false), 40);
            assertError(send(path + "&v=999.0.0&c=test&u=admin&p=admin", null, false), 30);
        }
    }

    private void assertGenres(JsonNode response) {
        assertEquals("ok", response.path("status").asText());
        assertEquals(TestCaseUtils.restApiVersion(), response.path("version").asText());
        JsonNode genres = response.path("genres").path("genre");
        assertEquals(2, genres.size());
        assertEquals("Рок & Jazz", genres.path(0).path("value").asText());
        assertEquals(3, genres.path(0).path("songCount").asInt());
        assertEquals(2, genres.path(0).path("albumCount").asInt());
        assertEquals("Ambient", genres.path(1).path("value").asText());
        assertEquals(7, genres.path(1).path("songCount").asInt());
        assertEquals(1, genres.path(1).path("albumCount").asInt());
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
        return "v=" + TestCaseUtils.restApiVersion() + "&c=genres-test&u=admin&p=admin";
    }

    private HttpResponse<String> send(String path, String form, boolean basicAuth) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/airsonic" + path))
                .timeout(Duration.ofSeconds(10));
        if (basicAuth) {
            request.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8)));
        }
        if (form != null) {
            request.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
