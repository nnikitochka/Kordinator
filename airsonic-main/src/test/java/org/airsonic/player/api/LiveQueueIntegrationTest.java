package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.util.MusicFolderTestData;
import java.util.List;
import java.util.UUID;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.HomeRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class LiveQueueIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao files;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void liveQueueRequiresCsrfAndPlayerOwnership() throws Exception {
        HttpClient admin = client();
        String csrf = login(admin, "admin", "admin");
        HttpResponse<String> queuePage = send(admin, "/playQueue", null, null);
        assertEquals(queuePage.body(), 200, queuePage.statusCode());
        assertTrue(queuePage.body().contains("/airsonic/script/subsonic-live-queue.js"));
        assertFalse(queuePage.body().contains("/app/playqueues/"));
        JsonNode player = mapper.readTree(send(admin, "/playQueue/player", null, null).body());
        int id = player.path("id").asInt();
        assertTrue(id > 0);
        String params = "f=json&c=airsonic-web&playerId=" + id;
        JsonNode listed = body(send(admin, "/rest/livePlayQueue", "f=json&action=players", csrf)).path("livePlayQueue").path("player");
        assertTrue(listed.isArray());
        assertTrue(listed.toString().contains("\"id\":" + id));
        assertEquals(403, send(admin, "/rest/livePlayQueue", params + "&action=get", null).statusCode());
        JsonNode initial = body(send(admin, "/rest/livePlayQueue", params + "&action=get", csrf));
        assertEquals("ok", initial.path("status").asText());
        assertTrue(initial.has("livePlayQueue"));
        assertEquals(-1, initial.path("livePlayQueue").path("startPlayerAt").asInt());
        assertEquals("ok", body(send(admin, "/rest/livePlayQueue", params + "&action=toggleRepeat", csrf)).path("status").asText());
        assertEquals("ok", body(send(admin, "/rest/livePlayQueue", params + "&action=save&index=-1", csrf)).path("status").asText());
        assertEquals("failed", body(send(admin, "/rest/livePlayQueue", params + "&action=unknown", csrf)).path("status").asText());
        security.createUser(new User("other-listener", null), "secret", "test");
        HttpClient other = client();
        String otherCsrf = login(other, "other-listener", "secret");
        JsonNode otherPlayers = body(send(other, "/rest/livePlayQueue", "f=json&action=players&playerId=" + id, otherCsrf)).path("livePlayQueue").path("player");
        assertFalse(otherPlayers.toString().contains("\"id\":" + id));
        assertEquals(50, body(send(other, "/rest/livePlayQueue", params + "&action=clear", otherCsrf)).path("error").path("code").asInt());
    }
    @Test public void playsAndEditsRealMediaQueue() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            int id = files.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId()).getId();
            HttpClient browser = client();
            String csrf = login(browser, "admin", "admin");
            int player = mapper.readTree(send(browser, "/playQueue/player", null, null).body()).path("id").asInt();
            String prefix = "f=json&c=airsonic-web&playerId=" + player;
            JsonNode played = body(send(browser, "/rest/livePlayQueue", prefix + "&action=play%2Fmediafile&id=" + id, csrf));
            assertEquals(played.toString(), "ok", played.path("status").asText());
            JsonNode queue = played.path("livePlayQueue");
            assertEquals(0, queue.path("startPlayerAt").asInt(-2));
            assertTrue(queue.path("entry").size() > 0);
            assertTrue(queue.path("entry").get(0).path("streamUrl").asText().contains("stream?player=" + player));
            int originalSize = queue.path("entry").size();
            int songId = queue.path("entry").get(0).path("id").asInt();
            JsonNode added = body(send(browser, "/rest/livePlayQueue", prefix + "&action=add&id=" + songId, csrf));
            assertEquals("ok", added.path("status").asText());
            assertEquals(originalSize + 1, added.path("livePlayQueue").path("entry").size());
            assertEquals(originalSize + 1, body(send(browser, "/rest/livePlayQueue", prefix + "&action=get", csrf)).path("livePlayQueue").path("entry").size());
            assertEquals("failed", body(send(browser, "/rest/livePlayQueue", prefix + "&action=rearrange&id=0&id=0", csrf)).path("status").asText());
            assertEquals("ok", body(send(browser, "/rest/livePlayQueue", prefix + "&action=remove&id=0", csrf)).path("status").asText());
            assertEquals(originalSize, body(send(browser, "/rest/livePlayQueue", prefix + "&action=get", csrf)).path("livePlayQueue").path("entry").size());
            assertEquals("ok", body(send(browser, "/rest/livePlayQueue", prefix + "&action=save&index=0&offset=1234", csrf)).path("status").asText());
            JsonNode cleared = body(send(browser, "/rest/livePlayQueue", prefix + "&action=clear", csrf));
            assertEquals("ok", cleared.path("status").asText());
            assertEquals(0, cleared.path("livePlayQueue").path("entry").size());
            assertEquals(0, body(send(browser, "/rest/livePlayQueue", prefix + "&action=get", csrf)).path("livePlayQueue").path("entry").size());
            JsonNode restored = body(send(browser, "/rest/livePlayQueue", prefix + "&action=play%2Fsaved", csrf));
            assertEquals("ok", restored.path("status").asText());
            assertEquals(0, restored.path("livePlayQueue").path("startPlayerAt").asInt(-2));
            assertEquals(1234, restored.path("livePlayQueue").path("startPlayerAtPosition").asInt());
            assertEquals(originalSize, restored.path("livePlayQueue").path("entry").size());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }
    private HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build(); }
    private String login(HttpClient client, String name, String password) throws Exception {
        HttpResponse<String> page = send(client, "/login", null, null);
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
        assertTrue(token.find());
        assertEquals(302, send(client, "/login", "j_username=" + name + "&j_password=" + password + "&_csrf=" + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8), null).statusCode());
        page = send(client, "/top.view", null, null);
        token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(page.body());
        assertTrue(token.find());
        return token.group(1);
    }
    private HttpResponse<String> send(HttpClient client, String path, String form, String csrf) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path));
        if (csrf != null) b.header("X-CSRF-TOKEN", csrf);
        if (form != null) b.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form));
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
}
