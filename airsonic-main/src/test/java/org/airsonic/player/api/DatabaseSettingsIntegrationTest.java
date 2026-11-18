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
public class DatabaseSettingsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean private org.airsonic.player.service.DatabaseService database;
    @Autowired private org.airsonic.player.service.StatusService statuses;

    @Test public void administerDatabaseThroughKtor() throws Exception {
        // Keep the datasource in memory, but let settings edits read the persisted configuration.
        org.springframework.mock.env.MockEnvironment editable = new org.springframework.mock.env.MockEnvironment();
        editable.getPropertySources().addFirst(new org.apache.commons.configuration2.spring.ConfigurationPropertySource(
            "editable-settings", org.airsonic.player.service.ConfigurationPropertiesService.getInstance().getConfiguration()));
        editable.getPropertySources().addLast(new org.springframework.core.env.MapPropertySource("database-defaults", java.util.Map.of(
            SettingsService.KEY_DATABASE_URL, SettingsService.getDefaultJDBCUrl(),
            SettingsService.KEY_DATABASE_USERNAME, SettingsService.getDefaultJDBCUsername(),
            SettingsService.KEY_DATABASE_PASSWORD, SettingsService.getDefaultJDBCPassword())));
        settings.setPropertyLookup(editable::getProperty); settings.resetDatabaseToDefault();
        User member = new User("db-member", null);
        member.setRoles(Set.of(User.Role.SETTINGS)); security.createUser(member, "secret", "test");
        assertFalse(context.containsBean("databaseSettingsController"));
        JsonNode initial = read("admin", "admin"); assertFalse(initial.has("password"));
        HttpClient browser = login("admin", "admin");
        Matcher token = Pattern.compile("csrfToken: \\\"([^\\\"]+)\\\"").matcher(get(browser, "/top.view").body());
        assertTrue(token.find()); String csrf = "&_csrf=" + encode(token.group(1));
        for (String page : new String[]{"databaseSettings", "databaseSettings.view"}) {
            HttpResponse<String> response = get(browser, "/" + page);
            assertEquals(response.body(), 200, response.statusCode());
            assertTrue(response.body().contains("initDatabaseSettings"));
            assertFalse(response.body().contains("<form:")); assertFalse(response.body().contains("upload.view"));
        }
        assertEquals(403, get(login("db-member", "secret"), "/databaseSettings.view").statusCode());
        error(api("getDatabaseSettings", "db-member", "secret", null), 50);
        HttpClient memberBrowser = login("db-member", "secret");
        Matcher memberToken = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(memberBrowser, "/top.view").body());
        assertTrue(memberToken.find());
        for (String name : new String[]{"saveDatabaseSettings", "backupDatabase", "exportDatabase", "importDatabase"}) {
            assertEquals(405, raw(name, null).statusCode());
            assertEquals(403, post(browser, "/rest/" + name, "f=json").statusCode());
            error(website(memberBrowser, name, "&_csrf=" + encode(memberToken.group(1))), 50);
        }
        for (String old : new String[]{"/databaseSettings/backup", "/databaseSettings/export"}) assertEquals(404, get(browser, old).statusCode());
        assertEquals(405, post(browser, "/databaseSettings.view", csrf.substring(1)).statusCode());
        String base = "configType=BUILTIN&dbBackupInterval=-1&dbBackupRetentionCount=-1";
        for (String invalid : new String[]{"configType=BAD", "dbBackupInterval=0", "dbBackupInterval=bad", "dbBackupRetentionCount=-2",
                "configType=EXTERNAL&driver=&url=jdbc:test&username=x&mysqlVarcharMaxlength=128",
                "configType=EXTERNAL&driver=test&url=invalid&username=x&mysqlVarcharMaxlength=128",
                "configType=JNDI&jndiName=&mysqlVarcharMaxlength=128", "configType=JNDI&jndiName=jdbc/music&mysqlVarcharMaxlength=0"}) {
            error(website(browser, "saveDatabaseSettings", invalid + "&password=DO_NOT_SAVE&" + base + csrf), 0);
            assertEquals(initial, read("admin", "admin"));
        }
        error(website(browser, "saveDatabaseSettings", csrf), 0);
        ok(website(browser, "saveDatabaseSettings", "configType=EXTERNAL&driver=org.example.Driver&url=jdbc:test&username=" + encode("<user & name>")
            + "&password=" + encode("secret <db>") + "&mysqlVarcharMaxlength=128&dbBackupInterval=3&dbBackupRetentionCount=7" + csrf));
        JsonNode external = read("admin", "admin");
        assertEquals("EXTERNAL", external.path("configType").asText()); assertEquals("<user & name>", external.path("username").asText());
        assertFalse(external.has("password")); assertFalse(external.toString().contains("secret <db>"));
        assertEquals("secret <db>", settings.getDatabasePassword());
        ok(website(browser, "saveDatabaseSettings", payload(external) + "&password=" + csrf));
        assertEquals("secret <db>", settings.getDatabasePassword());
        ok(website(browser, "saveDatabaseSettings", payload(external) + csrf));
        assertEquals("secret <db>", settings.getDatabasePassword());
        error(website(browser, "saveDatabaseSettings", "clearPassword=yes&" + payload(external) + csrf), 0);
        error(website(browser, "saveDatabaseSettings", "clearPassword=true&password=conflicting&" + payload(external) + csrf), 0);
        assertEquals("secret <db>", settings.getDatabasePassword());
        ok(website(browser, "saveDatabaseSettings", "clearPassword=true&" + payload(external) + csrf));
        assertEquals("", settings.getDatabasePassword());
        ok(website(browser, "saveDatabaseSettings", "configType=JNDI&jndiName=java:comp/env/jdbc/Music&mysqlVarcharMaxlength=255&dbBackupInterval=-1&dbBackupRetentionCount=1" + csrf));
        assertEquals("JNDI", read("admin", "admin").path("configType").asText());
        error(website(browser, "backupDatabase", csrf), 0);
        ok(website(browser, "saveDatabaseSettings", base + csrf));
        assertEquals(SettingsService.getDefaultJDBCPassword(), settings.getDatabasePassword());
        assertEquals(SettingsService.getDefaultJDBCUrl(), settings.getDatabaseUrl());
        String xml = get(client, "/rest/getDatabaseSettings?u=admin&p=admin&v=" + TestCaseUtils.restApiVersion() + "&c=test").body();
        assertTrue(xml.contains("<databaseSettings")); assertFalse(xml.contains("password="));
        assertTrue(get(browser, "/rest/getDatabaseSettings?f=json").headers().firstValue("Cache-Control").orElse("").contains("no-store"));

        // Real export exercises ZIP streaming, accounting, status reporting and temporary-file cleanup.
        long beforeBytes = security.getUserByName("admin").getBytesDownloaded();
        HttpResponse<byte[]> exported = browser.send(HttpRequest.newBuilder(URI.create(base() + "/rest/exportDatabase?f=json"))
            .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(csrf.substring(1))).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(new String(exported.body(), StandardCharsets.UTF_8), 200, exported.statusCode());
        assertTrue(exported.headers().firstValue("Content-Type").orElse("").startsWith("application/zip"));
        assertTrue(exported.headers().firstValue("Content-Disposition").orElse("").contains("attachment"));
        assertTrue(exported.body().length > 0);
        java.util.Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(exported.body()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), zip.readAllBytes());
        }
        assertTrue(entries.keySet().toString(), entries.size() >= 2); assertTrue(entries.containsKey("0.xml")); assertTrue(entries.containsKey("1.xml"));
        assertEquals(beforeBytes + exported.body().length, security.getUserByName("admin").getBytesDownloaded());
        assertTrue(statuses.getAllDownloadStatuses().isEmpty()); assertTrue(database.getExportStatus().version() > 0);
        assertFalse(database.getExportStatus().failed()); assertCleanBackups();

        // Real round trip through Liquibase on the isolated in-memory database.
        member.setEmail("changed-after-export@example.test"); security.updateUser(member);
        org.mockito.Mockito.doReturn(false).when(database).backuppable();
        ok(multipart(browser, token.group(1), new String[]{"export.zip"}, new byte[][]{exported.body()}));
        assertTrue(database.getImportStatus().version() > 0); assertFalse(database.getImportStatus().failed());
        assertNotNull(security.getUserByName("db-member"));
        assertNull(context.getBean(org.airsonic.player.dao.UserDao.class).getUserByName("db-member", true).getEmail());
        assertCleanBackups();
        org.mockito.Mockito.verify(database).backup();
        // Native HSQL file backup cannot run on the in-memory fixture; verify its HTTP boundary.
        org.mockito.Mockito.doReturn(true).when(database).backuppable();
        org.mockito.Mockito.doNothing().when(database).backup();
        ok(website(browser, "backupDatabase", csrf));
        org.mockito.Mockito.verify(database, org.mockito.Mockito.times(2)).backup();

        // Verify import boundary using a spy so malformed requests cannot reach the destructive service.
        java.util.concurrent.atomic.AtomicInteger imported = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            java.nio.file.Path folder = invocation.getArgument(0);
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(folder)) {
                assertEquals(entries.size(), files.count());
            }
            assertArrayEquals(entries.get("0.xml"), java.nio.file.Files.readAllBytes(folder.resolve("0.xml")));
            imported.incrementAndGet(); return null;
        }).when(database).importDB(org.mockito.ArgumentMatchers.any(java.nio.file.Path.class));
        ok(multipart(browser, token.group(1), new String[]{"export.zip"}, new byte[][]{exported.body()}));
        assertEquals(1, imported.get()); assertCleanBackups();
        ok(multipart(browser, token.group(1), entries.keySet().toArray(String[]::new), entries.values().toArray(byte[][]::new)));
        assertEquals(2, imported.get()); assertCleanBackups();
        for (String name : new String[]{"../0.xml", "file.txt", "C:\\0.xml"}) {
            error(multipart(browser, token.group(1), new String[]{name}, new byte[][]{entries.get("0.xml")}), 0); assertCleanBackups();
        }
        error(multipart(browser, token.group(1), new String[]{"bad.zip"}, new byte[][]{"invalid".getBytes(StandardCharsets.UTF_8)}), 0);
        error(multipart(browser, token.group(1), new String[]{"bad.xml"}, new byte[][]{"<bad/>".getBytes(StandardCharsets.UTF_8)}), 0);
        error(multipart(browser, token.group(1), new String[]{"bad.xml"}, new byte[][]{"<!DOCTYPE a SYSTEM 'file:///etc/passwd'><databaseChangeLog/>".getBytes(StandardCharsets.UTF_8)}), 0);
        error(multipart(browser, token.group(1), new String[]{"0.xml", "0.xml"}, new byte[][]{entries.get("0.xml"), entries.get("0.xml")}), 0);
        byte[] traversal;
        try (java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream(); java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(data)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("../escaped.xml")); zip.write(entries.get("0.xml")); zip.closeEntry(); zip.finish(); traversal = data.toByteArray();
        }
        error(multipart(browser, token.group(1), new String[]{"escape.zip"}, new byte[][]{traversal}), 0);
        assertEquals(2, imported.get()); assertCleanBackups();
        org.mockito.Mockito.doThrow(new IllegalStateException("sensitive JDBC details")).when(database).importDB(org.mockito.ArgumentMatchers.any(java.nio.file.Path.class));
        JsonNode failure = multipart(browser, token.group(1), new String[]{"export.zip"}, new byte[][]{exported.body()});
        error(failure, 0); assertFalse(failure.toString().contains("sensitive")); assertCleanBackups();
        // Reproduce production startup defaults shadowing the persisted JDBC values until restart.
        editable.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("startup-defaults", java.util.Map.of(
            SettingsService.KEY_DATABASE_URL, SettingsService.getDefaultJDBCUrl(),
            SettingsService.KEY_DATABASE_USERNAME, SettingsService.getDefaultJDBCUsername(),
            SettingsService.KEY_DATABASE_PASSWORD, SettingsService.getDefaultJDBCPassword(),
            "spring.liquibase.parameters.mysqlVarcharLimit", 512)));
        ok(website(browser, "saveDatabaseSettings", "configType=EXTERNAL&driver=org.example.Driver&url=jdbc:pending&username=pending&password=pending-secret&mysqlVarcharMaxlength=128&dbBackupInterval=-1&dbBackupRetentionCount=1" + csrf));
        JsonNode pending = read("admin", "admin");
        assertEquals("jdbc:pending", pending.path("url").asText()); assertEquals("pending", pending.path("username").asText());
        assertEquals("EXTERNAL", pending.path("configType").asText()); assertEquals(128, pending.path("mysqlVarcharMaxlength").asInt());
        ok(website(browser, "saveDatabaseSettings", payload(pending) + csrf));
        assertEquals("pending-secret", org.airsonic.player.service.ConfigurationPropertiesService.getInstance().getProperty(SettingsService.KEY_DATABASE_PASSWORD));
        ok(website(browser, "saveDatabaseSettings", base + csrf));

    }

    private void assertCleanBackups() throws Exception {
        java.nio.file.Path folder = SettingsService.getAirsonicHome().resolve("backups");
        if (java.nio.file.Files.exists(folder)) try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(folder)) {
            assertEquals(0, files.count());
        }
    }
    private JsonNode multipart(HttpClient browser, String csrf, String[] names, byte[][] values) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < names.length; i++) {
            out.write(("--database-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + names[i] + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(values[i]); out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write("--database-boundary--\r\n".getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> response = browser.send(HttpRequest.newBuilder(URI.create(base() + "/rest/importDatabase?f=json"))
            .header("X-CSRF-TOKEN", csrf).header("Content-Type", "multipart/form-data; boundary=database-boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
    private String payload(JsonNode data) {
        StringBuilder body = new StringBuilder();
        data.fields().forEachRemaining(entry -> body.append('&').append(entry.getKey()).append('=').append(encode(entry.getValue().asText())));
        return body.toString();
    }

    private JsonNode read(String user, String password) throws Exception {
        JsonNode body = api("getDatabaseSettings", user, password, null);
        ok(body); return body.path("databaseSettings");
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
        HttpResponse<String> response = post(browser, "/rest/" + operation + "?f=json", "f=json&c=airsonic-web&" + body);
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
