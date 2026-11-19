package org.airsonic.player.api;

import org.airsonic.player.domain.User;
import org.airsonic.player.health.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.airsonic.player.TestCaseUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.Base64;
import java.util.IdentityHashMap;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.servlet.context-path=/airsonic", "management.endpoints.web.exposure.include=metrics",
        "management.health.ldap.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ServerHealthIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private HealthContributorRegistry contributors;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private final Map<HttpClient, String> authorization = new IdentityHashMap<>();

    @Test public void healthIsAvailableThroughKtorIncludingUnhealthyAndNestedComponents() throws Exception {
        User member = new User("health-member", null);
        member.setRoles(Set.of(User.Role.STREAM));
        security.createUser(member, "secret", "test");
        String name = "diagnostic <health> & test";
        AtomicInteger calls = new AtomicInteger();
        HealthIndicator up = () -> { calls.incrementAndGet(); return Health.up().withDetail("message", "<script>unsafe & text</script>")
                .withDetail("number", 42).withDetail("nested", Map.of("enabled", true)).build(); };
        contributors.registerContributor(name, up);
        try {
            assertEquals("failed", body(get(client, "/rest/getServerHealth?f=json")).path("status").asText());
            assertEquals(0, calls.get());
            for (HttpClient browser : new HttpClient[]{basic("admin", "admin"), basic(member.getUsername(), "secret")}) {
                var response = get(browser, "/rest/getServerHealth?f=json");
                assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
                JsonNode health = body(response).path("serverHealth");
                assertFalse(health.path("status").asText().isEmpty());
                JsonNode entry = component(health, name);
                assertEquals("UP", entry.path("status").asText());
                JsonNode details = mapper.readTree(entry.path("details").asText());
                assertEquals("<script>unsafe & text</script>", details.path("message").asText());
                assertEquals(42, details.path("number").asInt());
                assertTrue(details.path("nested").path("enabled").asBoolean());
                assertEquals("UP", component(health, "db").path("status").asText());
                assertEquals("UP", component(health, "diskSpace").path("status").asText());
                String previous = null;
                for (JsonNode row : health.path("component")) {
                    if (previous != null) assertTrue(previous.compareTo(row.path("name").asText()) <= 0);
                    previous = row.path("name").asText();
                }
            }
            assertEquals(2, calls.get());
            String auth = "?u=admin&p=admin&c=test&v=" + TestCaseUtils.restApiVersion();
            var xml = get(client, "/rest/getServerHealth" + auth);
            assertEquals(200, xml.statusCode());
            var factory = javax.xml.parsers.DocumentBuilderFactory.newDefaultNSInstance();
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var document = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml.body())));
            var nodes = document.getElementsByTagNameNS("http://subsonic.org/restapi", "component");
            boolean found = false;
            for (int i = 0; i < nodes.getLength(); i++) {
                var row = (org.w3c.dom.Element) nodes.item(i);
                if (name.equals(row.getAttribute("name"))) {
                    found = true;
                    assertEquals("UP", row.getAttribute("status"));
                    assertTrue(row.getTextContent().contains("<script>unsafe & text</script>"));
                }
            }
            assertTrue(found);
            contributors.unregisterContributor(name);
            contributors.registerContributor(name, CompositeHealthContributor.fromMap(Map.of(
                "healthy", up, "broken", (HealthIndicator) () -> { throw new IllegalStateException("diagnostic failure"); },
                "unavailable", (HealthIndicator) () -> Health.outOfService().withDetail("reason", "offline").build())));
            JsonNode result = body(get(client, "/rest/getServerHealth" + auth + "&f=json"));
            // A failing check is diagnostic data, not a failed Subsonic envelope or HTTP 503.
            assertEquals("ok", result.path("status").asText());
            JsonNode health = result.path("serverHealth");
            assertEquals("DOWN", health.path("status").asText());
            JsonNode composite = component(health, name);
            assertEquals("DOWN", composite.path("status").asText());
            assertEquals("UP", component(composite, "healthy").path("status").asText());
            assertEquals("OUT_OF_SERVICE", component(composite, "unavailable").path("status").asText());
            assertEquals("DOWN", component(composite, "broken").path("status").asText());
            assertTrue(component(composite, "broken").path("details").asText().contains("diagnostic failure"));
        } finally {
            contributors.unregisterContributor(name);
        }
        for (JsonNode row : body(get(basic("admin", "admin"), "/rest/getServerHealth?f=json")).path("serverHealth").path("component")) {
            assertNotEquals(name, row.path("name").asText());
        }
    }

    private JsonNode component(JsonNode parent, String name) {
        for (JsonNode row : parent.path("component")) if (name.equals(row.path("name").asText())) return row;
        throw new AssertionError("Missing health component: " + name + " in " + parent);
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }

    private HttpClient basic(String username, String password) {
        HttpClient browser = HttpClient.newHttpClient();
        authorization.put(browser, "Basic " + Base64.getEncoder().encodeToString((username + ":" + password)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return browser;
    }
    private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base() + path));
        String credentials = authorization.get(browser);
        if (credentials != null) request.header("Authorization", credentials);
        return browser.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private String base() { return "http://localhost:" + port + "/airsonic"; }
}
