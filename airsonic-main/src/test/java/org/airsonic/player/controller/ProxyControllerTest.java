package org.airsonic.player.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.airsonic.player.controller.rest.proxy.ProxyRestController;
import org.airsonic.player.util.HomeRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ProxyControllerTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private ApplicationContext context;
    private final Logger logger = (Logger) LoggerFactory.getLogger(ProxyRestController.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final HttpClient client = HttpClient.newHttpClient();
    private Level previousLevel;
    private HttpServer server;

    @Before
    public void setUp() throws Exception {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        events.start();
        logger.addAppender(events);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/resource", exchange -> {
            byte[] body = "Данные внешнего ресурса".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/upstream-error", exchange -> {
            byte[] body = "upstream-private-error-body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        logger.detachAppender(events);
        events.stop();
        logger.setLevel(previousLevel);
    }

    @Test
    public void servesBothLegacyPathsAndLogsUsageWithoutSecrets() throws Exception {
        assertFalse(context.containsBean("proxyController"));
        String target = target("/resource?token=private-token");
        for (String path : ProxyRestController.PATHS) {
            HttpResponse<byte[]> response = request(path, target, true);
            assertEquals(200, response.statusCode());
            assertArrayEquals("Данные внешнего ресурса".getBytes(StandardCharsets.UTF_8), response.body());
            assertFalse(response.headers().firstValue("Content-Type").orElse("").contains("text/plain"));
            HttpResponse<byte[]> head = request("HEAD", path, target, true);
            assertEquals(200, head.statusCode());
            assertEquals(0, head.body().length);
        }
        assertUsageEvents(4);
    }

    @Test
    public void preservesUpstreamErrorStatusAndLogsUsage() throws Exception {
        HttpResponse<byte[]> response = request("/proxy", target("/missing"), true);
        assertEquals(404, response.statusCode());
        assertFalse(new String(response.body(), StandardCharsets.UTF_8).contains("upstream"));
        assertUsageEvents(1);
    }

    @Test
    public void preservesUpstreamServerErrorWithoutLeakingItsBody() throws Exception {
        HttpResponse<byte[]> response = request("/proxy", target("/upstream-error"), true);
        assertEquals(500, response.statusCode());
        assertFalse(new String(response.body(), StandardCharsets.UTF_8).contains("upstream-private-error-body"));
        assertUsageEvents(1);
    }

    @Test
    public void reportsConnectionFailureAndLogsUsage() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            closedPort = socket.getLocalPort();
        }
        HttpResponse<byte[]> response = request("/proxy", "http://127.0.0.1:" + closedPort + "/unreachable", true);
        assertTrue("Connection failure should surface as a server error", response.statusCode() >= 500);
        assertUsageEvents(1);
    }

    @Test
    public void rejectsMissingUrlAndStillLogsInvocation() throws Exception {
        HttpResponse<byte[]> response = request("/proxy", null, true);
        assertEquals(400, response.statusCode());
        assertUsageEvents(1);
    }

    @Test
    public void requiresTheExistingUserRole() throws Exception {
        HttpResponse<byte[]> response = request("/proxy", target("/resource"), false);
        assertEquals(401, response.statusCode());
        assertTrue(events.list.isEmpty());
    }

    private String target(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private HttpResponse<byte[]> request(String path, String target, boolean authenticated) throws Exception {
        return request("GET", path, target, authenticated);
    }

    private HttpResponse<byte[]> request(String method, String path, String target, boolean authenticated) throws Exception {
        String query = target == null ? "" : "?url=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + "/airsonic" + path + query));
        if (authenticated) {
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                    "admin:admin".getBytes(StandardCharsets.UTF_8)));
        }
        builder.method(method, HttpRequest.BodyPublishers.noBody());
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private void assertUsageEvents(int count) {
        assertEquals(count, events.list.size());
        for (ILoggingEvent event : events.list) {
            assertEquals(Level.INFO, event.getLevel());
            assertTrue(event.getFormattedMessage().contains("Legacy /proxy endpoint invoked"));
            assertFalse(event.getFormattedMessage().contains("private-token"));
            assertFalse(event.getFormattedMessage().contains("127.0.0.1"));
        }
    }
}
