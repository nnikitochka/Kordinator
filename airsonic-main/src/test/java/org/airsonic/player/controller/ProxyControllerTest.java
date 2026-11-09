package org.airsonic.player.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class ProxyControllerTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(ProxyController.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
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
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
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
    public void copiesResponseAndRecordsEveryInvocationWithoutTargetSecrets() throws Exception {
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertNull(new ProxyController().handleRequest(request("/resource?token=private-token"), response));
            assertEquals(200, response.getStatus());
            assertArrayEquals("Данные внешнего ресурса".getBytes(StandardCharsets.UTF_8), response.getContentAsByteArray());
        }
        assertUsageEvents(2);
    }

    @Test
    public void preservesUpstreamErrorAndRecordsUsage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ProxyController().handleRequest(request("/missing"), response);
        assertEquals(404, response.getStatus());
        assertTrue(response.isCommitted());
        assertUsageEvents(1);
    }

    @Test
    public void recordsInvocationEvenWhenRequiredUrlIsMissing() {
        assertThrows(MissingServletRequestParameterException.class, () ->
                new ProxyController().handleRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertUsageEvents(1);
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/proxy");
        request.setParameter("url", "http://127.0.0.1:" + server.getAddress().getPort() + path);
        return request;
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
