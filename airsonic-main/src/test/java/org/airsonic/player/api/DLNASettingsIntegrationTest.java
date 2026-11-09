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
public class DLNASettingsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private org.airsonic.player.service.UPnPService upnp;

    @Test public void administerDlnaSettingsViaKtor() throws Exception {
        settings.setDlnaEnabled(false);
        settings.setDlnaServerName("Initial DLNA");
        settings.setDlnaBaseLANURL(null);
        settings.save();
        org.mockito.Mockito.clearInvocations(upnp);
        User member = new User("dlna-member", null);
        member.setRoles(Set.of(User.Role.SETTINGS, User.Role.STREAM));
        security.createUser(member, "secret", "test");
        assertFalse(context.containsBean("DLNASettingsController"));
        JsonNode initial = read("admin", "admin");
        assertFalse(initial.path("dlnaEnabled").asBoolean());
        assertEquals("Initial DLNA", initial.path("dlnaServerName").asText());
        assertEquals("", initial.path("dlnaBaseLANURL").asText());
        error(api("getDlnaSettings", "dlna-member", "secret", null), 50);
        assertEquals(405, raw("saveDlnaSettings", null).statusCode());
        HttpClient browser = login("admin", "admin");
        for (String page : new String[]{"dlnaSettings", "dlnaSettings.view"}) {
            HttpResponse<String> response = get(browser, "/" + page);
            assertEquals(response.body(), 200, response.statusCode());
            assertTrue(response.body().contains("initDlnaSettings"));
            assertFalse(response.body().contains("${model"));
            assertFalse(response.body().contains("Initial DLNA"));
            HttpResponse<String> head = browser.send(HttpRequest.newBuilder(URI.create(base() + "/" + page))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, head.statusCode());
            assertEquals("", head.body());
        }
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body());
        assertTrue(token.find());
        String csrf = "&_csrf=" + encode(token.group(1));
        assertEquals(405, post(browser, "/rest/getDlnaSettings", "f=json" + csrf).statusCode());
        String base = payload(initial);
        assertEquals(403, post(browser, "/rest/saveDlnaSettings", "f=json" + base).statusCode());
        assertEquals(405, post(browser, "/dlnaSettings.view", "_csrf=" + encode(token.group(1))).statusCode());
        HttpClient memberBrowser = login("dlna-member", "secret");
        assertEquals(403, get(memberBrowser, "/dlnaSettings.view").statusCode());
        Matcher memberToken = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(memberBrowser, "/top.view").body());
        assertTrue(memberToken.find());
        error(website(memberBrowser, "saveDlnaSettings", base + "&_csrf=" + encode(memberToken.group(1))), 50);
        for (String invalid : new String[]{"", "dlnaEnabled=yes&dlnaServerName=SHOULD_NOT_SAVE&dlnaBaseLANURL=",
                "dlnaEnabled=true&dlnaServerName=SHOULD_NOT_SAVE", "dlnaEnabled=true&dlnaBaseLANURL=",
                "dlnaServerName=SHOULD_NOT_SAVE&dlnaBaseLANURL="}) {
            error(website(browser, "saveDlnaSettings", invalid + csrf), 0);
            assertEquals(initial, read("admin", "admin"));
        }
        org.mockito.Mockito.verifyNoInteractions(upnp);
        java.util.List<String> lifecycle = new java.util.ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            lifecycle.add(invocation.getArgument(0) + ":" + settings.getDlnaServerName() + ":" + settings.isDlnaEnabled());
            return null;
        }).when(upnp).setMediaServerEnabled(org.mockito.ArgumentMatchers.anyBoolean());
        String name = "DLNA <& Ktor>";
        String url = "http://192.168.1.10:4040/airsonic";
        ok(website(browser, "saveDlnaSettings", "dlnaEnabled=true&dlnaServerName=" + encode("  " + name + "  ")
                + "&dlnaBaseLANURL=" + encode("  " + url + "  ") + csrf));
        assertEquals(java.util.List.of("false:Initial DLNA:false", "true:" + name + ":true"), lifecycle);
        JsonNode saved = read("admin", "admin");
        assertEquals(name, saved.path("dlnaServerName").asText());
        assertEquals(url, saved.path("dlnaBaseLANURL").asText());
        assertTrue(saved.path("dlnaEnabled").asBoolean());
        assertEquals(name, settings.getDlnaServerName());
        assertEquals(url, settings.getDlnaBaseLANURL());
        String xml = get(client, "/rest/getDlnaSettings?u=admin&p=admin&v=" + TestCaseUtils.restApiVersion() + "&c=test").body();
        org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        assertEquals(name, ((org.w3c.dom.Element) document.getElementsByTagName("dlnaSettings").item(0)).getAttribute("dlnaServerName"));
        assertTrue(raw("getDlnaSettings", null).headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        lifecycle.clear();
        ok(website(browser, "saveDlnaSettings", "dlnaEnabled=false&dlnaServerName=" + encode(" ") + "&dlnaBaseLANURL=" + encode(" ") + csrf));
        assertEquals(java.util.List.of("false:" + name + ":true", "false:Airsonic:false"), lifecycle);
        assertEquals("Airsonic", settings.getDlnaServerName());
        assertNull(settings.getDlnaBaseLANURL());
        assertFalse(read("admin", "admin").path("dlnaEnabled").asBoolean());
    }

    private String payload(JsonNode data) {
        StringBuilder body = new StringBuilder();
        data.fields().forEachRemaining(entry -> body.append('&').append(entry.getKey()).append('=').append(encode(entry.getValue().asText())));
        return body.toString();
    }

    private JsonNode read(String user, String password) throws Exception {
        JsonNode body = api("getDlnaSettings", user, password, null);
        ok(body); return body.path("dlnaSettings");
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
