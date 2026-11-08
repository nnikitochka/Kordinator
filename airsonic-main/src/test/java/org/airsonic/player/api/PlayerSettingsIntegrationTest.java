package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.SettingsService;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PlayerSettingsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private org.airsonic.player.service.PlayerService players;
    @Autowired private org.airsonic.player.service.TranscodingService transcoding;
    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void managePlayersThroughKtor() throws Exception {
        User owner = new User("player-owner", null);
        owner.setRoles(Set.of(User.Role.SETTINGS, User.Role.STREAM));
        security.createUser(owner, "secret", "test");
        User outsider = new User("player-outsider", null);
        outsider.setRoles(Set.of(User.Role.STREAM));
        security.createUser(outsider, "secret", "test");
        org.airsonic.player.domain.Player own = new org.airsonic.player.domain.Player();
        own.setUsername("player-owner"); own.setName("Own <&> player"); players.createPlayer(own);
        org.airsonic.player.domain.Player other = new org.airsonic.player.domain.Player();
        other.setUsername("admin"); other.setName("Other player"); players.createPlayer(other);
        assertFalse(context.containsBean("playerSettingsController"));
        JsonNode visible = api("getPlayerSettings", "player-owner", "secret", null).path("playerSettings");
        for (JsonNode item : visible.path("player")) assertNotEquals(other.getId().intValue(), item.path("id").asInt());
        assertEquals(own.getId().intValue(), visible.path("selectedPlayer").path("id").asInt());
        error(api("getPlayerSettings&id=" + other.getId(), "player-owner", "secret", null), 50);
        error(api("getPlayerSettings", "player-outsider", "secret", null), 50);
        error(api("getPlayerSettings&id=2147483647", "admin", "admin", null), 70);
        error(api("getPlayerSettings&id=bad", "admin", "admin", null), 0);
        HttpClient browser = login("player-owner", "secret");
        for (String page : new String[]{"playerSettings", "playerSettings.view"}) {
            HttpResponse<String> response = get(browser, "/" + page + "?delete=" + own.getId());
            assertEquals(response.body(), 200, response.statusCode());
            assertTrue(response.body().contains("initPlayerSettings"));
            assertFalse(response.body().contains("<form:"));
            assertNotNull(players.getPlayerById(own.getId()));
        }
        assertEquals(403, get(login("player-outsider", "secret"), "/playerSettings.view").statusCode());
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body());
        assertTrue(token.find());
        String csrf = "&_csrf=" + encode(token.group(1));
        String base = "&id=" + own.getId() + "&name=" + encode(" Saved <&> ")
                + "&technologyName=EXTERNAL&transcodeSchemeName=MAX_128&dynamicIp=false&autoControlEnabled=false&m3uBomEnabled=false&activeTranscodingId=";
        for (String operation : new String[]{"savePlayerSettings", "clonePlayer", "deletePlayer"}) {
            assertEquals(405, raw(operation, null).statusCode());
            assertEquals(403, post(browser, "/rest/" + operation, "f=json" + base).statusCode());
            error(website(browser, operation, "id=" + other.getId() + base + csrf), 50);
        }
        assertEquals(405, post(browser, "/playerSettings.view", csrf.substring(1)).statusCode());
        players.getPlayerById(own.getId()).getPlayQueue().setStatus(org.airsonic.player.domain.PlayQueue.Status.PLAYING);
        for (String invalid : new String[]{"technologyName=BAD", "transcodeSchemeName=BAD", "dynamicIp=yes", "autoControlEnabled=1", "m3uBomEnabled=yes", "activeTranscodingId=bad", "activeTranscodingId=2147483647"}) {
            error(website(browser, "savePlayerSettings", invalid + base + csrf), 0);
            assertEquals("Own <&> player", players.getPlayerById(own.getId()).getName());
            assertEquals(org.airsonic.player.domain.PlayerTechnology.WEB, players.getPlayerById(own.getId()).getTechnology());
        }
        assertEquals(org.airsonic.player.domain.PlayQueue.Status.PLAYING, players.getPlayerById(own.getId()).getPlayQueue().getStatus());
        error(website(browser, "savePlayerSettings", "id=" + own.getId() + csrf), 0);
        ok(website(browser, "savePlayerSettings", base + csrf));
        org.airsonic.player.domain.Player saved = players.getPlayerById(own.getId());
        assertEquals(org.airsonic.player.domain.PlayQueue.Status.STOPPED, saved.getPlayQueue().getStatus());
        assertEquals("Saved <&>", saved.getName());
        assertEquals(org.airsonic.player.domain.PlayerTechnology.EXTERNAL, saved.getTechnology());
        assertEquals(org.airsonic.player.domain.TranscodeScheme.MAX_128, saved.getTranscodeScheme());
        assertFalse(saved.getDynamicIp()); assertFalse(saved.getAutoControlEnabled()); assertFalse(saved.getM3uBomEnabled());
        assertTrue(transcoding.getTranscodingsForPlayer(saved).isEmpty());
        saved.getPlayQueue().setStatus(org.airsonic.player.domain.PlayQueue.Status.PLAYING);
        int profile = transcoding.getAllTranscodings().getFirst().getId();
        ok(website(browser, "savePlayerSettings", "activeTranscodingId=" + profile + base + csrf));
        assertEquals(org.airsonic.player.domain.PlayQueue.Status.PLAYING, saved.getPlayQueue().getStatus());
        assertEquals(1, transcoding.getTranscodingsForPlayer(saved).size());
        assertEquals(Integer.valueOf(profile), transcoding.getTranscodingsForPlayer(saved).getFirst().getId());
        String xml = get(client, "/rest/getPlayerSettings?u=admin&p=admin&v=" + TestCaseUtils.restApiVersion() + "&c=test&id=" + own.getId()).body();
        org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        assertEquals("Saved <&>", ((org.w3c.dom.Element) document.getElementsByTagName("selectedPlayer").item(0)).getAttribute("name"));
        JsonNode cloned = website(browser, "clonePlayer", "id=" + own.getId() + csrf); ok(cloned);
        int cloneId = cloned.path("playerSettings").path("selectedPlayer").path("id").asInt();
        assertNotEquals(own.getId().intValue(), cloneId);
        assertEquals("player-owner", players.getPlayerById(cloneId).getUsername());
        assertEquals("Saved <&> (copy)", players.getPlayerById(cloneId).getName());
        assertEquals("Saved <&>", players.getPlayerById(own.getId()).getName());
        ok(website(browser, "deletePlayer", "id=" + cloneId + csrf));
        assertNull(players.getPlayerById(cloneId));
        error(website(browser, "deletePlayer", "id=" + cloneId + csrf), 70);
        HttpClient admin = login("admin", "admin");
        Matcher adminToken = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(admin, "/top.view").body());
        assertTrue(adminToken.find());
        ok(website(admin, "savePlayerSettings", "id=" + own.getId() + "&name=Admin-edited" + base + "&_csrf=" + encode(adminToken.group(1))));
        assertEquals("Admin-edited", players.getPlayerById(own.getId()).getName());
        ok(website(browser, "deletePlayer", "id=" + own.getId() + csrf));
        assertNull(players.getPlayerById(own.getId()));
    }

    private HttpResponse<String> raw(String operation, String body) throws Exception {
        String path = "/rest/" + operation + "?u=admin&p=admin&v=" + TestCaseUtils.restApiVersion() + "&c=test&f=json";
        return body == null ? get(client, path) : post(client, path, body);
    }
    private JsonNode api(String operation, String user, String password, String body) throws Exception {
        String path = "/rest/" + operation.split("&", 2)[0] + "?" + (operation.contains("&") ? operation.split("&", 2)[1] + "&" : "") + "u=" + user + "&p=" + password + "&v=" + TestCaseUtils.restApiVersion() + "&c=test&f=json";
        HttpResponse<String> response = body == null ? get(client, path) : post(client, path, body);
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
    private JsonNode website(HttpClient browser, String operation, String body) throws Exception {
        HttpResponse<String> response = post(browser, "/rest/" + operation, "f=json&c=airsonic-web&" + body);
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
    private HttpClient login(String username, String password) throws Exception {
        HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(get(browser, "/login").body());
        assertTrue(token.find());
        assertEquals(302, post(browser, "/login", "j_username=" + username + "&j_password=" + password + "&_csrf=" + encode(token.group(1))).statusCode());
        return browser;
    }
    private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient browser, String path, String form) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(base() + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String base() { return "http://localhost:" + port + "/airsonic"; }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private void ok(JsonNode body) { assertEquals(body.toString(), "ok", body.path("status").asText()); }
    private void error(JsonNode body, int code) {
        assertEquals(body.toString(), "failed", body.path("status").asText());
        assertEquals(body.toString(), code, body.path("error").path("code").asInt());
    }
}
