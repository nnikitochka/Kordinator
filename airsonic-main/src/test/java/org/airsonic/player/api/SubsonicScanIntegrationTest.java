package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.HomeRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SubsonicScanIntegrationTest {
    @ClassRule
    public static final HomeRule homeRule = new HomeRule();

    @LocalServerPort
    private int port;
    @MockitoBean
    private MediaScannerService scanner;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private PlayerService playerService;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() {
        reset(scanner);
        when(scanner.isScanning()).thenReturn(true);
        when(scanner.getScanCount()).thenReturn(42);
    }

    @Test
    public void preservesScanContract() throws Exception {
        for (String endpoint : List.of("startScan", "getScanStatus")) {
            for (String suffix : List.of("", ".view")) {
                String path = "/rest/" + endpoint + suffix;
                HttpResponse<String> json = send(path + "?" + credentials() + "&f=json", null, null);
                assertTrue(json.headers().firstValue("content-type").orElseThrow().startsWith("application/json"));
                assertStatus(body(json));
                HttpResponse<String> xml = send(path, null, "admin:admin");
                assertEquals(200, xml.statusCode());
                assertTrue(xml.body(), xml.body().contains("scanning=\"true\""));
                assertTrue(xml.body(), xml.body().contains("count=\"42\""));
                HttpResponse<String> jsonp = send(path + "?" + credentials() + "&f=jsonp&callback=client.scan", null, null);
                assertTrue(jsonp.body(), jsonp.body().startsWith("client.scan("));
                assertTrue(jsonp.body(), jsonp.body().endsWith(");"));
                assertStatus(mapper.readTree(jsonp.body().substring("client.scan(".length(), jsonp.body().length() - 2))
                        .path("subsonic-response"));
                assertEquals(10, body(send(path + "?f=json", null, null)).path("error").path("code").asInt());
                assertEquals(40, body(send(path + "?f=json&v=" + TestCaseUtils.restApiVersion()
                        + "&c=scan-test&u=admin&p=wrong", null, null)).path("error").path("code").asInt());
            }
            assertStatus(body(send("/rest/" + endpoint + ".view", credentials() + "&f=json", null)));
            assertEquals(403, send("/rest/" + endpoint, credentials() + "&f=json", null).statusCode());
        }
        verify(scanner, times(7)).scanLibrary();
        assertFalse(playerService.getPlayersForUserAndClientId("admin", "scan-test").isEmpty());
    }

    @Test
    public void onlyAdminCanStartScan() throws Exception {
        securityService.createUser(new User("scanuser", null), "secret", "scan test");
        for (String suffix : List.of("", ".view")) {
            assertStatus(body(send("/rest/getScanStatus" + suffix + "?f=json", null, "scanuser:secret")));
            HttpResponse<String> denied = send("/rest/startScan" + suffix + "?f=json", null, "scanuser:secret");
            assertEquals(403, denied.statusCode());
        }
        verify(scanner, never()).scanLibrary();
    }

    private void assertStatus(JsonNode response) {
        assertEquals("ok", response.path("status").asText());
        assertEquals(TestCaseUtils.restApiVersion(), response.path("version").asText());
        assertTrue(response.path("scanStatus").path("scanning").asBoolean());
        assertEquals(42, response.path("scanStatus").path("count").asInt());
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }

    private String credentials() {
        return "v=" + TestCaseUtils.restApiVersion() + "&c=scan-test&u=admin&p=admin";
    }

    private HttpResponse<String> send(String path, String form, String basic) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/airsonic" + path))
                .timeout(Duration.ofSeconds(10));
        if (basic != null) {
            request.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
        }
        if (form != null) {
            request.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
