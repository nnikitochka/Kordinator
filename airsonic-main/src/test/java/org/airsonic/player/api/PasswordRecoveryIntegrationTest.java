package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
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

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PasswordRecoveryIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private ApplicationContext context;
    @Autowired private SettingsService settings;
    @Autowired private SecurityService security;
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Test public void anonymousRecoveryUsesKtorCsrfAndSmtpWithoutChangingSubsonicAuthentication() throws Exception {
        assertFalse(context.containsBean("recoverController"));
        settings.setCaptchaEnabled(false);
        User member = new User("recovery-member", "member@example.test");
        security.createUser(member, "old-password", "test");
        int credentialsBefore = security.getCredentials(member.getUsername(), UserCredential.App.AIRSONIC).size();
        for (String page : List.of("/recover", "/recover.view")) {
            var response = send(page, "GET", null, null);
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("/airsonic/rest/recover"));
            assertTrue(response.body().contains("password-recovery.js?v=1"));
        }
        var configResponse = send("/rest/recover?usernameOrEmail=recovery-member", "GET", null, null);
        assertEquals(200, configResponse.statusCode());
        assertTrue(configResponse.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        JsonNode config = json.readTree(configResponse.body());
        assertFalse(config.has("sentTo"));
        assertFalse(config.has("error"));
        assertFalse(config.has("recaptchaSecretKey"));
        assertFalse(config.path("csrfToken").asText().isEmpty());
        assertEquals(credentialsBefore, security.getCredentials(member.getUsername(), UserCredential.App.AIRSONIC).size());
        var head = send("/rest/recover?usernameOrEmail=recovery-member", "HEAD", null, null);
        assertEquals(200, head.statusCode()); assertEquals("", head.body());
        assertEquals(403, send("/rest/recover", "POST", "usernameOrEmail=recovery-member", null).statusCode());
        assertEquals("recover.error.usernotfound", error(post("missing", config)));
        assertEquals("recover.error.noemail", error(post("admin", config)));
        settings.setSmtpServer("");
        assertEquals("recover.error.sendfailed", error(post("recovery-member", config)));
        assertEquals(credentialsBefore, security.getCredentials(member.getUsername(), UserCredential.App.AIRSONIC).size());
        settings.setCaptchaEnabled(true);
        settings.setRecaptchaSiteKey("public-key"); settings.setRecaptchaSecretKey("private-key");
        var captchaConfig = send("/rest/recover", "GET", null, null);
        assertEquals("public-key", json.readTree(captchaConfig.body()).path("recaptchaSiteKey").asText());
        assertFalse(captchaConfig.body().contains("private-key"));
        assertEquals("recover.error.invalidcaptcha", error(post("recovery-member", config)));
        settings.setCaptchaEnabled(false);
        assertEquals(405, send("/rest/recover", "DELETE", null, config).statusCode());
        assertEquals(405, send("/recover.view", "POST", "usernameOrEmail=recovery-member", config).statusCode());
        member.setLdapAuthenticated(true); security.updateUser(member);
        try (ServerSocket smtp = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            smtp.setSoTimeout(10000);
            settings.setSmtpServer("127.0.0.1"); settings.setSmtpPort(Integer.toString(smtp.getLocalPort()));
            settings.setSmtpEncryption("None"); settings.setSmtpUser(""); settings.setSmtpFrom("server@example.test");
            CompletableFuture<String> mail = CompletableFuture.supplyAsync(() -> receiveMail(smtp));
            var sent = post("member@example.test", config);
            assertEquals(200, sent.statusCode());
            assertEquals("member@example.test", json.readTree(sent.body()).path("sentTo").asText());
            String raw = mail.get(10, TimeUnit.SECONDS);
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1)));
            assertEquals("Airsonic Password", message.getSubject());
            String body = message.getContent().toString();
            var password = Pattern.compile("Password: ([a-zA-Z0-9]{32})").matcher(body);
            assertTrue(password.find());
            assertFalse(sent.body().contains(password.group(1)));
            assertEquals(credentialsBefore + 1, security.getCredentials(member.getUsername(), UserCredential.App.AIRSONIC).size());
            assertFalse(security.getUserByName(member.getUsername()).isLdapAuthenticated());
            for (String secret : List.of("old-password", password.group(1))) {
                String authorization = "Basic " + Base64.getEncoder().encodeToString(("recovery-member:" + secret).getBytes(StandardCharsets.UTF_8));
                var ping = client.send(HttpRequest.newBuilder(uri("/rest/ping.view?f=json")).header("Authorization", authorization).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals("ok", json.readTree(ping.body()).path("subsonic-response").path("status").asText());
            }
        }
        assertEquals(credentialsBefore + 1, security.getCredentials(member.getUsername(), UserCredential.App.AIRSONIC).size());
        var anonymousPing = HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/rest/ping.view?f=json")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals("failed", json.readTree(anonymousPing.body()).path("subsonic-response").path("status").asText());
    }

    private String error(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode()); return json.readTree(response.body()).path("error").asText();
    }
    private HttpResponse<String> post(String identifier, JsonNode config) throws Exception {
        return send("/rest/recover", "POST", "usernameOrEmail=" + URLEncoder.encode(identifier, StandardCharsets.UTF_8), config);
    }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + "/airsonic" + path); }
    private HttpResponse<String> send(String path, String method, String body, JsonNode config) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20));
        if (config != null) request.header(config.path("csrfHeader").asText(), config.path("csrfToken").asText());
        if (body != null) request.header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String receiveMail(ServerSocket server) {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout(10000);
            var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            var out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1), true);
            StringBuilder message = new StringBuilder(); boolean data = false;
            out.print("220 test SMTP\r\n"); out.flush();
            for (String line; (line = in.readLine()) != null;) {
                if (data && !line.equals(".")) { message.append(line).append("\r\n"); continue; }
                if (data) { data = false; out.print("250 accepted\r\n"); }
                else if (line.startsWith("DATA")) { data = true; out.print("354 send message\r\n"); }
                else if (line.startsWith("QUIT")) { out.print("221 bye\r\n"); out.flush(); break; }
                else { out.print("250 OK\r\n"); }
                out.flush();
            }
            return message.toString();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
