package org.airsonic.player.api;

import org.airsonic.player.domain.User;
import org.airsonic.player.service.TaskSchedulingService;
import org.airsonic.player.TestCaseUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
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
public class ScheduledTasksIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private ApplicationContext context;
    @Autowired private TaskSchedulingService tasks;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void scheduledTaskDiagnosticsUseKtorForAuthenticatedUsers() throws Exception {
        User member = new User("scheduled-member", null);
        member.setRoles(Set.of(User.Role.STREAM));
        security.createUser(member, "secret", "test");
        assertFalse(java.util.Arrays.stream(context.getBeanDefinitionNames()).anyMatch(name -> name.contains("CustomScheduledTasksEndpoint")));
        AtomicInteger executed = new AtomicInteger();
        Instant future = Instant.now().plusSeconds(86400);
        String once = "<scheduled & once>", rate = "scheduled-rate-test", delay = "scheduled-delay-test";
        tasks.scheduleOnce(once, executed::incrementAndGet, future, true);
        tasks.scheduleAtFixedRate(rate, executed::incrementAndGet, future, Duration.ofHours(1), true);
        tasks.scheduleFixedDelayTask(delay, executed::incrementAndGet, future, Duration.ofHours(1), true);
        try {
            JsonNode anonymous = body(get(client, "/rest/getScheduledTasks?f=json"));
            assertEquals("failed", anonymous.path("status").asText());
            assertFalse(anonymous.has("scheduledTasks"));
            for (HttpClient browser : new HttpClient[]{login("admin", "admin"), login(member.getUsername(), "secret")}) {
                HttpResponse<String> response = get(browser, "/rest/getScheduledTasks?f=json");
                assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
                JsonNode result = body(response);
                assertEquals("ok", result.path("status").asText());
                JsonNode rows = result.path("scheduledTasks").path("task");
                assertTrue(rows.toString(), rows.isArray());
                for (String name : new String[]{once, rate, delay}) {
                    JsonNode row = null;
                    for (JsonNode candidate : rows) if (name.equals(candidate.path("name").asText())) row = candidate;
                    assertNotNull(name, row);
                    assertEquals(name.equals(once) ? "RUN_ONCE" : name.equals(rate) ? "FIXED_RATE" : "FIXED_DELAY", row.path("type").asText());
                    assertTrue(Instant.parse(row.path("firstRun").asText()).isAfter(Instant.now()));
                    assertEquals(row.path("firstRun"), row.path("nextRun"));
                    assertTrue(row.path("lastRun").isMissingNode() || row.path("lastRun").isNull());
                    assertTrue(row.path("scheduledBy").isTextual());
                    assertTrue(row.path("created").isTextual());
                    assertFalse(row.has("scheduledTask"));
                    assertFalse(row.has("runnable"));
                }
                HttpResponse<String> page = get(browser, "/status.view");
                assertEquals(200, page.statusCode());
                assertTrue(page.body().contains("statisticsApi.scheduledTasks()"));
                assertFalse(page.body().contains("/actuator/customscheduledtasks"));
                assertEquals(404, get(browser, "/actuator/customscheduledtasks").statusCode());
                Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body());
                assertTrue(token.find());
                assertEquals(403, post(browser, "/rest/getScheduledTasks", "").statusCode());
                for (String method : new String[]{"POST", "PUT", "DELETE", "PATCH"}) {
                    HttpResponse<String> denied = browser.send(HttpRequest.newBuilder(URI.create(base() + "/rest/getScheduledTasks"))
                            .header("X-CSRF-TOKEN", token.group(1)).method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                    assertEquals(405, denied.statusCode());
                }
            }
            HttpResponse<String> xml = get(client, "/rest/getScheduledTasks?u=admin&p=admin&c=test&v=" + TestCaseUtils.restApiVersion());
            assertEquals(200, xml.statusCode());
            var factory = javax.xml.parsers.DocumentBuilderFactory.newDefaultNSInstance();
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var document = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml.body())));
            assertEquals(1, document.getElementsByTagNameNS("http://subsonic.org/restapi", "scheduledTasks").getLength());
            boolean found = false;
            var nodes = document.getElementsByTagNameNS("http://subsonic.org/restapi", "task");
            for (int i = 0; i < nodes.getLength(); i++) if (once.equals(((org.w3c.dom.Element) nodes.item(i)).getAttribute("name"))) found = true;
            assertTrue(found);
            assertEquals(0, executed.get());
            tasks.unscheduleTask(once);
            JsonNode rows = body(get(client, "/rest/getScheduledTasks?f=json&u=admin&p=admin&c=test&v=" + TestCaseUtils.restApiVersion())).path("scheduledTasks").path("task");
            for (JsonNode row : rows) assertNotEquals(once, row.path("name").asText());
        } finally {
            tasks.unscheduleTask(once); tasks.unscheduleTask(rate); tasks.unscheduleTask(delay);
        }
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
