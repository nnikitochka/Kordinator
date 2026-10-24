package org.airsonic.player.ktor;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

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
