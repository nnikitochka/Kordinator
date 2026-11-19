package org.airsonic.player.api;

import jakarta.servlet.ServletContext;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.Avatar;
import org.airsonic.player.domain.AvatarScheme;
import org.airsonic.player.domain.UserSettings;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AvatarRestIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SettingsService settings;
    @Autowired private ServletContext servletContext;
    @Autowired private ApplicationContext context;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test public void servesAvatarsViaKtorWithoutSpringMvc() throws Exception {
        assertFalse(context.containsBean("avatarController"));
        Avatar system = settings.getAllSystemAvatars().get(0);
        byte[] systemBytes;
        try (var input = servletContext.getResourceAsStream("/" + system.getPath())) {
            assertNotNull("System avatar must be available in the web application", input);
            systemBytes = input.readAllBytes();
        }
        Path file = Files.createTempFile("avatar-", ".jpg");
        byte[] custom = "test-avatar".getBytes(StandardCharsets.UTF_8);
        Files.write(file, custom);
        Avatar previous = settings.getCustomAvatar("admin");
        UserSettings user = settings.getUserSettings("admin");
        AvatarScheme previousScheme = user.getAvatarScheme();
        Integer previousId = user.getSystemAvatarId();
        try {
            settings.setCustomAvatar(new Avatar(0, "test", Instant.now(), "image/jpeg", 1, 1, file), "admin");
            user.setAvatarScheme(AvatarScheme.SYSTEM);
            user.setSystemAvatarId(system.getId());
            settings.updateUserSettings(user);
            String auth = "v=" + TestCaseUtils.restApiVersion() + "&c=test&u=admin&p=admin";
            for (String suffix : new String[]{"", ".view"}) {
                String endpoint = "/rest/getAvatar" + suffix + "?" + auth;
                HttpResponse<byte[]> byId = request(endpoint + "&id=" + system.getId(), "GET", null);
                assertEquals(200, byId.statusCode());
                assertEquals(system.getMimeType(), byId.headers().firstValue("content-type").orElseThrow());
                assertArrayEquals(systemBytes, byId.body());
                assertArrayEquals(systemBytes, request(endpoint + "&username=admin", "GET", null).body());
                assertArrayEquals(custom, request(endpoint + "&username=admin&forceCustom=on", "GET", null).body());
                assertEquals(404, request(endpoint, "GET", null).statusCode());
                assertEquals(404, request(endpoint + "&id=2147483647", "GET", null).statusCode());
                HttpResponse<byte[]> head = request(endpoint + "&username=admin&forceCustom=true", "HEAD", null);
                assertEquals(200, head.statusCode());
                assertEquals("image/jpeg", head.headers().firstValue("content-type").orElseThrow());
                assertEquals(0, head.body().length);
            }
            assertArrayEquals(custom, request("/rest/getAvatar.view", "POST", auth + "&username=admin&forceCustom=true").body());
            assertEquals(403, request("/rest/getAvatar", "POST", auth + "&username=admin").statusCode());
            user.setAvatarScheme(AvatarScheme.NONE);
            settings.updateUserSettings(user);
            assertEquals(404, request("/rest/getAvatar?" + auth + "&username=admin", "GET", null).statusCode());
            assertArrayEquals(custom, request("/rest/getAvatar?" + auth + "&username=admin&forceCustom=yes", "GET", null).body());
            user.setAvatarScheme(AvatarScheme.CUSTOM);
            settings.updateUserSettings(user);
            assertArrayEquals(custom, request("/rest/getAvatar?" + auth + "&username=admin", "GET", null).body());
            settings.setCustomAvatar(new Avatar(0, "web", Instant.now(), system.getMimeType(), 1, 1,
                    "/" + system.getPath()), "admin");
            assertArrayEquals(systemBytes, request("/rest/getAvatar?" + auth + "&username=admin", "GET", null).body());
            settings.setCustomAvatar(new Avatar(0, "uri", Instant.now(), "image/jpeg", 1, 1,
                    file.toUri().toString()), "admin");
            assertArrayEquals(custom, request("/rest/getAvatar?" + auth + "&username=admin", "GET", null).body());
            settings.setCustomAvatar(null, "admin");
            assertEquals(404, request("/rest/getAvatar?" + auth + "&username=admin", "GET", null).statusCode());
        } finally {
            settings.setCustomAvatar(previous, "admin");
            user.setAvatarScheme(previousScheme);
            user.setSystemAvatarId(previousId);
            settings.updateUserSettings(user);
            Files.deleteIfExists(file);
        }
    }
    private HttpResponse<byte[]> request(String path, String method, String form) throws Exception {
        return request(path, method, form, false);
    }
    private HttpResponse<byte[]> request(String path, String method, String form, boolean basic) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path));
        if (form != null) builder.header("Content-Type", "application/x-www-form-urlencoded");
        if (basic) builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8)));
        builder.method(method, form == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(form));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
