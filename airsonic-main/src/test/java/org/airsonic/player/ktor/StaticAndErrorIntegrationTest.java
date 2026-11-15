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
    public void staticRoutesAreRegisteredDirectlyOnKtorAndBootErrorControllerIsAbsent() {
        jakarta.servlet.ServletRegistration registration = servletContext.getServletRegistration("ktor");
        assertNotNull("Ktor servlet registration", registration);
        for (String mapping : new String[]{"/script/*", "/style/*", "/icons/*", "/favicon.ico",
                "/browserconfig.xml", "/index.html", "/error"}) {
            assertTrue("Ktor mapping " + mapping, registration.getMappings().contains(mapping));
        }
        assertTrue("Boot BasicErrorController must not compete with Ktor /error",
                applicationContext.getBeansOfType(BasicErrorController.class).isEmpty());
    }

    @Test
    public void staticResourcesPreserveBytesMimeAndConditionalRangeSemantics() throws Exception {
        assertStaticResource("/script/utils.js", "javascript");
        assertStaticResource("/style/sonic_blue.css", "text/css");
        assertStaticResource("/icons/default_light/error.svg", "image/svg+xml");
        assertStaticResource("/index.html", "text/html");

        HttpResponse<byte[]> root = bytes("/", "GET", null, null, null, true);
        assertEquals(200, root.statusCode());
        assertTrue(new String(root.body(), StandardCharsets.UTF_8),
                new String(root.body(), StandardCharsets.UTF_8).contains("index.view"));
    }

    @Test
    public void staticPrefixResourcesRemainAvailableAnonymously() throws Exception {
        for (String path : new String[]{"/script/utils.js", "/style/sonic_blue.css", "/icons/default_light/error.svg"}) {
            HttpResponse<byte[]> response = bytes(path, "GET", null, null, null, false);
            assertEquals(path, 200, response.statusCode());
            assertTrue(path + " content type: " + response.headers().firstValue("content-type").orElse(""),
                    response.headers().firstValue("content-type").orElse("").contains("/"));
            assertTrue(path, response.body().length > 0);
        }
    }

    @Test
    public void staticMissesAndUnsafePathsNeverRedirectOrExposeWebInf() throws Exception {
        for (String path : new String[]{"/script/does-not-exist.js", "/script/", "/script/DataTables/"}) {
            HttpResponse<byte[]> response = bytes(path, "GET", null, null, null, true);
            assertEquals(path, 404, response.statusCode());
            assertFalse(path, response.headers().firstValue("location").isPresent());
        }
        for (String path : new String[]{
                "/script", "/style", "/icons",
                "/script/%2e%2e/WEB-INF/web.xml", "/script/%2E%2E%2fWEB-INF/web.xml",
                "/script/%2f%2e%2e%2fWEB-INF/web.xml", "/script/%5c..%5cWEB-INF%5cweb.xml",
                "/script/../WEB-INF/web.xml", "/script/%C3%28.js"}) {
            HttpResponse<byte[]> response = bytes(path, "GET", null, null, null, true);
            assertTrue(path + " returned " + response.statusCode(),
                    response.statusCode() == 400 || response.statusCode() == 404);
            assertFalse("unsafe path redirected: " + path, response.headers().firstValue("location").isPresent());
            assertFalse("unsafe resource contents leaked for " + path,
                    new String(response.body(), StandardCharsets.UTF_8).contains("<web-app"));
        }
    }

    @Test
    public void errorDispatchPreservesStatusAndReturnsNegotiatedEscapedRepresentations() throws Exception {
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

        HttpResponse<String> html = request("/test-error/throw?secret=query-token",
                "GET", "text/html", null, null, true);
        assertEquals(500, html.statusCode());
        assertTrue(html.body(), html.body().contains("Error 500"));
        assertTrue(html.body(), html.body().contains("&lt;controlled &amp; safe&gt;"));
        assertFalse(html.body(), html.body().contains("<controlled & safe>"));
        assertTrue(html.body(), html.body().contains("/test-error/throw"));
        assertFalse(html.body(), html.body().contains("query-token"));

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
    public void errorAcceptQualityChoosesTheBestSupportedRepresentation() throws Exception {
        HttpResponse<String> json = request("/test-error/404", "GET", "text/html;q=0, application/json",
                null, null, true);
        assertEquals(404, json.statusCode());
        assertTrue(json.headers().firstValue("content-type").orElse(""),
                json.headers().firstValue("content-type").orElse("").startsWith("application/json"));
        assertEquals(404, new ObjectMapper().readTree(json.body()).path("status").asInt());

        HttpResponse<String> html = request("/test-error/404", "GET", "text/html, application/json;q=0.5",
                null, null, true);
        assertEquals(404, html.statusCode());
        assertTrue(html.headers().firstValue("content-type").orElse(""),
                html.headers().firstValue("content-type").orElse("").startsWith("text/html"));
        assertTrue(html.body(), html.body().contains("<html>"));
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

    @Test
    public void errorDispatchAfterPostRetainsTheOriginalMethodAndExceptionContext() throws Exception {
        HttpClient browser = HttpClient.newBuilder()
                .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> loginPage = send(browser, "/login", "GET", null, null, null);
        Matcher tokenMatcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                .matcher(loginPage.body());
        assertTrue(loginPage.body(), tokenMatcher.find());
        String loginToken = tokenMatcher.group(1);
        HttpResponse<String> login = send(browser, "/login", "POST", "application/x-www-form-urlencoded",
                "j_username=admin&j_password=admin&_csrf=" + URLEncoder.encode(loginToken, StandardCharsets.UTF_8), null);
        assertTrue("login result " + login.statusCode(), login.statusCode() == 302 || login.statusCode() == 303);

        HttpResponse<String> main = send(browser, "/top.view", "GET", null, null, null);
        Matcher pageToken = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(main.body());
        assertTrue(main.body(), pageToken.find());
        HttpResponse<String> post = send(browser, "/test-error/throw", "POST", null, "", pageToken.group(1));
        assertEquals(500, post.statusCode());
        JsonNode json = new ObjectMapper().readTree(post.body());
        assertEquals(500, json.path("status").asInt());
        assertTrue(json.path("path").asText(), json.path("path").asText().contains("/test-error/throw"));
    }

    private void assertStaticResource(String resourcePath, String expectedMime) throws Exception {
        byte[] expected;
        try (InputStream stream = servletContext.getResourceAsStream(resourcePath)) {
            assertNotNull("ServletContext resource " + resourcePath, stream);
            expected = stream.readAllBytes();
        }
        HttpResponse<byte[]> get = bytes(resourcePath, "GET", null, null, null, true);
        assertEquals(resourcePath, 200, get.statusCode());
        assertArrayEquals(resourcePath, expected, get.body());
        String mime = get.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
        if ("javascript".equals(expectedMime)) {
            assertTrue(resourcePath + " mime=" + mime,
                    mime.startsWith("application/javascript") || mime.startsWith("text/javascript"));
        } else {
            assertTrue(resourcePath + " mime=" + mime, mime.startsWith(expectedMime));
        }

        HttpResponse<byte[]> head = bytes(resourcePath, "HEAD", null, null, null, true);
        assertEquals(200, head.statusCode());
        assertTrue("HEAD body for " + resourcePath, head.body().length == 0);
        assertEquals("HEAD Content-Length for " + resourcePath,
                get.headers().firstValue("content-length").orElseThrow(),
                head.headers().firstValue("content-length").orElseThrow());
        assertEquals("HEAD Content-Type for " + resourcePath,
                get.headers().firstValue("content-type").orElseThrow(),
                head.headers().firstValue("content-type").orElseThrow());
        assertEquals(Integer.toString(expected.length), get.headers().firstValue("content-length").orElseThrow());

        HttpResponse<byte[]> rangedHead = bytes(resourcePath, "HEAD", "bytes=0-3", null, null, true);
        assertEquals(200, rangedHead.statusCode());
        assertTrue("HEAD with Range body for " + resourcePath, rangedHead.body().length == 0);
        assertEquals("HEAD must ignore Range for " + resourcePath,
                get.headers().firstValue("content-length").orElseThrow(),
                rangedHead.headers().firstValue("content-length").orElseThrow());

        String lastModified = get.headers().firstValue("last-modified").orElseThrow();
        HttpResponse<byte[]> notModified = bytes(resourcePath, "GET", null, null, lastModified, true);
        assertEquals(304, notModified.statusCode());
        assertEquals(0, notModified.body().length);
        if (resourcePath.startsWith("/script/") || resourcePath.startsWith("/style/")
                || resourcePath.startsWith("/icons/")) {
            assertTrue("static cache header: " + get.headers().firstValue("cache-control").orElse(""),
                    get.headers().firstValue("cache-control").orElse("").contains("max-age=36000"));
            assertTrue("304 static cache header: " + notModified.headers().firstValue("cache-control").orElse(""),
                    notModified.headers().firstValue("cache-control").orElse("").contains("max-age=36000"));
        }

        int end = Math.min(expected.length - 1, 16);
        HttpResponse<byte[]> range = bytes(resourcePath, "GET", "bytes=2-" + end, null, null, true);
        assertEquals(206, range.statusCode());
        assertArrayEquals(Arrays.copyOfRange(expected, 2, end + 1), range.body());
        assertEquals("bytes 2-" + end + "/" + expected.length,
                range.headers().firstValue("content-range").orElseThrow());

        HttpResponse<byte[]> unsatisfiable = bytes(resourcePath, "GET", "bytes=" + expected.length + "-", null, null, true);
        assertEquals(416, unsatisfiable.statusCode());
        assertEquals("bytes */" + expected.length,
                unsatisfiable.headers().firstValue("content-range").orElseThrow());

        HttpResponse<byte[]> oldIfRange = bytes(resourcePath, "GET", "bytes=0-3", "Thu, 01 Jan 1970 00:00:00 GMT", null, true);
        assertEquals(200, oldIfRange.statusCode());
        assertArrayEquals(expected, oldIfRange.body());

        HttpResponse<byte[]> suffix = bytes(resourcePath, "GET", "bytes=-4", null, null, true);
        assertEquals(206, suffix.statusCode());
        assertArrayEquals(Arrays.copyOfRange(expected, expected.length - 4, expected.length), suffix.body());
        assertEquals("bytes " + (expected.length - 4) + "-" + (expected.length - 1) + "/" + expected.length,
                suffix.headers().firstValue("content-range").orElseThrow());

        HttpResponse<byte[]> openEnded = bytes(resourcePath, "GET", "bytes=4-", null, null, true);
        assertEquals(206, openEnded.statusCode());
        assertArrayEquals(Arrays.copyOfRange(expected, 4, expected.length), openEnded.body());

        for (String invalidRange : new String[]{"abc-5", "-", "invalidend"}) {
            HttpResponse<byte[]> invalid = bytes(resourcePath, "GET", "bytes=" + invalidRange, null, null, true);
            assertEquals(invalidRange, 416, invalid.statusCode());
            assertEquals("bytes */" + expected.length, invalid.headers().firstValue("content-range").orElseThrow());
        }

        HttpResponse<byte[]> multiRange = bytes(resourcePath, "GET", "bytes=0-1,4-5", null, null, true);
        assertEquals(200, multiRange.statusCode());
        assertArrayEquals(expected, multiRange.body());
    }

    private HttpResponse<byte[]> bytes(String path, String method, String range, String ifRange,
                                      String ifModifiedSince, boolean authenticated) throws Exception {
        HttpRequest.Builder builder = requestBuilder(path, method, null, authenticated);
        if (range != null) builder.header("Range", range);
        if (ifRange != null) builder.header("If-Range", ifRange);
        if (ifModifiedSince != null) builder.header("If-Modified-Since", ifModifiedSince);
        return client.send(builder.method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
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
