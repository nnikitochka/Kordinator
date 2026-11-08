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
public class TranscodingSettingsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    @Autowired private ApplicationContext context;
    @Autowired private org.airsonic.player.service.TranscodingService transcodings;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void administerTranscodingSettingsViaKtor() throws Exception {
        User member = new User("transcoding-member", null);
        member.setRoles(Set.of(User.Role.SETTINGS, User.Role.STREAM));
        security.createUser(member, "secret", "test");
        assertFalse(context.containsBean("transcodingSettingsController"));
        JsonNode initial = read("admin", "admin");
        assertTrue(initial.path("transcoding").isArray());
        error(api("getTranscodingSettings", "transcoding-member", "secret", null), 50);
        assertEquals(405, raw("saveTranscodingSettings", null).statusCode());
        HttpClient browser = login("admin", "admin");
        for (String page : new String[]{"transcodingSettings", "transcodingSettings.view"}) {
            HttpResponse<String> response = get(browser, "/" + page);
            assertEquals(response.body(), 200, response.statusCode());
            assertTrue(response.body().contains("initTranscodingSettings"));
        }
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body());
        assertTrue(token.find());
        String csrf = "&_csrf=" + encode(token.group(1));
        String base = payload(initial);
        assertEquals(403, post(browser, "/rest/saveTranscodingSettings", "f=json" + base).statusCode());
        assertEquals(405, post(browser, "/transcodingSettings.view", "_csrf=" + encode(token.group(1))).statusCode());
        HttpClient memberBrowser = login("transcoding-member", "secret");
        assertEquals(403, get(memberBrowser, "/transcodingSettings.view").statusCode());
        Matcher memberToken = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(memberBrowser, "/top.view").body());
        assertTrue(memberToken.find());
        error(website(memberBrowser, "saveTranscodingSettings", base + "&_csrf=" + encode(memberToken.group(1))), 50);
        int count = transcodings.getAllTranscodings().size();
        for (String invalid : new String[]{"name=partial", "transcodeEstimateTimePadding=bad", "transcodeEstimateBytePadding=-1",
                "transcodeEstimateTimePadding=9223372036854775808", "defaultActive=yes"}) {
            error(website(browser, "saveTranscodingSettings", invalid + base + csrf), 0);
            assertEquals(initial, read("admin", "admin"));
        }
        error(website(browser, "saveTranscodingSettings", csrf), 0);
        String create = "name=" + encode("<REST & Ktor>") + "&sourceFormats=flac&targetFormat=mp3&step1=" + encode("ffmpeg -i %s -f mp3 -")
                + "&step2=" + base + csrf;
        ok(website(browser, "saveTranscodingSettings", create));
        assertEquals(count + 1, transcodings.getAllTranscodings().size());
        org.airsonic.player.domain.Transcoding created = transcodings.getAllTranscodings().stream()
                .filter(t -> t.getName().equals("<REST & Ktor>")).findFirst().orElseThrow();
        created.setStep3("third-step"); transcodings.updateTranscoding(created);
        String suffix = "[" + created.getId() + "]";
        JsonNode withNew = read("admin", "admin");
        // An invalid new row must not delete an existing one or update global commands.
        error(website(browser, "saveTranscodingSettings", "delete" + suffix + "=true&name=incomplete&hlsCommand=SHOULD_NOT_SAVE" + payload(withNew) + csrf), 0);
        assertEquals(withNew, read("admin", "admin"));
        error(website(browser, "saveTranscodingSettings", "name" + suffix + "=" + payload(withNew) + csrf), 0);
        ok(website(browser, "saveTranscodingSettings", "name" + suffix + "=updated&step2" + suffix + "=second&hlsCommand=" + encode("quoted <&> %s")
                + "&transcodeEstimateTimePadding=123&transcodeEstimateBytePadding=456" + payload(withNew) + csrf));
        org.airsonic.player.domain.Transcoding updated = transcodings.getAllTranscodings().stream().filter(t -> t.getId().equals(created.getId())).findFirst().orElseThrow();
        assertEquals("updated", updated.getName()); assertEquals("second", updated.getStep2());
        assertEquals("third-step", updated.getStep3()); assertTrue(updated.isDefaultActive());
        assertEquals(123, settings.getTranscodeEstimateTimePadding()); assertEquals(456, settings.getTranscodeEstimateBytePadding());
        assertEquals("quoted <&> %s", settings.getHlsCommand());
        String xml = get(client, "/rest/getTranscodingSettings?u=admin&p=admin&v=" + TestCaseUtils.restApiVersion() + "&c=test").body();
        org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        assertEquals("quoted <&> %s", ((org.w3c.dom.Element) document.getElementsByTagName("transcodingSettings").item(0)).getAttribute("hlsCommand"));
        ok(website(browser, "saveTranscodingSettings", "delete" + suffix + "=true&transcodeEstimateTimePadding=&transcodeEstimateBytePadding=" + payload(read("admin", "admin")) + csrf));
        assertEquals(count, transcodings.getAllTranscodings().size());
        assertEquals(2000, settings.getTranscodeEstimateTimePadding()); assertEquals(0, settings.getTranscodeEstimateBytePadding());
    }

    private String payload(JsonNode data) {
        StringBuilder body = new StringBuilder();
        for (String name : new String[]{"downsampleCommand", "hlsCommand", "jukeboxCommand", "videoImageCommand", "subtitlesExtractionCommand", "transcodeEstimateTimePadding", "transcodeEstimateBytePadding"})
            body.append('&').append(name).append('=').append(encode(data.path(name).asText()));
        for (JsonNode profile : data.path("transcoding")) {
            String suffix = "[" + profile.path("id").asInt() + "]";
            for (String name : new String[]{"name", "sourceFormats", "targetFormat", "step1", "step2"})
                body.append('&').append(name).append(suffix).append('=').append(encode(profile.path(name).asText()));
            body.append("&delete").append(suffix).append("=false");
        }
        return body + "&name=&sourceFormats=&targetFormat=&step1=&step2=&defaultActive=true";
    }

    private JsonNode read(String user, String password) throws Exception {
        JsonNode body = api("getTranscodingSettings", user, password, null);
        ok(body); return body.path("transcodingSettings");
    }
    private HttpResponse<String> raw(String operation, String body) throws Exception {
        String path = "/rest/" + operation + "?u=admin&p=admin&v=" + TestCaseUtils.restApiVersion() + "&c=test&f=json";
        return body == null ? get(client, path) : post(client, path, body);
    }
    private JsonNode api(String operation, String user, String password, String body) throws Exception {
        String path = "/rest/" + operation + "?u=" + user + "&p=" + password + "&v=" + TestCaseUtils.restApiVersion() + "&c=test&f=json";
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
