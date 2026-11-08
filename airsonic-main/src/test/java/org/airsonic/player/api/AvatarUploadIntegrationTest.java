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
public class AvatarUploadIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private SettingsService settings;
    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void uploadAvatarsViaKtor() throws Exception {
        User member = new User("avatar-member", null); member.setRoles(Set.of(User.Role.SETTINGS, User.Role.STREAM));
        security.createUser(member, "secret", "test");
        User outsider = new User("avatar-outsider", null); outsider.setRoles(Set.of(User.Role.STREAM)); security.createUser(outsider, "secret", "test");
        assertFalse(context.containsBean("avatarUploadController"));
        HttpClient browser = login("avatar-member", "secret");
        String csrf = csrf(browser);
        assertEquals(405, raw("uploadAvatar", null).statusCode());
        byte[] small = image(32, 16, "png");
        assertEquals(403, multipart(browser, "?f=json", null, "file", "small.png", small).statusCode());
        JsonNode denied = upload(login("avatar-outsider", "secret"), null, "file", "small.png", small);
        error(denied, 50); assertNull(settings.getCustomAvatar("avatar-outsider"));
        assertEquals(404, post(browser, "/avatarUpload.view?_csrf=" + encode(csrf), "").statusCode());
        assertEquals(404, post(browser, "/avatarUpload?_csrf=" + encode(csrf), "").statusCode());
        error(parse(post(browser, "/rest/uploadAvatar?f=json", "_csrf=" + encode(csrf))), 0);
        // The extension is untrusted: content is decoded and converted to PNG.
        JsonNode first = upload(browser, csrf, "file", "misleading.jpg", small); ok(first);
        assertFalse(first.path("avatarUpload").path("resized").asBoolean());
        assertEquals(32, first.path("avatarUpload").path("avatar").path("width").asInt());
        org.airsonic.player.domain.Avatar avatar = settings.getCustomAvatar("avatar-member");
        assertEquals("image/png", avatar.getMimeType()); assertEquals("misleading.jpg", avatar.getName());
        assertTrue(java.nio.file.Files.exists(avatar.getPath()));
        assertEquals(org.airsonic.player.domain.AvatarScheme.NONE, settings.getUserSettings("avatar-member").getAvatarScheme());
        assertNull(settings.getCustomAvatar("admin"));
        java.nio.file.Path firstPath = avatar.getPath();
        byte[] displayed = getBytes(browser, "/rest/getAvatar?username=avatar-member&forceCustom=true&c=test");
        java.awt.image.BufferedImage decoded = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(displayed));
        assertEquals(32, decoded.getWidth()); assertEquals(16, decoded.getHeight()); assertEquals(0, decoded.getRGB(0, 0) >>> 24);
        // Landscape with transparency, portrait JPEG, and extreme aspect ratio.
        for (Object[] sample : new Object[][]{{160, 80, "png", 64, 32}, {80, 160, "jpeg", 32, 64}, {1, 1000, "png", 1, 64}}) {
            JsonNode result = upload(browser, csrf, "file", "portrait.jpg", image((int) sample[0], (int) sample[1], (String) sample[2])); ok(result);
            assertTrue(result.path("avatarUpload").path("resized").asBoolean());
            avatar = settings.getCustomAvatar("avatar-member");
            assertEquals((int) sample[3], avatar.getWidth()); assertEquals((int) sample[4], avatar.getHeight());
            decoded = javax.imageio.ImageIO.read(avatar.getPath().toFile());
            assertEquals(avatar.getWidth(), decoded.getWidth()); assertEquals(avatar.getHeight(), decoded.getHeight());
        }
        assertFalse(java.nio.file.Files.exists(firstPath));
        java.nio.file.Path goodPath = avatar.getPath(); byte[] goodBytes = java.nio.file.Files.readAllBytes(goodPath);
        for (byte[] invalid : new byte[][]{new byte[0], "NOT AN IMAGE".getBytes(StandardCharsets.UTF_8), new byte[5 * 1024 * 1024 + 1], oversizedDimensions()}) {
            error(upload(browser, csrf, "file", "bad.png", invalid), 0);
            assertEquals(goodPath, settings.getCustomAvatar("avatar-member").getPath());
            assertArrayEquals(goodBytes, java.nio.file.Files.readAllBytes(goodPath));
        }
        error(upload(browser, csrf, "wrong-field", "image.png", small), 0);
        error(upload(browser, csrf, "file", "", small), 0);
        // A second file is rejected before any avatar or file is replaced.
        error(parse(sendMultipart(browser, "?f=json", csrf, part("file", "one.png", small), part("file", "two.png", small))), 0);
        error(parse(sendMultipart(browser, "?f=json", csrf)), 0);
        assertEquals(goodPath, settings.getCustomAvatar("avatar-member").getPath());
        // Filenames and supplied usernames never select a filesystem path or a different account.
        ok(upload(browser, csrf, "file", "../../outside.png", small));
        avatar = settings.getCustomAvatar("avatar-member");
        assertEquals("outside.png", avatar.getName()); assertEquals(goodPath.getParent(), avatar.getPath().getParent());
        String xml = sendMultipart(browser, "?c=test&username=admin", csrf, part("file", "xml.png", small)).body();
        org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        assertEquals("ok", document.getDocumentElement().getAttribute("status"));
        assertEquals("xml.png", ((org.w3c.dom.Element) document.getElementsByTagName("avatar").item(0)).getAttribute("name"));
        assertFalse(xml.contains(SettingsService.getAirsonicHome().toString()));
        assertNull(settings.getCustomAvatar("admin"));
        // The file is ready for selection and display via the existing personal settings API.
        JsonNode personal = api("getPersonalSettings", "avatar-member", "secret", null).path("personalSettings");
        assertEquals("xml.png", personal.path("customAvatar").path("name").asText());
        assertEquals(-1, personal.path("avatarId").asInt());
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(avatar.getPath().getParent())) {
            assertEquals(1, files.count());
        }
    }

    private byte[] image(int width, int height, String type) throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
                type.equals("jpeg") ? java.awt.image.BufferedImage.TYPE_INT_RGB : java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); assertTrue(javax.imageio.ImageIO.write(image, type, out)); return out.toByteArray();
    }
    private byte[] oversizedDimensions() throws Exception {
        byte[] png = image(1, 1, "png");
        java.nio.ByteBuffer.wrap(png, 16, 8).putInt(100_000).putInt(100_000);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(png, 12, 17);
        java.nio.ByteBuffer.wrap(png, 29, 4).putInt((int) crc.getValue()); return png;
    }
    private String csrf(HttpClient browser) throws Exception {
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body()); assertTrue(token.find()); return token.group(1);
    }
    private JsonNode upload(HttpClient browser, String token, String field, String name, byte[] data) throws Exception {
        return parse(multipart(browser, "?f=json&username=admin", token == null ? csrf(browser) : token, field, name, data));
    }
    private JsonNode parse(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode()); return mapper.readTree(response.body()).path("subsonic-response");
    }
    private HttpResponse<String> multipart(HttpClient browser, String query, String token, String field, String name, byte[] data) throws Exception {
        return sendMultipart(browser, query, token, part(field, name, data));
    }
    private byte[] part(String field, String name, byte[] bytes) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(("--AvatarTestBoundary\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\"" + name + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes); out.write("\r\n".getBytes(StandardCharsets.UTF_8)); return out.toByteArray();
    }
    private HttpResponse<String> sendMultipart(HttpClient browser, String query, String token, byte[]... parts) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); for (byte[] part : parts) out.write(part);
        out.write("--AvatarTestBoundary--\r\n".getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder request = HttpRequest.newBuilder().timeout(java.time.Duration.ofSeconds(30)).uri(URI.create(base() + "/rest/uploadAvatar" + query))
                .header("Content-Type", "multipart/form-data; boundary=AvatarTestBoundary");
        if (token != null) request.header("X-CSRF-TOKEN", token);
        return browser.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }
    private byte[] getBytes(HttpClient browser, String path) throws Exception {
        HttpResponse<byte[]> response = browser.send(HttpRequest.newBuilder().timeout(java.time.Duration.ofSeconds(30)).uri(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode()); assertEquals("image/png", response.headers().firstValue("Content-Type").orElse("")); return response.body();
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
        return browser.send(HttpRequest.newBuilder().timeout(java.time.Duration.ofSeconds(30)).uri(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient browser, String path, String form) throws Exception {
        return browser.send(HttpRequest.newBuilder().timeout(java.time.Duration.ofSeconds(30)).uri(URI.create(base() + path)).header("Content-Type", "application/x-www-form-urlencoded")
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
