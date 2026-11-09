package org.airsonic.player.api;

import org.airsonic.player.domain.User;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.service.StatusService;
import org.airsonic.player.util.StringUtil;
import org.airsonic.player.TestCaseUtils;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Locale;
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
public class StatisticsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private ApplicationContext context;
    @Autowired private StatusService status;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void exposesUserCountersAndTransferSnapshotsThroughKtor() throws Exception {
        User member = new User("stats-member", null);
        member.setRoles(Set.of(User.Role.STREAM));
        security.createUser(member, "secret", "test");
        security.updateUserByteCounts(member, 123456789012L, 3456, 7890);
        assertFalse(context.containsBean("statisticsController"));
        Player player = new Player(); player.setId(101); player.setUsername(member.getUsername());
        player.setName("<Player & stats>"); player.setType("<type>");
        Player oldPlayer = new Player(); oldPlayer.setId(102); oldPlayer.setUsername("admin");
        TransferStatus stream = status.createStreamStatus(player);
        TransferStatus stale = status.createStreamStatus(oldPlayer);
        TransferStatus download = status.createDownloadStatus(null);
        TransferStatus upload = status.createUploadStatus(player);
        try {
            stream.setBytesTransferred(8192);
            stream.setExternalFile(Path.of("/private-root/album/<song & track>.flac"));
            TransferStatus.SampleHistory history = (TransferStatus.SampleHistory) ReflectionTestUtils.getField(stream, "history");
            history.clear();
            long now = System.currentTimeMillis();
            history.add(new TransferStatus.Sample(4096, now - 6000));
            history.add(new TransferStatus.Sample(8192, now - 1000));
            TransferStatus.SampleHistory oldHistory = (TransferStatus.SampleHistory) ReflectionTestUtils.getField(stale, "history");
            oldHistory.add(new TransferStatus.Sample(1, now - 3_600_001));
            download.setBytesTransferred(1024); upload.setBytesTransferred(2048);
            HttpClient browser = login(member.getUsername(), "secret");
            for (String route : new String[]{"getTransferStatistics", "getUserTransferStatistics"}) {
                JsonNode anonymous = body(get(client, "/rest/" + route + "?f=json"));
                assertEquals("failed", anonymous.path("status").asText());
                assertFalse(anonymous.has("transferStatistics"));
                assertFalse(anonymous.has("userTransferStatistics"));
                HttpResponse<String> response = get(browser, "/rest/" + route + "?f=json&username=admin");
                assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
                assertEquals("ok", body(response).path("status").asText());
            }
            JsonNode transfers = body(get(browser, "/rest/getTransferStatistics?f=json")).path("transferStatistics").path("transfer");
            assertEquals(transfers.toString(), 3, transfers.size());
            JsonNode first = transfers.get(0);
            assertEquals("stream", first.path("transferType").asText());
            assertEquals(player.toString(), first.path("playerDescription").asText());
            assertEquals("<type>", first.path("playerType").asText());
            assertEquals(member.getUsername(), first.path("username").asText());
            assertEquals("album/<song & track>.flac", first.path("path").asText());
            assertEquals(StringUtil.formatBytes(8192L, Locale.ENGLISH), first.path("bytesTransferred").asText());
            assertEquals(2, first.path("sample").size());
            assertEquals(now - 6000, first.path("sample").get(0).path("timestamp").asLong());
            assertEquals(8192, first.path("sample").get(1).path("bytesTransferred").asLong());
            assertEquals("download", transfers.get(1).path("transferType").asText());
            assertTrue(transfers.get(1).path("username").isMissingNode() || transfers.get(1).path("username").isNull());
            assertEquals("upload", transfers.get(2).path("transferType").asText());
            JsonNode users = body(get(browser, "/rest/getUserTransferStatistics?f=json")).path("userTransferStatistics").path("user");
            JsonNode counters = null;
            for (JsonNode user : users) if (member.getUsername().equals(user.path("username").asText())) counters = user;
            assertNotNull(counters);
            assertEquals(123456789012L, counters.path("streamed").asLong());
            assertEquals(3456, counters.path("downloaded").asLong());
            assertEquals(7890, counters.path("uploaded").asLong());
            assertFalse(users.toString().contains("password"));
            HttpResponse<String> xml = get(client, "/rest/getTransferStatistics?u=admin&p=admin&c=test&v=" + TestCaseUtils.restApiVersion());
            assertEquals(xml.body(), 200, xml.statusCode());
            assertTrue(xml.body(), xml.body().contains("<transferStatistics>"));
            var factory = javax.xml.parsers.DocumentBuilderFactory.newDefaultNSInstance();
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var document = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml.body())));
            var transfer = (org.w3c.dom.Element) document.getElementsByTagNameNS("http://subsonic.org/restapi", "transfer").item(0);
            assertEquals("album/<song & track>.flac", transfer.getAttribute("path"));
            assertFalse(xml.body().contains("private-root"));
            assertTrue(xml.body().contains("<sample "));
            String usersXml = get(browser, "/rest/getUserTransferStatistics").body();
            assertTrue(usersXml, usersXml.contains("streamed=\"123456789012\""));
            Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body());
            assertTrue(token.find());
            for (String route : new String[]{"getTransferStatistics", "getUserTransferStatistics"}) {
                assertEquals(403, post(browser, "/rest/" + route, "").statusCode());
                HttpResponse<String> wrongMethod = browser.send(HttpRequest.newBuilder(URI.create(base() + "/rest/" + route))
                        .header("X-CSRF-TOKEN", token.group(1)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(405, wrongMethod.statusCode());
            }
            assertEquals(404, get(browser, "/statistics/transfers").statusCode());
            assertEquals(404, get(browser, "/statistics/users").statusCode());
        } finally {
            stream.setExternalFile(null);
            status.removeStreamStatus(stream); status.removeStreamStatus(stale);
            status.removeDownloadStatus(download); status.removeUploadStatus(upload);
        }
        HttpClient admin = login("admin", "admin");
        JsonNode empty = body(get(admin, "/rest/getTransferStatistics?f=json")).path("transferStatistics").path("transfer");
        assertEquals(empty.toString(), 0, empty.size());
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
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
}
