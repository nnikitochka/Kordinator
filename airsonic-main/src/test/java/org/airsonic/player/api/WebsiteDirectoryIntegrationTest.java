package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
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
import org.springframework.test.context.junit4.SpringRunner;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class WebsiteDirectoryIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void websiteDirectoryUsesSessionAndProtectsEverySelectedFolder() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            String albumPath = "_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes";
            MediaFile album = mediaFiles.getMediaFile(albumPath, library.get(0).getId());
            MediaFile other = mediaFiles.getMediaFile("_DIR_ chrome hoof - 2004", library.get(1).getId());
            assertNotNull(album); assertNotNull(other);
            User restricted = new User("web-directory-reader", null);
            restricted.setRoles(Set.of(User.Role.STREAM));
            security.createUser(restricted, "secret", "test");
            folders.setMusicFoldersForUser(restricted.getUsername(), Set.of(library.get(0).getId()));
            HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<String> login = send(browser, "/login", null, null);
            Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
            assertTrue(token.find());
            assertEquals(302, send(browser, "/login", "j_username=web-directory-reader&j_password=secret&_csrf=" + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8), null).statusCode());
            HttpResponse<String> page = send(browser, "/main.view?id=" + album.getId(), null, null);
            assertEquals(page.body(), 200, page.statusCode());
            assertTrue(page.body().contains("/airsonic/script/subsonic-media-directory.js"));
            assertFalse(page.body().contains("/app/mediafile/directory/get"));
            token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(page.body());
            assertTrue(token.find());
            String csrf = token.group(1);
            String base = "f=json&c=airsonic-web&includeWebsiteDetails=true";
            assertEquals(403, send(browser, "/rest/getMusicDirectory", base + "&id=" + album.getId(), null).statusCode());
            JsonNode dir = body(send(browser, "/rest/getMusicDirectory", base + "&id=" + album.getId(), csrf)).path("websiteDirectory");
            assertEquals("album", dir.path("contentType").asText());
            assertEquals(album.getId().intValue(), dir.path("detail").path("id").asInt());
            assertTrue(dir.path("file").size() > 0);
            assertTrue(dir.path("ancestor").size() > 0);
            MediaFile artist = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower", library.get(0).getId());
            JsonNode combined = body(send(browser, "/rest/getMusicDirectory", base + "&id=" + album.getId() + "&id=" + artist.getId(), csrf)).path("websiteDirectory");
            assertTrue(combined.path("file").size() > 0);
            assertTrue(combined.path("subDir").size() > 0);
            String path = URLEncoder.encode(library.get(0).getId() + ":" + albumPath, StandardCharsets.UTF_8);
            assertEquals(album.getId().intValue(), body(send(browser, "/rest/getMusicDirectory", base + "&folderPath=" + path, csrf)).path("websiteDirectory").path("detail").path("id").asInt());
            assertEquals("accessDenied", body(send(browser, "/rest/getMusicDirectory", base + "&id=" + album.getId() + "&id=" + other.getId(), csrf)).path("websiteDirectory").path("contentType").asText());
            assertEquals("notFound", body(send(browser, "/rest/getMusicDirectory", base + "&id=2147483647", csrf)).path("websiteDirectory").path("contentType").asText());
            assertEquals(10, body(send(browser, "/rest/getMusicDirectory", base, csrf)).path("error").path("code").asInt());
            String external = "v=" + TestCaseUtils.restApiVersion() + "&c=test&u=admin&p=admin&f=json&id=" + album.getId();
            JsonNode standard = body(send(HttpClient.newHttpClient(), "/rest/getMusicDirectory.view?" + external, null, null));
            assertTrue(standard.has("directory"));
            assertFalse(standard.has("websiteDirectory"));
            HttpResponse<String> xml = send(HttpClient.newHttpClient(), "/rest/getMusicDirectory.view?" + external.replace("f=json", "f=xml") + "&includeWebsiteDetails=true", null, null);
            assertEquals(xml.body(), 200, xml.statusCode());
            assertTrue(xml.body().contains("<websiteDirectory"));
            assertFalse(context.containsBean("mediaFileWSController"));
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
