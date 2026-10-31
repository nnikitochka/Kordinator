package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
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
import java.net.URI;
import java.net.http.*;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class UsersIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void userManagement() throws Exception {
        User admin = new User("users-admin", null); admin.setRoles(Set.of(User.Role.ADMIN, User.Role.STREAM));
        security.createUser(admin, "secret", "test");
        User member = new User("users-member", null); member.setRoles(Set.of(User.Role.SETTINGS, User.Role.STREAM));
        security.createUser(member, "secret", "test");
        for (String suffix : new String[]{"", ".view"}) {
            error(call("getUser" + suffix, "users-member", "username=users-admin"), 50);
            error(call("getUsers" + suffix, "users-member", ""), 50);
            error(call("createUser" + suffix, "users-member", ""), 50);
            error(call("updateUser" + suffix, "users-member", ""), 50);
            error(call("deleteUser" + suffix, "users-member", ""), 50);
            error(call("changePassword" + suffix, "users-member", "username=users-admin&password=x"), 50);
            error(call("getUser" + suffix, "users-admin", ""), 10);
            error(call("getUser" + suffix, "users-admin", "username=missing-user"), 70);
            error(call("deleteUser" + suffix, "users-admin", "username=admin"), 50);
            assertEquals("users-member", call("getUser" + suffix, "users-member", "username=users-member").path("user").path("username").asText());
            assertTrue(call("getUsers" + suffix, "users-admin", "").path("users").path("user").isArray());
        }
        ok(call("createUser", "users-admin", "username=users-created&password=enc:70617373&email=test%40example.com&downloadRole=true"));
        assertTrue(security.getUserByName("users-created").isDownloadRole());
        ok(call("updateUser.view", "users-admin", "username=users-created&email=new%40example.com&shareRole=true"));
        assertEquals("new@example.com", security.getUserByName("users-created").getEmail());
        assertTrue(security.getUserByName("users-created").isShareRole());
        ok(call("changePassword", "users-admin", "username=users-created&password=enc:6e6577"));
        ok(call("deleteUser.view", "users-admin", "username=users-created"));
        assertNull(security.getUserByName("users-created"));
    }
    private JsonNode call(String path, String user, String query) throws Exception {
        String url = "http://localhost:" + port + "/airsonic/rest/" + path + "?v=" + TestCaseUtils.restApiVersion() + "&c=users-test&u=" + user + "&p=secret&f=json&" + query;
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
    private void ok(JsonNode body) { assertEquals(body.toString(), "ok", body.path("status").asText()); }
    private void error(JsonNode body, int code) { assertEquals(body.toString(), code, body.path("error").path("code").asInt()); }
}
