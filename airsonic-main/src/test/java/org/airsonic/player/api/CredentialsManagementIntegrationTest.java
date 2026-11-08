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
public class CredentialsManagementIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    @Autowired private ApplicationContext context;
    @Autowired private org.airsonic.player.dao.UserDao userDao;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void manageCredentialsAndAdminSettingsThroughKtor() throws Exception {
        User member = new User("credentials-member", null); member.setRoles(Set.of(User.Role.SETTINGS, User.Role.STREAM));
        security.createUser(member, "secret", "test");
        User outsider = new User("credentials-outsider", null); outsider.setRoles(Set.of(User.Role.STREAM)); security.createUser(outsider, "secret", "test");
        assertFalse(context.containsBean("credentialsManagementController"));
        HttpClient browser = login(member.getUsername(), "secret"); String csrf = csrf(browser);
        HttpClient admin = login("admin", "admin"); String adminCsrf = csrf(admin);
        JsonNode initial = read(member.getUsername(), "secret");
        assertEquals(member.getUsername(), initial.path("username").asText()); assertFalse(initial.has("adminControls"));
        assertTrue(initial.path("credential").get(0).path("id").asText().matches("[0-9a-f]{64}"));
        for (String page : new String[]{"credentialsSettings", "credentialsSettings.view"}) {
            HttpResponse<String> response = get(browser, "/" + page); assertEquals(response.body(), 200, response.statusCode());
            assertTrue(response.body().contains("initCredentialsSettings")); assertFalse(response.body().contains("<form:"));
            assertFalse(response.body().contains("id=\"credentialAdmin\"")); assertFalse(response.body().contains(settings.getJWTKey()));
        }
        assertEquals(200, get(admin, "/credentialsSettings.view").statusCode());
        assertEquals(403, get(login(outsider.getUsername(), "secret"), "/credentialsSettings.view").statusCode());
        error(api("getCredentials", outsider.getUsername(), "secret", null), 50);
        error(api("getCredentialKeys", member.getUsername(), "secret", null), 50);
        for (String operation : new String[]{"createCredential", "updateCredentials", "updateCredentialSettings"}) {
            assertEquals(405, raw(operation, null).statusCode());
            assertEquals(403, post(browser, "/rest/" + operation, "f=json").statusCode());
        }
        assertEquals(405, post(browser, "/credentialsSettings.view", csrf.substring(1)).statusCode());
        assertEquals(404, post(browser, "/credentialsSettings/admin", csrf.substring(1)).statusCode());
        error(website(browser, "updateCredentialSettings", "action=jwtKey&jwtKey=SHOULD_NOT_SAVE" + csrf), 50);
        JsonNode adminInitial = read("admin", "admin"); assertTrue(adminInitial.has("adminControls"));
        assertFalse(adminInitial.toString().contains(settings.getJWTKey()));
        HttpResponse<String> keyResponse = get(admin, "/rest/getCredentialKeys?f=json");
        assertTrue(keyResponse.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        JsonNode keys = mapper.readTree(keyResponse.body()).path("subsonic-response").path("credentialKeys");
        assertEquals(settings.getJWTKey(), keys.path("jwtKey").asText());
        assertEquals(settings.getEncryptionPassword(), keys.path("encryptionKey").asText());
        assertEquals(settings.getEncryptionSalt(), keys.path("encryptionKeySalt").asText());
        assertTrue(get(browser, "/rest/getCredentials?f=json").headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        String create = "app=AIRSONIC&encoder=noop&credential=NEW_PRIVATE_PASSWORD&confirmCredential=NEW_PRIVATE_PASSWORD&username=admin&appUsername=admin&expires=";
        for (String invalid : new String[]{"app=MISSING", "encoder=bad", "encoder=legacynoop", "credential=", "confirmCredential=wrong", "expires=not-a-date"}) {
            error(website(browser, "createCredential", invalid + "&" + create + csrf), 0);
            assertEquals(initial, read(member.getUsername(), "secret"));
        }
        error(website(browser, "createCredential", "app=LASTFM&encoder=bcrypt&credential=x&confirmCredential=x&appUsername=lastfm" + csrf), 0);
        error(website(browser, "createCredential", "app=LASTFM&encoder=noop&credential=x&confirmCredential=x&appUsername=" + csrf), 0);
        ok(website(browser, "createCredential", create + csrf));
        assertEquals(2, security.getCredentials(member.getUsername(), org.airsonic.player.domain.UserCredential.App.AIRSONIC).size());
        JsonNode list = read(member.getUsername(), "secret"); assertFalse(list.toString().contains("NEW_PRIVATE_PASSWORD"));
        JsonNode added = find(list, "AIRSONIC", "noop");
        String addedId = added.path("id").asText();
        assertEquals(member.getUsername(), added.path("username").asText());
        assertEquals(1, read("admin", "admin").path("credential").size());
        String expires = "2035-06-12T10:23:45.123Z";
        ok(website(browser, "updateCredentials", change(addedId, "hex", expires, false) + csrf));
        JsonNode converted = find(read(member.getUsername(), "secret"), "AIRSONIC", "hex");
        assertEquals(expires, converted.path("expires").asText());
        assertNotEquals(addedId, converted.path("id").asText());
        error(website(browser, "updateCredentials", change(addedId, "hex", "", false) + csrf), 70);
        JsonNode outsiderCred = read("admin", "admin").path("credential").get(0);
        error(website(browser, "updateCredentials", change(outsiderCred.path("id").asText(), "bcrypt", "", true) + csrf), 70);
        String convertedId = converted.path("id").asText();
        error(website(browser, "updateCredentials", change(convertedId, "hex", "", false) + "&" + change(convertedId, "hex", "", false) + csrf), 0);
        JsonNode before = read(member.getUsername(), "secret");
        StringBuilder allDelete = new StringBuilder();
        for (JsonNode cred : before.path("credential")) allDelete.append('&').append(change(cred.path("id").asText(), cred.path("encoder").asText(), "", true));
        error(website(browser, "updateCredentials", allDelete + csrf), 0);
        assertEquals(before, read(member.getUsername(), "secret"));
        error(website(browser, "updateCredentials", change(convertedId, "bad", "", false) + csrf), 0);
        error(website(browser, "updateCredentials", change(convertedId, "hex", "bad-date", false) + csrf), 0);
        // Non-decodable credentials can retain their encoder, but cannot be converted without the secret.
        JsonNode original = before.path("credential").get(0);
        error(website(browser, "updateCredentials", change(original.path("id").asText(), "noop", "", false) + csrf), 0);
        // Create and convert third-party records, preserving the app-specific username requirement.
        ok(website(browser, "createCredential", "app=LASTFM&appUsername=" + encode("Last.fm <user>") + "&encoder=noop&credential=THIRD_PARTY_SECRET&confirmCredential=THIRD_PARTY_SECRET" + csrf));
        JsonNode lastfm = find(read(member.getUsername(), "secret"), "LASTFM", "noop");
        error(website(browser, "updateCredentials", change(lastfm.path("id").asText(), "bcrypt", "", false) + csrf), 0);
        ok(website(browser, "updateCredentials", change(lastfm.path("id").asText(), "encrypted-AES-GCM", "", false) + csrf));
        assertNotNull(security.getDecodableCredsForApps(member.getUsername(), org.airsonic.player.domain.UserCredential.App.LASTFM).get(org.airsonic.player.domain.UserCredential.App.LASTFM));
        // A later encoder failure must roll back an earlier successful deletion in the same batch.
        org.airsonic.player.domain.UserCredential corrupt = new org.airsonic.player.domain.UserCredential(member.getUsername(), member.getUsername(), "INVALID_ENCRYPTED_VALUE", "encrypted-AES-GCM", org.airsonic.player.domain.UserCredential.App.LISTENBRAINZ);
        assertTrue(userDao.createCredential(corrupt));
        JsonNode withCorrupt = read(member.getUsername(), "secret");
        JsonNode bad = find(withCorrupt, "LISTENBRAINZ", "encrypted-AES-GCM");
        error(website(browser, "updateCredentials", change(convertedId, "hex", "", true) + "&" + change(bad.path("id").asText(), "noop", "", false) + csrf), 0);
        assertEquals(withCorrupt, read(member.getUsername(), "secret"));
        ok(website(browser, "updateCredentials", change(bad.path("id").asText(), "noop", "", true) + csrf));
        // Re-encoding preserves the actual login password, and the old password can be removed.
        ok(api("getCredentials", member.getUsername(), "NEW_PRIVATE_PASSWORD", null));
        ok(website(browser, "updateCredentials", change(original.path("id").asText(), original.path("encoder").asText(), "", true) + csrf));
        JsonNode singleAirsonic = find(read(member.getUsername(), "NEW_PRIVATE_PASSWORD"), "AIRSONIC", "hex");
        error(website(browser, "updateCredentials", change(singleAirsonic.path("id").asText(), "hex", "", true) + csrf), 0);
        // Admin defaults and migration, including third-party decodable-only records.
        String oldNon = settings.getNonDecodablePasswordEncoder(), oldDec = settings.getDecodablePasswordEncoder();
        boolean oldPrefer = settings.getPreferNonDecodablePasswords();
        String config = "action=encoders&nonDecodableEncoder=bcrypt&decodableEncoder=noop&preferNonDecodable=true";
        error(website(admin, "updateCredentialSettings", "decodableEncoder=bcrypt&" + config + adminCsrf), 0);
        assertEquals(oldDec, settings.getDecodablePasswordEncoder());
        ok(website(admin, "updateCredentialSettings", config + adminCsrf));
        assertEquals("bcrypt", settings.getNonDecodablePasswordEncoder()); assertEquals("noop", settings.getDecodablePasswordEncoder()); assertTrue(settings.getPreferNonDecodablePasswords());
        assertTrue(security.createCredential(new org.airsonic.player.domain.UserCredential(member.getUsername(), member.getUsername(), "LEGACY_PRIVATE", "legacynoop", org.airsonic.player.domain.UserCredential.App.AIRSONIC)));
        assertTrue(security.createCredential(new org.airsonic.player.domain.UserCredential(member.getUsername(), "legacy-lastfm", "LEGACY_LASTFM", "legacyhex", org.airsonic.player.domain.UserCredential.App.LASTFM)));
        ok(website(admin, "updateCredentialSettings", "action=migrateDefault" + adminCsrf));
        assertFalse(security.checkLegacyCredsPresent());
        assertTrue(security.getCredentials(member.getUsername(), org.airsonic.player.domain.UserCredential.App.LASTFM).stream().allMatch(c -> c.getEncoder().equals("noop") || c.getEncoder().equals("encrypted-AES-GCM")));
        assertTrue(security.createCredential(new org.airsonic.player.domain.UserCredential(member.getUsername(), member.getUsername(), "LEGACY_AGAIN", "legacynoop", org.airsonic.player.domain.UserCredential.App.AIRSONIC)));
        ok(website(admin, "updateCredentialSettings", "action=migrateDecodable" + adminCsrf)); assertFalse(security.checkLegacyCredsPresent());
        assertTrue(security.getCredentials(member.getUsername(), org.airsonic.player.domain.UserCredential.App.AIRSONIC).stream().anyMatch(c -> c.getEncoder().equals("noop")));
        String jwt = settings.getJWTKey(), encryption = settings.getEncryptionPassword();
        error(website(admin, "updateCredentialSettings", "action=jwtKey&jwtKey=" + adminCsrf), 0); assertEquals(jwt, settings.getJWTKey());
        error(website(admin, "updateCredentialSettings", "action=encryptionKey&encryptionKey=" + adminCsrf), 0); assertEquals(encryption, settings.getEncryptionPassword());
        ok(website(admin, "updateCredentialSettings", "action=jwtKey&jwtKey=ISOLATED_TEST_JWT_KEY" + adminCsrf)); assertEquals("ISOLATED_TEST_JWT_KEY", settings.getJWTKey());
        ok(website(admin, "updateCredentialSettings", "action=encryptionKey&encryptionKey=ISOLATED_TEST_ENCRYPTION" + adminCsrf)); assertEquals("ISOLATED_TEST_ENCRYPTION", settings.getEncryptionPassword());
        assertFalse(read("admin", "admin").toString().contains("ISOLATED_TEST"));
        // Restore global settings in this isolated context.
        settings.setJWTKey(jwt); settings.setEncryptionPassword(encryption); settings.setNonDecodablePasswordEncoder(oldNon); settings.setDecodablePasswordEncoder(oldDec); settings.setPreferNonDecodablePasswords(oldPrefer); settings.save();
        String xml = get(browser, "/rest/getCredentials").body();
        assertTrue(xml.contains("credentialManagement")); assertFalse(xml.contains("THIRD_PARTY_SECRET")); assertFalse(xml.contains("NEW_PRIVATE_PASSWORD"));
        assertFalse(read(member.getUsername(), "NEW_PRIVATE_PASSWORD").toString().contains("THIRD_PARTY_SECRET"));
    }

    private JsonNode find(JsonNode list, String app, String encoder) {
        for (JsonNode value : list.path("credential")) if (app.equals(value.path("app").asText()) && encoder.equals(value.path("encoder").asText())) return value;
        throw new AssertionError("Missing " + app + "/" + encoder);
    }
    private String change(String id, String encoder, String expires, boolean delete) {
        return "id=" + encode(id) + "&encoder[" + id + "]=" + encode(encoder) + "&expires[" + id + "]=" + encode(expires) + "&delete[" + id + "]=" + delete;
    }
    private String csrf(HttpClient browser) throws Exception {
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body()); assertTrue(token.find()); return "&_csrf=" + encode(token.group(1));
    }
    private JsonNode read(String username, String password) throws Exception {
        JsonNode result = api("getCredentials", username, password, null); ok(result); return result.path("credentialManagement");
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
