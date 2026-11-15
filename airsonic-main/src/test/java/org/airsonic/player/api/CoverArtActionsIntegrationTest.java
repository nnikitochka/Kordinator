package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.LastFmCoverArt;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.CoverArtSearchService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.HomeRule;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class CoverArtActionsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private ApplicationContext context;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao files;
    @Autowired private SecurityService security;
    @MockitoBean private CoverArtSearchService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void sessionSearchAndSetEnforceRolesAndCsrf() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            int id = files.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId()).getId();
            when(service.searchCoverArt("A & B", "Album")).thenReturn(List.of(new LastFmCoverArt("https://img.test/a.png", "A & B", "Album")));
            HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<String> login = send(browser, "/login", null, null);
            java.util.regex.Matcher token = java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
            assertTrue(token.find());
            assertEquals(302, send(browser, "/login", "j_username=admin&j_password=admin&_csrf=" + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8), null).statusCode());
            HttpResponse<String> page = send(browser, "/changeCoverArt.view?id=" + id + "&artist=A&album=B", null, null);
            assertEquals(page.body(), 200, page.statusCode());
            assertTrue(page.body().contains("/airsonic/script/subsonic-cover-art.js"));
            assertFalse(page.body().contains("/app/coverart/"));
            assertTrue(page.body().contains("value=\"A\""));
            assertTrue(page.body().contains("value=\"B\""));
            HttpResponse<String> defaults = send(browser, "/changeCoverArt?id=" + id, null, null);
            assertEquals(defaults.body(), 200, defaults.statusCode());
            assertTrue(defaults.body().contains("id=\"album\""));
            assertEquals(400, send(browser, "/changeCoverArt", null, null).statusCode());
            assertEquals(404, send(browser, "/changeCoverArt?id=2147483647", null, null).statusCode());
            int inaccessibleId = files.getMediaFile("_DIR_ chrome hoof - 2004", library.get(1).getId()).getId();
            User restricted = new User("cover-restricted", null);
            restricted.setRoles(Set.of(User.Role.COVERART));
            security.createUser(restricted, "secret", "test");
            folders.setMusicFoldersForUser(restricted.getUsername(), Set.of(library.get(0).getId()));
            HttpClient restrictedBrowser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<String> restrictedLogin = send(restrictedBrowser, "/login", null, null);
            token = java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(restrictedLogin.body());
            assertTrue(token.find());
            assertEquals(302, send(restrictedBrowser, "/login", "j_username=cover-restricted&j_password=secret&_csrf="
                    + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8), null).statusCode());
            assertEquals(403, send(restrictedBrowser, "/changeCoverArt.view?id=" + inaccessibleId, null, null).statusCode());
            token = java.util.regex.Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(page.body());
            assertTrue(token.find());
            String csrf = token.group(1);
            String query = "f=json&c=airsonic-web&artist=A+%26+B&album=Album";
            assertEquals(403, send(browser, "/rest/searchCoverArt", query, null).statusCode());
            JsonNode result = body(send(browser, "/rest/searchCoverArt", query, csrf));
            assertEquals("ok", result.path("status").asText());
            assertEquals("https://img.test/a.png", result.path("coverArtSearchResults").path("image").get(0).path("imageUrl").asText());
            assertEquals("ok", body(send(browser, "/rest/setCoverArt", "f=json&id=" + id + "&url=https%3A%2F%2Fimg.test%2Fa.png", csrf)).path("status").asText());
            verify(service).setCoverArtImage(id, "https://img.test/a.png", "admin");
            assertEquals(10, body(send(browser, "/rest/setCoverArt", "f=json&id=" + id, csrf)).path("error").path("code").asInt());
            security.createUser(new User("cover-reader", null), "secret", "test");
            String auth = "v=" + TestCaseUtils.restApiVersion() + "&c=test&u=cover-reader&p=secret&f=json&";
            HttpClient external = HttpClient.newHttpClient();
            assertEquals(50, body(send(external, "/rest/searchCoverArt.view", auth + "artist=A&album=B", null)).path("error").path("code").asInt());
            assertEquals(50, body(send(external, "/rest/setCoverArt.view", auth + "id=" + id + "&url=https%3A%2F%2Fimg.test", null)).path("error").path("code").asInt());
            assertFalse(context.containsBean("coverArtWSController"));
            assertFalse(context.containsBean("changeCoverArtController"));
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
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
