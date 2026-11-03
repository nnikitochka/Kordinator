package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.LyricsService;
import org.junit.Before;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.ApplicationContext;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
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
    @Autowired private ApplicationContext context;
    @MockitoBean private LyricsService lyricsService;

    @Before public void stubProvider() {
        when(lyricsService.getLyrics(nullable(String.class), nullable(String.class)))
                .thenReturn(new LyricsService.Result(null, null, null, false));
        when(lyricsService.getLyrics("Artist & Co", "Песня ?"))
                .thenReturn(new LyricsService.Result("Line one\n<script>literal</script>", "Artist & Co", "Песня ?", false));
        when(lyricsService.getLyrics("Unavailable", "Title"))
                .thenReturn(new LyricsService.Result(null, null, null, true));
    }

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
    @Test public void preservesFoundAndRetryContractsInBothFormats() throws Exception {
        String search = "&artist=Artist+%26+Co&title=" + URLEncoder.encode("Песня ?", StandardCharsets.UTF_8);
        for (String suffix : List.of("", ".view")) {
            JsonNode found = body(send(suffix, "f=json" + search, false)).path("lyrics");
            assertEquals("Artist & Co", found.path("artist").asText());
            assertEquals("Песня ?", found.path("title").asText());
            assertEquals("Line one\n<script>literal</script>", found.path("value").asText());
            assertFalse(found.has("tryLater"));
            String xml = send(suffix, "f=xml" + search, false).body();
            assertTrue(xml, xml.contains("Artist &amp; Co"));
            org.jdom2.Element root = org.airsonic.player.util.XMLUtil.createSAXBuilder()
                    .build(new java.io.StringReader(xml)).getRootElement();
            org.jdom2.Element lyric = root.getChild("lyrics", root.getNamespace());
            assertEquals("Line one\n<script>literal</script>", lyric.getText());
            assertTrue(lyric.getChildren().isEmpty());
            assertFalse(xml.contains("tryLater="));
            String unavailable = "&artist=Unavailable&title=Title";
            JsonNode legacy = body(send(suffix, "f=json" + unavailable, false));
            assertEquals("ok", legacy.path("status").asText());
            assertFalse(legacy.path("lyrics").has("tryLater"));
            JsonNode retry = body(send(suffix, "f=json&includeLookupStatus=true" + unavailable, false));
            assertTrue(retry.path("lyrics").path("tryLater").asBoolean());
            String retryXml = send(suffix, "f=xml&includeLookupStatus=true" + unavailable, false).body();
            assertTrue(retryXml, retryXml.contains("tryLater=\"true\""));
        }
    }

    @Test public void websiteUsesSessionRestAndEscapesInitialSearchValues() throws Exception {
        assertFalse(context.containsBean("lyricsWSController"));
        HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> loginPage = browser(browser, "/login", null, null);
        java.util.regex.Matcher token = java.util.regex.Pattern
                .compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(loginPage.body());
        assertTrue(token.find());
        assertEquals(302, browser(browser, "/login", "j_username=admin&j_password=admin&_csrf="
                + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8), null).statusCode());
        HttpResponse<String> page = browser(browser, "/lyrics.view?artist=%22%3E%3Cscript%3E&song=Title", null, null);
        assertEquals(page.body(), 200, page.statusCode());
        assertTrue(page.body(), page.body().contains("/airsonic/rest/getLyrics"));
        assertTrue(page.body(), page.body().contains("/airsonic/script/subsonic-lyrics.js"));
        assertFalse(page.body().contains("StompClient"));
        assertFalse(page.body().contains("value=\"\"><script>"));
        token = java.util.regex.Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(page.body());
        assertTrue(token.find());
        String csrf = token.group(1);
        String form = "f=json&c=airsonic-web&includeLookupStatus=true&artist=Artist+%26+Co&title="
                + URLEncoder.encode("Песня ?", StandardCharsets.UTF_8);
        JsonNode found = body(browser(browser, "/rest/getLyrics", form, csrf));
        assertEquals("ok", found.path("status").asText());
        assertEquals("Line one\n<script>literal</script>", found.path("lyrics").path("value").asText());
        assertTrue(found.path("lyrics").has("tryLater"));
        assertFalse(found.path("lyrics").path("tryLater").asBoolean());
        assertEquals(403, browser(browser, "/rest/getLyrics", form, null).statusCode());
        JsonNode retry = body(browser(browser, "/rest/getLyrics",
                "f=json&includeLookupStatus=true&artist=Unavailable&title=Title", csrf));
        assertTrue(retry.path("lyrics").path("tryLater").asBoolean());
        assertEquals(10, body(client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/airsonic/rest/getLyrics?f=json")).build(), HttpResponse.BodyHandlers.ofString()))
                .path("error").path("code").asInt());
    }

    private HttpResponse<String> browser(HttpClient browser, String path, String form, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path))
                .timeout(Duration.ofSeconds(10));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (form != null) request.header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
