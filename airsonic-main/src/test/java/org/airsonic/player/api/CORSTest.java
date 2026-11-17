package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.util.HomeRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class CORSTest {

    private static final String ORIGIN = "https://example.com";
    private static final String CLIENT_NAME = "airsonic";
    private static final String API_VERSION = TestCaseUtils.restApiVersion();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @ClassRule
    public static final HomeRule classRule = new HomeRule();

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    public void corsHeadersShouldBeAddedToSuccessResponses() throws Exception {
        HttpResponse<String> response = get("/rest/ping?" + credentials("admin", "admin"));

        assertEquals(200, response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        JsonNode envelope = json.readTree(response.body()).path("subsonic-response");
        assertEquals("ok", envelope.path("status").asText());
    }

    @Test
    public void corsHeadersShouldBeAddedToErrorResponses() throws Exception {
        HttpResponse<String> response = get("/rest/ping?" + credentials("admin", "incorrect-password"));

        assertEquals(200, response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        JsonNode envelope = json.readTree(response.body()).path("subsonic-response");
        assertEquals("failed", envelope.path("status").asText());
        assertNotNull(envelope.get("error"));
        assertEquals(40, envelope.path("error").path("code").asInt());
        assertTrue(envelope.path("error").path("message").asText(),
                !envelope.path("error").path("message").asText().isEmpty());
    }

    @Test
    public void testOptionRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/rest/ping"))
                .timeout(REQUEST_TIMEOUT)
                .header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }

    @Test
    public void corsPreflightMatchesEveryConfiguredPathFamily() throws Exception {
        for (String path : List.of(
                "/rest/ping",
                "/stream/test",
                "/hls/test",
                "/captions/test",
                "/ext/stream/test",
                "/ext/hls/test",
                "/ext/captions/test")) {
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Origin", ORIGIN)
                    .header("Access-Control-Request-Method", "GET")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals("CORS preflight for " + path + " headers=" + response.headers().map() + " body=" + response.body(),
                    200, response.statusCode());
            assertEquals("CORS origin for " + path, "*",
                    response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
            assertTrue("CORS allowed methods for " + path,
                    response.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("GET"));
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Origin", ORIGIN)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + "/airsonic" + path);
    }

    private static String credentials(String username, String password) {
        return "v=" + API_VERSION + "&c=" + CLIENT_NAME + "&u=" + username + "&p=" + password + "&f=json";
    }
}
