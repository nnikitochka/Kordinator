package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
import org.airsonic.player.runtime.DaoGraph;
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
public class ShareSettingsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private DaoGraph daoGraph;
    @Autowired private org.airsonic.player.service.ShareService shares;

    @Autowired private org.airsonic.player.service.MediaFolderService folders;
    @Autowired private org.airsonic.player.service.MediaScannerService scanner;
    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void manageSharesUsingExistingSubsonicRoutes() throws Exception {
        java.util.List<org.airsonic.player.domain.MusicFolder> library = org.airsonic.player.util.MusicFolderTestData.getTestMusicFolders();
        java.util.UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            org.airsonic.player.domain.MediaFile album = daoGraph.getMediaFileDao().getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.getFirst().getId());
            int song = daoGraph.getMediaFileDao().getChildrenOf(album.getPath(), album.getFolderId(), true).getFirst().getId();
            User member = new User("settings-owner", null); member.setRoles(Set.of(User.Role.SETTINGS, User.Role.STREAM));
            security.createUser(member, "secret", "test"); folders.setMusicFoldersForUser(member.getUsername(), java.util.List.of(library.getFirst().getId()));
            User external = new User("settings-external", null); external.setRoles(Set.of(User.Role.STREAM));
            security.createUser(external, "secret", "test"); folders.setMusicFoldersForUser(external.getUsername(), java.util.List.of(library.getFirst().getId()));
            org.airsonic.player.domain.Share own = seed(member.getUsername(), "Own", song);
            org.airsonic.player.domain.Share other = seed("admin", "Other", song);
            org.airsonic.player.domain.Share ownExpired = seed(member.getUsername(), "Expired", song);
            ownExpired.setExpires(java.time.Instant.now().minusSeconds(100)); shares.updateShare(ownExpired);
            org.airsonic.player.domain.Share otherExpired = seed("admin", "OtherExpired", song);
            otherExpired.setExpires(java.time.Instant.now().minusSeconds(100)); shares.updateShare(otherExpired);
            org.airsonic.player.domain.Share hidden = seed(member.getUsername(), "Hidden", null);
            org.airsonic.player.domain.Share externalShare = seed(external.getUsername(), "External", song);
            assertFalse(context.containsBean("shareSettingsController"));
            HttpClient browser = login(member.getUsername(), "secret");
            for (String page : new String[]{"shareSettings", "shareSettings.view"}) {
                HttpResponse<String> response = get(browser, "/" + page);
                assertEquals(response.body(), 200, response.statusCode());
                assertTrue(response.body().contains("initShareSettings"));
                assertTrue(response.body().contains("/airsonic/rest/getShares"));
                assertTrue(response.body().contains("/airsonic/rest/updateShare"));
                assertTrue(response.body().contains("/airsonic/rest/deleteShare"));
                assertFalse(response.body().contains("action=\"shareSettings.view\""));
            }
            assertEquals(200, get(login("admin", "admin"), "/shareSettings.view").statusCode());
            assertEquals(403, get(login(external.getUsername(), "secret"), "/shareSettings.view").statusCode());
            Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body());
            assertTrue(token.find()); String csrf = "&_csrf=" + encode(token.group(1));
            assertEquals(405, post(browser, "/shareSettings.view", csrf.substring(1)).statusCode());
            assertEquals(405, post(browser, "/shareSettings", csrf.substring(1)).statusCode());
            JsonNode owned = api("getShares", member.getUsername(), "secret", null).path("shares").path("share");
            assertEquals(3, owned.size());
            for (JsonNode share : owned) {
                assertEquals(member.getUsername(), share.path("username").asText());
                if (share.path("id").asInt() == hidden.getId()) assertEquals(0, share.path("entry").size());
                else {
                    assertEquals(1, share.path("entry").size());
                    assertEquals(String.valueOf(album.getId()), share.path("entry").get(0).path("parent").asText());
                }
            }
            assertEquals(403, post(browser, "/rest/updateShare", "f=json&id=" + own.getId() + "&description=SHOULD_NOT_SAVE").statusCode());
            assertEquals(403, post(browser, "/rest/deleteShare", "f=json&id=" + own.getId()).statusCode());
            for (String operation : new String[]{"updateShare", "deleteShare"}) {
                error(website(browser, operation, "id=" + other.getId() + "&description=SHOULD_NOT_SAVE" + csrf), 50);
            }
            assertEquals("Other", shares.getShareById(other.getId()).getDescription());
            long future = java.time.Instant.now().plusSeconds(7 * 86400L).toEpochMilli();
            ok(website(browser, "updateShare", "id=" + own.getId() + "&description=" + encode("<REST & share settings>") + "&expires=" + future + csrf));
            assertEquals("<REST & share settings>", shares.getShareById(own.getId()).getDescription());
            assertEquals(future, shares.getShareById(own.getId()).getExpires().toEpochMilli());
            ok(website(browser, "updateShare", "id=" + own.getId() + "&description=Preserve-expiration" + csrf));
            assertEquals(future, shares.getShareById(own.getId()).getExpires().toEpochMilli());
            ok(website(browser, "updateShare", "id=" + own.getId() + "&description=Never&expires=0" + csrf));
            assertNull(shares.getShareById(own.getId()).getExpires());
            // Expired-link cleanup uses a fresh standard REST listing and deletes only links visible to this account.
            JsonNode current = api("getShares", member.getUsername(), "secret", null).path("shares").path("share");
            for (JsonNode share : current) {
                if (share.has("expires") && java.time.Instant.parse(share.path("expires").asText()).isBefore(java.time.Instant.now()))
                    ok(website(browser, "deleteShare", "id=" + share.path("id").asText() + csrf));
            }
            assertNull(shares.getShareById(ownExpired.getId())); assertNotNull(shares.getShareById(otherExpired.getId()));
            // Existing external clients do not need the website's SETTINGS role.
            JsonNode externalList = api("getShares", external.getUsername(), "secret", null); ok(externalList);
            assertEquals(1, externalList.path("shares").path("share").size());
            ok(api("updateShare&id=" + externalShare.getId() + "&description=External-client&expires=0", external.getUsername(), "secret", null));
            assertEquals("External-client", shares.getShareById(externalShare.getId()).getDescription());
            HttpClient admin = login("admin", "admin");
            Matcher adminToken = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(admin, "/top.view").body()); assertTrue(adminToken.find());
            ok(website(admin, "updateShare", "id=" + own.getId() + "&description=Admin-edited&_csrf=" + encode(adminToken.group(1))));
            assertEquals("Admin-edited", shares.getShareById(own.getId()).getDescription());
            ok(website(browser, "deleteShare", "id=" + own.getId() + csrf)); assertNull(shares.getShareById(own.getId()));
            error(website(browser, "deleteShare", "id=" + own.getId() + csrf), 70);
        } finally { ScanningTestUtils.after(cleanup, folders); folders.expunge(); }
    }

    private org.airsonic.player.domain.Share seed(String username, String name, Integer file) {
        org.airsonic.player.domain.Share share = new org.airsonic.player.domain.Share();
        share.setUsername(username); share.setName(name); share.setDescription(name); share.setCreated(java.time.Instant.now());
        share.setExpires(java.time.Instant.now().plusSeconds(86400)); daoGraph.getShareDao().createShare(share);
        if (file != null) daoGraph.getShareDao().createSharedFiles(share.getId(), java.util.List.of(file)); return share;
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
