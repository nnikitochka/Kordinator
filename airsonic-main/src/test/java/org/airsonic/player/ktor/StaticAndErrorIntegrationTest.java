package org.airsonic.player.ktor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.util.HomeRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** HTTP-level contracts for servlet static resources and error dispatch through Ktor. */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.context-path=/airsonic", "server.error.include-message=always",
                "server.error.include-stacktrace=always"})
@Import(StaticAndErrorIntegrationTest.ErrorServletConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class StaticAndErrorIntegrationTest {

    @ClassRule
    public static final HomeRule homeRule = new HomeRule();

    private static final String ADMIN = "Basic " + Base64.getEncoder()
            .encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8));

    @LocalServerPort
    private int port;

    @Autowired
    private ServletContext servletContext;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ServerProperties serverProperties;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Test
    public void errorDispatchPreservesStatusAndReturnsJsonErrors() throws Exception {
        for (int status : new int[]{403, 404, 405}) {
            HttpResponse<String> jsonResponse = request("/test-error/" + status + "?secret=query-token",
                    "GET", "application/json", null, null, true);
            assertEquals("status " + status, status, jsonResponse.statusCode());
            JsonNode json = new ObjectMapper().readTree(jsonResponse.body());
            assertEquals(status, json.path("status").asInt());
            assertTrue("error field: " + json, json.path("error").isTextual());
            assertTrue("path field: " + json, json.path("path").asText().contains("/test-error/" + status));
            assertFalse(jsonResponse.body(), jsonResponse.body().contains("query-token"));
            assertTrue(jsonResponse.headers().firstValue("content-type").orElse("")
                    .toLowerCase(Locale.ROOT).startsWith("application/json"));
        }

        HttpResponse<String> htmlAccept = request("/test-error/throw?secret=query-token",
                "GET", "text/html", null, null, true);
        assertEquals(500, htmlAccept.statusCode());
        assertTrue(htmlAccept.headers().firstValue("content-type").orElse("").startsWith("application/json"));
        JsonNode htmlAcceptJson = new ObjectMapper().readTree(htmlAccept.body());
        assertEquals(500, htmlAcceptJson.path("status").asInt());
        assertFalse(htmlAccept.body(), htmlAccept.body().contains("query-token"));

        HttpResponse<String> exceptionJson = request("/test-error/throw?secret=query-token",
                "GET", "application/json", null, null, true);
        assertEquals(500, exceptionJson.statusCode());
        JsonNode exceptionDetails = new ObjectMapper().readTree(exceptionJson.body());
        assertEquals(500, exceptionDetails.path("status").asInt());
        assertTrue("configured error trace: " + exceptionDetails,
                exceptionDetails.path("trace").asText().contains("ServletException"));
        assertTrue(exceptionDetails.path("trace").asText().contains("<controlled & safe>"));
        assertFalse(exceptionJson.body(), exceptionJson.body().contains("query-token"));

        HttpResponse<String> htmlHead = request("/test-error/404", "HEAD", "text/html", null, null, true);
        assertEquals(404, htmlHead.statusCode());
        assertTrue("HEAD error response must not have a body", htmlHead.body().isEmpty());

        HttpResponse<String> direct = request("/error", "GET", "application/json", null, null, true);
        assertEquals(500, direct.statusCode());
        assertEquals(500, new ObjectMapper().readTree(direct.body()).path("status").asInt());
    }

    @Test
    public void errorForwardFromWarStyleDispatcherPreservesExplicitErrorAttributes() throws Exception {
        HttpResponse<String> response = request("/test-error/forward", "GET", "application/json", null, null, true);
        assertEquals(409, response.statusCode());
        JsonNode json = new ObjectMapper().readTree(response.body());
        assertEquals(409, json.path("status").asInt());
        assertEquals("/airsonic/test-error/forward", json.path("path").asText());
    }

    @Test
    public void errorResponsesStayJsonForBrowserAcceptHeaders() throws Exception {
        HttpResponse<String> json = request("/test-error/404", "GET", "text/html, application/json;q=0.5",
                null, null, true);
        assertEquals(404, json.statusCode());
        assertTrue(json.headers().firstValue("content-type").orElse(""),
                json.headers().firstValue("content-type").orElse("").startsWith("application/json"));
        assertEquals(404, new ObjectMapper().readTree(json.body()).path("status").asInt());

        assertFalse(json.body(), json.body().contains("<html>"));
    }

    @Test
    public void errorMessageAndTracePoliciesHonorNeverAndOnParam() throws Exception {
        ErrorProperties error = serverProperties.getError();
        ErrorProperties.IncludeAttribute originalMessage = error.getIncludeMessage();
        ErrorProperties.IncludeAttribute originalTrace = error.getIncludeStacktrace();
        ErrorProperties.IncludeAttribute originalPath = error.getIncludePath();
        boolean originalException = error.isIncludeException();
        try {
            error.setIncludeMessage(ErrorProperties.IncludeAttribute.NEVER);
            error.setIncludeStacktrace(ErrorProperties.IncludeAttribute.NEVER);
            error.setIncludePath(ErrorProperties.IncludeAttribute.NEVER);
            error.setIncludeException(true);
            HttpResponse<String> never = request("/test-error/throw", "GET", "application/json", null, null, true);
            assertEquals(500, never.statusCode());
            JsonNode neverJson = new ObjectMapper().readTree(never.body());
            assertFalse(neverJson.toString(), neverJson.has("trace"));
            assertFalse(neverJson.toString(), neverJson.toString().contains("controlled & safe"));
            assertFalse(neverJson.toString(), neverJson.has("path"));
            assertTrue("exception included: " + neverJson,
                    neverJson.toString().contains("ServletException"));

            error.setIncludeMessage(ErrorProperties.IncludeAttribute.ON_PARAM);
            error.setIncludeStacktrace(ErrorProperties.IncludeAttribute.ON_PARAM);
            error.setIncludePath(ErrorProperties.IncludeAttribute.ALWAYS);
            HttpResponse<String> onParamDefault = request("/test-error/throw", "GET", "application/json", null, null, true);
            assertEquals(500, onParamDefault.statusCode());
            JsonNode defaultJson = new ObjectMapper().readTree(onParamDefault.body());
            assertFalse(defaultJson.toString(), defaultJson.has("trace"));
            assertFalse(defaultJson.toString(), defaultJson.toString().contains("controlled & safe"));
            assertEquals("/airsonic/test-error/throw", defaultJson.path("path").asText());

            HttpResponse<String> onParam = request("/test-error/throw?trace&message", "GET",
                    "application/json", null, null, true);
            assertEquals(500, onParam.statusCode());
            JsonNode details = new ObjectMapper().readTree(onParam.body());
            assertTrue("message included on request: " + details, details.toString().contains("controlled & safe"));
            assertTrue("trace included on request: " + details, details.path("trace").asText().contains("ServletException"));
            assertTrue("path included on request: " + details,
                    details.path("path").asText().contains("/test-error/throw"));
        } finally {
            error.setIncludeMessage(originalMessage);
            error.setIncludeStacktrace(originalTrace);
            error.setIncludePath(originalPath);
            error.setIncludeException(originalException);
        }
    }

    private HttpResponse<String> request(String path, String method, String accept, String contentType,
                                         String body, boolean authenticated) throws Exception {
        HttpRequest.Builder builder = requestBuilder(path, method, accept, authenticated);
        if (contentType != null) builder.header("Content-Type", contentType);
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder requestBuilder(String path, String method, String accept, boolean authenticated) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/airsonic" + path))
                .timeout(Duration.ofSeconds(10));
        if (accept != null) builder.header("Accept", accept);
        if (authenticated) builder.header("Authorization", ADMIN);
        return builder;
    }

    private HttpResponse<String> send(HttpClient httpClient, String path, String method, String contentType,
                                      String body, String csrf) throws Exception {
        HttpRequest.Builder builder = requestBuilder(path, method, null, false);
        if (contentType != null) builder.header("Content-Type", contentType);
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        HttpRequest request = builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class ErrorServletConfiguration {
        @Bean
        ServletRegistrationBean<ErrorTestServlet> testErrorServlet() {
            ServletRegistrationBean<ErrorTestServlet> registration =
                    new ServletRegistrationBean<>(new ErrorTestServlet(), "/test-error/*");
            registration.setName("testErrorServlet");
            return registration;
        }
    }

    /** Test-only servlet to exercise real container ERROR dispatch without adding a production debug route. */
    static class ErrorTestServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            dispatch(request, response);
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            dispatch(request, response);
        }

        private void dispatch(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            String path = request.getPathInfo();
            if ("/forward".equals(path)) {
                request.setAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 409);
                request.setAttribute(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, request.getRequestURI());
                request.setAttribute(jakarta.servlet.RequestDispatcher.ERROR_MESSAGE, "forward <controlled>");
                request.getRequestDispatcher("/error").forward(request, response);
                return;
            }
            if ("/403".equals(path)) response.sendError(403, "forbidden");
            else if ("/404".equals(path)) response.sendError(404, "missing");
            else if ("/405".equals(path)) response.sendError(405, "unsupported");
            else throw new ServletException("<controlled & safe>");
        }
    }
}
