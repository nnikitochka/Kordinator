package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.StatusService;
import org.airsonic.player.util.HomeRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class UploadStatusIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private StatusService status;
    @Autowired private SecurityService security;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void returnsOnlyCurrentUsersActiveUploads() throws Exception {
        User uploader = new User("upload-other", null);
        uploader.setRoles(Set.of(User.Role.UPLOAD));
        security.createUser(uploader, "secret", "test");
        User denied = new User("upload-denied", null);
        denied.setRoles(Set.of(User.Role.STREAM));
        security.createUser(denied, "secret", "test");
        Player adminPlayer = new Player(); adminPlayer.setUsername("admin");
        Player otherPlayer = new Player(); otherPlayer.setUsername("upload-other");
        TransferStatus own = status.createUploadStatus(adminPlayer);
        TransferStatus other = status.createUploadStatus(otherPlayer);
        try {
            own.setBytesTotal(100); own.addBytesTransferred(25);
            other.setBytesTotal(200); other.addBytesTransferred(42);
            HttpClient client = HttpClient.newHttpClient();
            JsonNode admin = body(send(client, "/rest/getUploadStatus.view", auth("admin", "admin") + "&f=json", null));
            JsonNode uploads = admin.path("uploadStatuses").path("upload");
            assertEquals(uploads.toString(), 1, uploads.size());
            assertEquals(own.getId().toString(), uploads.get(0).path("transferId").asText());
            assertEquals(25, uploads.get(0).path("bytesUploaded").asLong());
            assertEquals(100, uploads.get(0).path("bytesTotal").asLong());
            String xml = send(client, "/rest/getUploadStatus.view", auth("upload-other", "secret"), null).body();
            assertTrue(xml, xml.contains(other.getId().toString()));
            assertFalse(xml, xml.contains(own.getId().toString()));
            assertEquals("failed", body(send(client, "/rest/getUploadStatus.view", auth("upload-denied", "secret") + "&f=json", null)).path("status").asText());
            status.removeUploadStatus(own);
            JsonNode emptyStatuses = body(send(client, "/rest/getUploadStatus.view", auth("admin", "admin") + "&f=json", null)).path("uploadStatuses");
            assertTrue(emptyStatuses.toString(), emptyStatuses.isObject());
            assertEquals(emptyStatuses.toString(), 0, emptyStatuses.path("upload").size());

            HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<String> login = send(browser, "/login", null, null);
            Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
            assertTrue(token.find());
            assertEquals(302, send(browser, "/login", "j_username=admin&j_password=admin&_csrf=" + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8), null).statusCode());
            for (String pagePath : new String[] {"/more.view", "/databaseSettings.view"}) {
                HttpResponse<String> page = send(browser, pagePath, null, null);
                assertEquals(page.body(), 200, page.statusCode());
                assertTrue(page.body(), page.body().contains("subsonic-upload-status.js"));
                assertFalse(page.body().contains("/user/queue/uploads/status"));
                token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(page.body());
                assertTrue(page.body(), token.find());
                assertEquals(403, send(browser, "/rest/getUploadStatus", "f=json", null).statusCode());
                assertEquals("ok", body(send(browser, "/rest/getUploadStatus", "f=json", token.group(1))).path("status").asText());
                if (pagePath.equals("/more.view")) {
                    Path target = Path.of(TestCaseUtils.airsonicHomePathForTest(), "upload-status-test");
                    String multipart = "--boundary\r\nContent-Disposition: form-data; name=\"dir\"\r\n\r\n" + target +
                            "\r\n--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n" +
                            "Content-Type: text/plain\r\n\r\ntest upload\r\n--boundary--\r\n";
                    HttpRequest upload = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/upload.view"))
                            .header("X-CSRF-TOKEN", token.group(1)).header("Content-Type", "multipart/form-data; boundary=boundary")
                            .POST(HttpRequest.BodyPublishers.ofString(multipart)).build();
                    HttpResponse<String> uploaded = browser.send(upload, HttpResponse.BodyHandlers.ofString());
                    assertEquals(uploaded.body(), 200, uploaded.statusCode());
                    assertEquals("test upload", Files.readString(target.resolve("test.txt")));
                    assertTrue(status.getAllUploadStatuses().stream().noneMatch(transfer -> transfer.getPlayer().getUsername().equals("admin")));
                }
            }
        } finally {
            status.removeUploadStatus(own);
            status.removeUploadStatus(other);
        }
    }

    private String auth(String username, String password) {
        return "v=" + TestCaseUtils.restApiVersion() + "&c=upload-test&u=" + username + "&p=" + password;
    }
    private HttpResponse<String> send(HttpClient client, String path, String form, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (form != null) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
}
