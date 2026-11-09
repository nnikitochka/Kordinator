package org.airsonic.player.api;

import org.airsonic.player.domain.User;
import javax.cache.CacheManager;
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
public class CacheStatisticsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private CacheManager caches;
    @Autowired private io.micrometer.core.instrument.MeterRegistry meters;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void cacheDiagnosticsAndTargetedClearingUseKtor() throws Exception {
        User member = new User("cache-member", null);
        member.setRoles(Set.of(User.Role.STREAM));
        security.createUser(member, "secret", "test");
        String temporaryName = "diagnostic <cache> & test";
        var temporary = caches.createCache(temporaryName, new javax.cache.configuration.MutableConfiguration<String, String>()
                .setTypes(String.class, String.class).setStoreByValue(false));
        var cache = caches.createCache("cache-statistics-test", new javax.cache.configuration.MutableConfiguration<Integer, Object>()
                .setTypes(Integer.class, Object.class).setStoreByValue(false).setStatisticsEnabled(true));
        io.micrometer.core.instrument.binder.cache.JCacheMetrics.monitor(meters, cache);
        int key = -197112, missing = -197113;
        try {
            assertEquals("failed", body(get(client, "/rest/getCacheStatistics?f=json")).path("status").asText());
            HttpClient admin = login("admin", "admin");
            JsonNode before = cacheRow(admin, "cache-statistics-test");
            cache.put(key, "private cached value");
            assertEquals("private cached value", cache.get(key));
            assertNull(cache.get(missing));
            cache.remove(key);
            JsonNode after = cacheRow(admin, "cache-statistics-test");
            for (String metric : new String[]{"hits", "misses", "puts", "removals"}) {
                assertTrue(metric + ": " + before + " -> " + after, after.path(metric).isNumber());
                assertTrue(metric, after.path(metric).asDouble() >= before.path(metric).asDouble() + 1);
            }
            for (HttpClient browser : new HttpClient[]{admin, login(member.getUsername(), "secret")}) {
                cache.put(key, "private cached value");
                temporary.put("keep", "untouched");
                HttpResponse<String> response = get(browser, "/rest/getCacheStatistics?f=json");
                assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
                JsonNode result = body(response);
                assertEquals("ok", result.path("status").asText());
                JsonNode rows = result.path("cacheStatistics").path("cache");
                String previous = null;
                int count = 0;
                for (JsonNode row : rows) {
                    String id = row.path("name").asText();
                    if (previous != null) assertTrue(previous.compareTo(id) <= 0);
                    previous = id;
                    count++;
                    assertFalse(row.has("entries"));
                }
                assertEquals(java.util.stream.StreamSupport.stream(caches.getCacheNames().spliterator(), false).count(), count);
                assertFalse(response.body().contains("private cached value"));
                assertFalse(response.body().contains("untouched"));
                JsonNode unavailable = cacheRow(browser, temporaryName);
                for (String metric : new String[]{"hits", "misses", "puts", "removals"}) assertTrue(unavailable.path(metric).isMissingNode() || unavailable.path(metric).isNull());
                assertTrue(cache.containsKey(key));
                String page = get(browser, "/status.view").body();
                assertTrue(page.contains("statisticsApi.caches()"));
                assertFalse(page.contains("/actuator/metrics/cache."));
                assertFalse(page.contains("/actuator/caches"));
                assertEquals(404, get(browser, "/actuator/caches").statusCode());
                Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body());
                assertTrue(token.find());
                assertEquals(403, post(browser, "/rest/clearCache?f=json", "cache=cache-statistics-test").statusCode());
                assertTrue(cache.containsKey(key));
                assertEquals(405, get(browser, "/rest/clearCache?cache=cache-statistics-test&f=json").statusCode());
                for (String method : new String[]{"POST", "PUT", "DELETE", "PATCH"}) {
                    assertEquals(405, browser.send(HttpRequest.newBuilder(URI.create(base() + "/rest/getCacheStatistics"))
                        .header("X-CSRF-TOKEN", token.group(1)).method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
                }
                assertEquals(10, body(clear(browser, token.group(1), "")).path("error").path("code").asInt());
                assertEquals(70, body(clear(browser, token.group(1), "cache=does-not-exist")).path("error").path("code").asInt());
                assertTrue(cache.containsKey(key));
                assertEquals("ok", body(clear(browser, token.group(1), "cache=cache-statistics-test")).path("status").asText());
                assertFalse(cache.containsKey(key));
                assertEquals("untouched", temporary.get("keep"));
                assertEquals("ok", body(clear(browser, token.group(1), "cache=cache-statistics-test")).path("status").asText());
                assertEquals("ok", body(clear(browser, token.group(1), "cache=" + encode(temporaryName))).path("status").asText());
                assertFalse(temporary.containsKey("keep"));
            }
            String auth = "?u=admin&p=admin&c=test&v=" + TestCaseUtils.restApiVersion();
            var xml = get(client, "/rest/getCacheStatistics" + auth);
            var factory = javax.xml.parsers.DocumentBuilderFactory.newDefaultNSInstance();
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var document = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml.body())));
            var nodes = document.getElementsByTagNameNS("http://subsonic.org/restapi", "cache");
            boolean found = false;
            for (int i = 0; i < nodes.getLength(); i++) {
                if (temporaryName.equals(((org.w3c.dom.Element) nodes.item(i)).getAttribute("name"))) found = true;
            }
            assertTrue(found);
        } finally {
            cache.remove(key);
            caches.destroyCache("cache-statistics-test");
            caches.destroyCache(temporaryName);
        }
    }

    private JsonNode cacheRow(HttpClient browser, String name) throws Exception {
        JsonNode rows = body(get(browser, "/rest/getCacheStatistics?f=json")).path("cacheStatistics").path("cache");
        for (JsonNode row : rows) if (name.equals(row.path("name").asText())) return row;
        throw new AssertionError("Missing cache: " + name + " in " + rows);
    }

    private HttpResponse<String> clear(HttpClient browser, String token, String form) throws Exception {
        HttpResponse<String> result = browser.send(HttpRequest.newBuilder(URI.create(base() + "/rest/clearCache?f=json"))
            .header("X-CSRF-TOKEN", token).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(result.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        return result;
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
