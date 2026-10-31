package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.service.PlayerService;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.airsonic.player.util.HomeRule;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class LyricsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private PlayerService players;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void preservesLyricsContract() throws Exception {
        for (String suffix : List.of("", ".view")) {
            JsonNode lyrics = body(send(suffix, "f=json", false)).path("lyrics");
            assertFalse(lyrics.isMissingNode());
            assertTrue(lyrics.path("artist").asText("").isEmpty());
            assertTrue(lyrics.path("title").asText("").isEmpty());
            assertTrue(send(suffix, "f=xml", false).body().contains("<lyrics"));
            assertTrue(send(suffix, "f=jsonp&callback=client.test", false).body().startsWith("client.test("));
        }
        assertEquals("ok", body(send(".view", "f=json", true)).path("status").asText());
        assertEquals(403, send("", "f=json", true).statusCode());
        assertFalse(players.getPlayersForUserAndClientId("admin", "lyrics-test").isEmpty());
    }
    private HttpResponse<String> send(String suffix, String query, boolean post) throws Exception {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=lyrics-test&u=admin&p=admin&" + query;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/getLyrics" + suffix + (post ? "" : "?" + params)));
        if (post) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(params));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
}
