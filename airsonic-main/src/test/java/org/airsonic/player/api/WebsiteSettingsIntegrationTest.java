package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.domain.AvatarScheme;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
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
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class WebsiteSettingsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private ApplicationContext context;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void updatesOnlyAuthenticatedUsersSettingsAndPreservesContracts() throws Exception {
        security.createUser(new User("settings-reader", null), "secret", "test");
        assertFalse(context.containsBean("topController"));
        UserSettings other = settings.getUserSettings("settings-reader");
        boolean otherSidebar = other.getShowSideBar();
        boolean otherList = other.getViewAsList();
        HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> login = send(browser, "/login", null, null);
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
        assertTrue(token.find());
        assertEquals(302, send(browser, "/login", "j_username=admin&j_password=admin&_csrf=" + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8), null).statusCode());
        HttpResponse<String> page = send(browser, "/top.view", null, null);
        assertEquals(page.body(), 200, page.statusCode());
        assertTrue(page.body().contains("/airsonic/script/subsonic-website-settings.js"));
        assertFalse(page.body().contains("/app/settings/"));
        assertFalse(page.body().contains("StompClient"));
        assertFalse(page.body().contains("connectionStatus"));
        assertTrue(page.body().contains("/airsonic/script/top-panel.js"));
        assertFalse(page.body().contains("model.user"));
        for (String path : new String[]{"/top", "/top.view"}) {
            assertEquals(401, send(HttpClient.newHttpClient(), path, null, null).statusCode());
            assertEquals(200, send(browser, path, null, null).statusCode());
            HttpResponse<String> head = browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, head.statusCode());
            assertEquals("", head.body());
        }
        HttpResponse<String> index = send(browser, "/index.view", null, null);
        assertEquals(index.body(), 200, index.statusCode());
        assertFalse(index.body().contains("StompClient"));
        assertFalse(index.body().contains("sockjs"));
        assertFalse(index.body().contains("stomp-"));
        assertEquals(404, send(browser, "/websocket/info", null, null).statusCode());
        assertFalse(context.containsBean("websocketConfiguration"));
        for (String path : new String[] {"/playlists.view", "/main.view"}) {
            HttpResponse<String> rendered = send(browser, path, null, null);
            assertEquals(rendered.body(), 200, rendered.statusCode());
            assertTrue(rendered.body().contains("/airsonic/script/subsonic-website-settings.js"));
            assertFalse(rendered.body().contains("/app/settings/"));
        }
        token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(page.body());
        assertTrue(token.find());
        String csrf = token.group(1);
        for (String path : new String[]{"/top", "/top.view"}) {
            assertEquals(403, send(browser, path, "", null).statusCode());
            assertEquals(405, send(browser, path, "", csrf).statusCode());
        }
        UserSettings admin = settings.getUserSettings("admin");
        boolean sidebar = !admin.getShowSideBar();
        boolean list = !admin.getViewAsList();
        String form = "f=json&c=airsonic-web&showSideBar=" + sidebar + "&viewAsList=" + list;
        assertEquals(403, send(browser, "/rest/updateWebsiteSettings", form, null).statusCode());
        assertEquals("ok", body(send(browser, "/rest/updateWebsiteSettings", form, csrf)).path("status").asText());
        assertEquals(sidebar, settings.getUserSettings("admin").getShowSideBar());
        assertEquals(list, settings.getUserSettings("admin").getViewAsList());
        assertEquals(otherSidebar, settings.getUserSettings("settings-reader").getShowSideBar());
        assertEquals(otherList, settings.getUserSettings("settings-reader").getViewAsList());
        assertEquals(403, send(browser, "/rest/getWebsiteSettings", "f=json", null).statusCode());
        JsonNode current = body(send(browser, "/rest/getWebsiteSettings", "f=json", csrf)).path("websiteSettings");
        assertTrue(current.toString(), current.path("showSideBar").isBoolean());
        assertTrue(current.toString(), current.path("viewAsList").isBoolean());
        assertEquals(sidebar, current.path("showSideBar").asBoolean());
        assertEquals(list, current.path("viewAsList").asBoolean());
        assertEquals("admin", current.path("username").asText());
        assertTrue(current.path("settingsRole").asBoolean());
        assertEquals(admin.getAvatarScheme() != AvatarScheme.NONE, current.path("showAvatar").asBoolean());
        admin.setAvatarScheme(AvatarScheme.NONE);
        settings.updateUserSettings(admin);
        assertFalse(body(send(browser, "/rest/getWebsiteSettings", "f=json&username=settings-reader&settingsRole=false", csrf))
                .path("websiteSettings").path("showAvatar").asBoolean());
        assertEquals(10, body(send(browser, "/rest/updateWebsiteSettings", "f=json", csrf)).path("error").path("code").asInt());
        assertEquals("failed", body(send(browser, "/rest/updateWebsiteSettings", "f=json&viewAsList=yes", csrf)).path("status").asText());
        assertEquals(list, settings.getUserSettings("admin").getViewAsList());
        String auth = "v=" + TestCaseUtils.restApiVersion() + "&c=test&u=settings-reader&p=secret&viewAsList=" + !otherList;
        HttpResponse<String> xml = send(HttpClient.newHttpClient(), "/rest/updateWebsiteSettings.view", auth, null);
        assertEquals(xml.body(), 200, xml.statusCode());
        assertTrue(xml.body().contains("status=\"ok\""));
        assertEquals(!otherList, settings.getUserSettings("settings-reader").getViewAsList());
        String readAuth = "v=" + TestCaseUtils.restApiVersion() + "&c=test&u=settings-reader&p=secret";
        HttpResponse<String> readXml = send(HttpClient.newHttpClient(), "/rest/getWebsiteSettings.view", readAuth, null);
        assertTrue(readXml.body(), readXml.body().contains("viewAsList=\"" + !otherList + "\""));
        assertTrue(readXml.body(), readXml.body().contains("showSideBar=\"" + otherSidebar + "\""));
        assertTrue(readXml.body(), readXml.body().contains("username=\"settings-reader\""));
        assertTrue(readXml.body(), readXml.body().contains("settingsRole=\"false\""));
        assertTrue(readXml.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        HttpClient member = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        Matcher memberToken = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(send(member, "/login", null, null).body());
        assertTrue(memberToken.find());
        assertEquals(302, send(member, "/login", "j_username=settings-reader&j_password=secret&_csrf=" + memberToken.group(1), null).statusCode());
        assertEquals(200, send(member, "/top.view", null, null).statusCode());
        assertFalse(context.containsBean("userSettingsWSController"));
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
