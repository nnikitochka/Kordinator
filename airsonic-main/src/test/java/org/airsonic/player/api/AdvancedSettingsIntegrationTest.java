package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
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
public class AdvancedSettingsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void administerAdvancedSettingsViaKtor() throws Exception {
        settings.setLdapManagerPassword("original ldap"); settings.setSmtpPassword("original smtp"); settings.setRecaptchaSecretKey("original captcha");
        settings.save();
        User member = new User("advanced-member", null);
        member.setRoles(Set.of(User.Role.SETTINGS, User.Role.STREAM));
        security.createUser(member, "secret", "test");
        assertFalse(context.containsBean("advancedSettingsController"));
        JsonNode initial = read("admin", "admin");
        noSecrets(initial);
        error(api("getAdvancedSettings", "advanced-member", "secret", null), 50);
        assertEquals(405, raw("saveAdvancedSettings", null).statusCode());
        HttpClient browser = login("admin", "admin");
        for (String page : new String[]{"advancedSettings", "advancedSettings.view"}) {
            HttpResponse<String> response = get(browser, "/" + page);
            assertEquals(response.body(), 200, response.statusCode());
            assertTrue(response.body().contains("initAdvancedSettings"));
            assertFalse(response.body().contains("<form:"));
            assertFalse(response.body().contains("original ldap"));
            assertFalse(response.body().contains("original smtp"));
            assertFalse(response.body().contains("original captcha"));
        }
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body());
        assertTrue(token.find());
        String csrf = "&_csrf=" + encode(token.group(1));
        String base = payload(initial);
        assertEquals(403, post(browser, "/rest/saveAdvancedSettings", "f=json" + base).statusCode());
        assertEquals(405, post(browser, "/advancedSettings.view", "_csrf=" + encode(token.group(1))).statusCode());
        HttpClient memberBrowser = login("advanced-member", "secret");
        assertEquals(403, get(memberBrowser, "/advancedSettings.view").statusCode());
        Matcher memberToken = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(memberBrowser, "/top.view").body());
        assertTrue(memberToken.find());
        error(website(memberBrowser, "saveAdvancedSettings", base + "&_csrf=" + encode(memberToken.group(1))), 50);
        for (String invalid : new String[]{"downloadLimit=-1", "downloadLimit=bad", "uploadLimit=-1", "uploadLimit=9223372036854775808",
                "smtpPort=0", "smtpPort=65536", "smtpPort=bad", "smtpEncryption=unknown", "ldapEnabled=yes", "ldapAutoShadowing=1", "captchaEnabled=yes"}) {
            error(website(browser, "saveAdvancedSettings", invalid + "&smtpServer=SHOULD_NOT_SAVE&smtpPassword=SHOULD_NOT_SAVE" + base + csrf), 0);
            assertEquals(initial, read("admin", "admin"));
            assertEquals("original smtp", settings.getSmtpPassword());
        }
        error(website(browser, "saveAdvancedSettings", csrf), 0);
        String changed = "downloadLimit=4&uploadLimit=8&smtpServer=" + encode("smtp.<REST & Ktor>")
                + "&smtpPort=587&smtpEncryption=STARTTLS&smtpUser=mailer&smtpFrom=" + encode("Music <mail@example.org>")
                + "&ldapEnabled=true&ldapAutoShadowing=true&ldapUrl=" + encode("ldap://localhost:389/dc=test")
                + "&ldapSearchFilter=" + encode("(uid={0})") + "&ldapManagerDn=" + encode("cn=manager,dc=test")
                + "&captchaEnabled=true&recaptchaSiteKey=site-key&smtpPassword=" + encode(" new smtp <&> ")
                + "&ldapManagerPassword=" + encode(" new ldap <&> ") + "&recaptchaSecretKey=" + encode(" new captcha <&> ");
        ok(website(browser, "saveAdvancedSettings", changed + base + csrf));
        assertEquals(4, settings.getDownloadBitrateLimit()); assertEquals(8, settings.getUploadBitrateLimit());
        assertEquals(4096.0, settings.getDownloadBitrateLimiter().getRate(), 0.0);
        assertEquals(8192.0, settings.getUploadBitrateLimiter().getRate(), 0.0);
        assertTrue(settings.isLdapEnabled()); assertTrue(settings.isLdapAutoShadowing()); assertTrue(settings.isCaptchaEnabled());
        assertEquals("ldap://localhost:389/dc=test", settings.getLdapUrl()); assertEquals("(uid={0})", settings.getLdapSearchFilter());
        assertEquals("cn=manager,dc=test", settings.getLdapManagerDn()); assertEquals("site-key", settings.getRecaptchaSiteKey());
        assertEquals(" new smtp <&> ", settings.getSmtpPassword()); assertEquals(" new ldap <&> ", settings.getLdapManagerPassword());
        assertEquals(" new captcha <&> ", settings.getRecaptchaSecretKey());
        JsonNode saved = read("admin", "admin"); noSecrets(saved);
        assertEquals("smtp.<REST & Ktor>", saved.path("smtpServer").asText());
        assertEquals("587", saved.path("smtpPort").asText()); assertEquals("STARTTLS", saved.path("smtpEncryption").asText());
        assertEquals("mailer", saved.path("smtpUser").asText()); assertEquals("Music <mail@example.org>", saved.path("smtpFrom").asText());
        String xml = get(client, "/rest/getAdvancedSettings?u=admin&p=admin&v=" + TestCaseUtils.restApiVersion() + "&c=test").body();
        assertFalse(xml.contains("smtpPassword")); assertFalse(xml.contains("ldapManagerPassword")); assertFalse(xml.contains("recaptchaSecretKey"));
        org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        assertEquals("smtp.<REST & Ktor>", ((org.w3c.dom.Element) document.getElementsByTagName("advancedSettings").item(0)).getAttribute("smtpServer"));
        // Blank credentials retain the old values; zero limits mean unlimited.
        ok(website(browser, "saveAdvancedSettings", "downloadLimit=0&uploadLimit=0&ldapEnabled=false&captchaEnabled=false&smtpServer=&smtpPassword=&ldapManagerPassword=&recaptchaSecretKey=" + payload(saved) + csrf));
        assertEquals(Double.POSITIVE_INFINITY, settings.getDownloadBitrateLimiter().getRate(), 0.0);
        assertEquals(Double.POSITIVE_INFINITY, settings.getUploadBitrateLimiter().getRate(), 0.0);
        assertEquals(" new smtp <&> ", settings.getSmtpPassword()); assertEquals(" new ldap <&> ", settings.getLdapManagerPassword());
        assertEquals(" new captcha <&> ", settings.getRecaptchaSecretKey());
        assertEquals("", settings.getSmtpServer());
        // Omitted credentials also retain the stored values.
        ok(website(browser, "saveAdvancedSettings", payload(read("admin", "admin")) + csrf));
        assertEquals(" new smtp <&> ", settings.getSmtpPassword()); assertEquals(" new ldap <&> ", settings.getLdapManagerPassword());
        assertEquals(" new captcha <&> ", settings.getRecaptchaSecretKey());
    }

    private void noSecrets(JsonNode data) {
        for (String field : new String[]{"smtpPassword", "ldapManagerPassword", "recaptchaSecretKey"}) assertFalse(data.has(field));
        assertFalse(data.toString().contains("original")); assertFalse(data.toString().contains(" new "));
    }
    private String payload(JsonNode data) {
        StringBuilder body = new StringBuilder();
        data.fields().forEachRemaining(entry -> body.append('&').append(entry.getKey()).append('=').append(encode(entry.getValue().asText())));
        return body.toString();
    }

    private JsonNode read(String user, String password) throws Exception {
        JsonNode body = api("getAdvancedSettings", user, password, null);
        ok(body); return body.path("advancedSettings");
    }
    private HttpResponse<String> raw(String operation, String body) throws Exception {
        String path = "/rest/" + operation + "?u=admin&p=admin&v=" + TestCaseUtils.restApiVersion() + "&c=test&f=json";
        return body == null ? get(client, path) : post(client, path, body);
    }
    private JsonNode api(String operation, String user, String password, String body) throws Exception {
        String path = "/rest/" + operation + "?u=" + user + "&p=" + password + "&v=" + TestCaseUtils.restApiVersion() + "&c=test&f=json";
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
