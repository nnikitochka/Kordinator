package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.TranscodeScheme;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.MediaFolderService;
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
import java.net.URI;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.http.*;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class UsersIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    @Autowired private MediaFolderService folders;
    @Autowired private org.springframework.context.ApplicationContext context;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void userManagement() throws Exception {
        User admin = new User("users-admin", null); admin.setRoles(Set.of(User.Role.ADMIN, User.Role.STREAM));
        security.createUser(admin, "secret", "test");
        User member = new User("users-member", null); member.setRoles(Set.of(User.Role.SETTINGS, User.Role.STREAM));
        security.createUser(member, "secret", "test");
        for (String suffix : new String[]{"", ".view"}) {
            error(call("getUser" + suffix, "users-member", "username=users-admin"), 50);
            error(call("getUsers" + suffix, "users-member", ""), 50);
            error(call("createUser" + suffix, "users-member", ""), 50);
            error(call("updateUser" + suffix, "users-member", ""), 50);
            error(call("deleteUser" + suffix, "users-member", ""), 50);
            error(call("changePassword" + suffix, "users-member", "username=users-admin&password=x"), 50);
            error(call("getUser" + suffix, "users-admin", ""), 10);
            error(call("getUser" + suffix, "users-admin", "username=missing-user"), 70);
            error(call("deleteUser" + suffix, "users-admin", "username=admin"), 50);
            assertEquals("users-member", call("getUser" + suffix, "users-member", "username=users-member").path("user").path("username").asText());
            assertTrue(call("getUsers" + suffix, "users-admin", "").path("users").path("user").isArray());
        }
        error(call("getWebsiteUserAdministration", "users-member", ""), 50);
        error(call("getWebsiteUserAdministration", "users-admin", "username=missing-user"), 70);
        JsonNode newUserPage = call("getWebsiteUserAdministration", "users-admin", "").path("websiteUserAdministration");
        assertTrue(newUserPage.path("user").path("newUser").asBoolean());
        assertTrue(newUserPage.path("user").path("streamRole").asBoolean());
        assertTrue(newUserPage.path("user").path("settingsRole").asBoolean());
        assertTrue(newUserPage.path("schemes").path("scheme").isArray());
        JsonNode current = call("getWebsiteUserAdministration", "users-admin", "username=users-admin").path("websiteUserAdministration");
        assertTrue(current.path("user").path("currentUser").asBoolean());
        assertTrue(current.path("user").path("adminRole").asBoolean());
        assertEquals("admin", call("getWebsiteUserAdministration", "users-admin", "userIndex=0").path("websiteUserAdministration").path("user").path("username").asText());
        User ldapUser = new User("users-ldap", "ldap@example.com");
        ldapUser.setLdapAuthenticated(true);
        security.createUser(ldapUser, "secret", "test");
        JsonNode ldapDetails = call("getWebsiteUserAdministration", "users-admin", "username=users-ldap").path("websiteUserAdministration").path("user");
        assertTrue(ldapDetails.path("ldapAuthenticated").asBoolean());
        assertFalse(ldapDetails.has("password"));
        String xmlUrl = base() + "/rest/getWebsiteUserAdministration?v=" + TestCaseUtils.restApiVersion()
                + "&c=users-test&u=users-admin&p=secret&username=users-ldap";
        HttpResponse<String> xml = client.send(HttpRequest.newBuilder(URI.create(xmlUrl)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, xml.statusCode());
        assertTrue(xml.body().contains("websiteUserAdministration"));
        assertTrue(xml.body().contains("ldapAuthenticated=\"true\""));
        ok(call("createUser", "users-admin", "username=users-created&password=enc:70617373&email=test%40example.com&downloadRole=true"));
        assertTrue(security.getUserByName("users-created").isDownloadRole());
        assertEquals(TranscodeScheme.OFF, settings.getUserSettings("users-created").getTranscodeScheme());
        assertEquals(folders.getAllMusicFolders().size(), folders.getMusicFoldersForUser("users-created").size());
        ok(call("getUser", "users-created", "pass", "username=users-created"));
        ok(call("updateUser.view", "users-admin", "username=users-created&email=new%40example.com&shareRole=true&password=enc:75706461746564&maxBitRate=128"));
        assertEquals("new@example.com", security.getUserByName("users-created").getEmail());
        assertTrue(security.getUserByName("users-created").isShareRole());
        assertEquals(128, settings.getUserSettings("users-created").getTranscodeScheme().getMaxBitRate());
        ok(call("getUser", "users-created", "updated", "username=users-created"));
        ok(call("changePassword", "users-admin", "username=users-created&password=enc:6e6577"));
        ok(call("getUser", "users-created", "new", "username=users-created"));
        ok(call("changePassword.view", "users-member", "username=users-member&password=enc:6f776e"));
        ok(call("getUser", "users-member", "own", "username=users-member"));
        ok(call("deleteUser.view", "users-admin", "username=users-created"));
        assertNull(security.getUserByName("users-created"));
        assertFalse(context.containsBean("userSettingsController"));
        websiteUserAdministration();
    }
    private void websiteUserAdministration() throws Exception {
        Path folderPath = Files.createTempDirectory("users-test-music");
        folders.createMusicFolder(new MusicFolder(folderPath, "Users test", MusicFolder.Type.MEDIA, true, Instant.now()));
        int folderId = folders.getAllMusicFolders().stream().filter(folder -> folder.getPath().equals(folderPath)).findFirst().orElseThrow().getId();
        HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> login = browser.send(HttpRequest.newBuilder(URI.create(base() + "/login")).GET().build(), HttpResponse.BodyHandlers.ofString());
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
        assertTrue(token.find());
        String credentials = "j_username=admin&j_password=admin&_csrf=" + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
        assertEquals(302, post(browser, "/login", credentials).statusCode());
        HttpResponse<String> page = browser.send(HttpRequest.newBuilder(URI.create(base() + "/top.view")).GET().build(), HttpResponse.BodyHandlers.ofString());
        token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(page.body());
        assertTrue(token.find());
        String csrf = URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
        for (String path : new String[]{"/userSettings", "/userSettings.view"}) {
            HttpResponse<String> userPage = browser.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(userPage.body(), 200, userPage.statusCode());
            assertTrue(userPage.body().contains("initUserAdministration"));
            assertFalse(userPage.body().contains("${_csrf.token}"));
        }
        assertEquals(403, post(browser, "/rest/saveWebsiteUser", "f=json&c=airsonic-web&username=admin&deleteUser=true").statusCode());
        HttpClient memberBrowser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> memberLogin = memberBrowser.send(HttpRequest.newBuilder(URI.create(base() + "/login")).GET().build(), HttpResponse.BodyHandlers.ofString());
        Matcher memberToken = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(memberLogin.body());
        assertTrue(memberToken.find());
        String memberCredentials = "j_username=users-member&j_password=own&_csrf=" + URLEncoder.encode(memberToken.group(1), StandardCharsets.UTF_8);
        assertEquals(302, post(memberBrowser, "/login", memberCredentials).statusCode());
        HttpResponse<String> memberPage = memberBrowser.send(HttpRequest.newBuilder(URI.create(base() + "/top.view")).GET().build(), HttpResponse.BodyHandlers.ofString());
        memberToken = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(memberPage.body());
        assertTrue(memberToken.find());
        assertEquals(403, memberBrowser.send(HttpRequest.newBuilder(URI.create(base() + "/userSettings.view")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        error(websiteCall(memberBrowser, URLEncoder.encode(memberToken.group(1), StandardCharsets.UTF_8), "username=admin&deleteUser=true"), 50);
        String form = "_csrf=" + csrf + "&newUser=true&username=website-created&email=web%40example.com&password=secret&confirmPassword=secret&streamRole=true&transcodeSchemeName=OFF&allowedMusicFolderIds=" + folderId;
        ok(websiteCall(browser, csrf, form));
        assertEquals("web@example.com", security.getUserByName("website-created").getEmail());
        assertTrue(security.getUserByName("website-created").isStreamRole());
        assertEquals(1, folders.getMusicFoldersForUser("website-created").size());
        JsonNode details = call("getWebsiteUserAdministration", "users-admin", "username=website-created").path("websiteUserAdministration");
        assertEquals("web@example.com", details.path("user").path("email").asText());
        assertTrue(details.path("user").path("streamRole").asBoolean());
        assertTrue(details.path("folders").path("folder").findValues("allowed").stream().anyMatch(JsonNode::asBoolean));
        ok(websiteCall(browser, csrf, "username=website-created&email=changed%40example.com&shareRole=true&streamRole=false&transcodeSchemeName=OFF"));
        assertEquals("changed@example.com", security.getUserByName("website-created").getEmail());
        assertTrue(security.getUserByName("website-created").isShareRole());
        assertFalse(security.getUserByName("website-created").isStreamRole());
        assertTrue(folders.getMusicFoldersForUser("website-created").isEmpty());
        details = call("getWebsiteUserAdministration", "users-admin", "username=website-created").path("websiteUserAdministration");
        assertFalse(details.path("folders").path("folder").findValues("allowed").stream().anyMatch(JsonNode::asBoolean));
        error(websiteCall(browser, csrf, "username=admin&deleteUser=true"), 50);
        error(websiteCall(browser, csrf, "username=admin&transcodeSchemeName=OFF&adminRole=false"), 50);
        error(websiteCall(browser, csrf, "username=website-created&transcodeSchemeName=INVALID"), 0);
        error(websiteCall(browser, csrf, "newUser=true&username=bad-user&email=x&password=a&confirmPassword=b&transcodeSchemeName=OFF"), 0);
        assertNull(security.getUserByName("bad-user"));
        ok(websiteCall(browser, csrf, "username=website-created&deleteUser=true"));
        assertNull(security.getUserByName("website-created"));
        assertEquals(405, post(browser, "/userSettings.view", "_csrf=" + csrf).statusCode());
    }
    private JsonNode websiteCall(HttpClient browser, String csrf, String form) throws Exception {
        HttpResponse<String> response = post(browser, "/rest/saveWebsiteUser", "f=json&c=airsonic-web&_csrf=" + csrf + "&" + form);
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
    private String base() { return "http://localhost:" + port + "/airsonic"; }
    private HttpResponse<String> post(HttpClient browser, String path, String form) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(base() + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode call(String path, String user, String query) throws Exception {
        return call(path, user, "secret", query);
    }
    private JsonNode call(String path, String user, String password, String query) throws Exception {
        String url = "http://localhost:" + port + "/airsonic/rest/" + path + "?v=" + TestCaseUtils.restApiVersion() + "&c=users-test&u=" + user + "&p=" + password + "&f=json&" + query;
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
    private void ok(JsonNode body) { assertEquals(body.toString(), "ok", body.path("status").asText()); }
    private void error(JsonNode body, int code) { assertEquals(body.toString(), code, body.path("error").path("code").asInt()); }
}
