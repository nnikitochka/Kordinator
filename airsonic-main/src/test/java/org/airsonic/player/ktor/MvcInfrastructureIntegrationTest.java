package org.airsonic.player.ktor;

import org.airsonic.player.util.HomeRule;
import org.airsonic.player.service.SettingsService;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.MessageSource;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import jakarta.servlet.ServletContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class MvcInfrastructureIntegrationTest {

    @ClassRule
    public static final HomeRule homeRule = new HomeRule();

    @LocalServerPort
    private int port;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ServletContext servletContext;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Test
    public void accessDeniedJspUsesTheConfiguredEnglishAndRussianMessages() throws Exception {
        Locale original = settingsService.getLocale();
        try {
            assertLocalePage(Locale.ENGLISH, "Access denied");
            assertLocalePage(new Locale("ru"), "Доступ запрещён");
        } finally {
            settingsService.setLocale(original);
        }
    }

    private void assertLocalePage(Locale locale, String expected) throws Exception {
        settingsService.setLocale(locale);
        assertEquals(expected, messageSource.getMessage("accessDenied.title", null, locale));

        HttpResponse<String> response = get("/accessDenied");
        assertEquals(response.body(), 200, response.statusCode());
        assertTrue(response.body(), response.body().contains(expected));
        assertTrue(response.body(), !response.body().contains("???accessDenied.title???"));
    }

    @Test
    public void directAndLegacyPagePathsStaticResourcesAndHttpErrorsKeepTheirContracts() throws Exception {
        jakarta.servlet.ServletRegistration registration = servletContext.getServletRegistration("ktor");
        assertTrue("Ktor servlet registration is present", registration != null);
        assertTrue(registration.getMappings().contains("/accessDenied"));
        assertTrue(registration.getMappings().contains("/accessDenied.view"));

        for (String path : new String[]{"/accessDenied", "/accessDenied.view"}) {
            HttpResponse<String> page = get(path);
            assertEquals(page.body(), 200, page.statusCode());
            assertTrue(page.body(), page.body().contains("class=\"mainframe bgcolor1\""));
        }

        HttpResponse<byte[]> asset = client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/airsonic/icons/default_light/error.svg"))
                        .timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, asset.statusCode());
        assertTrue(asset.headers().firstValue("content-type").orElse("").startsWith("image/svg+xml"));
        assertTrue(asset.body().length > 0);

        assertStaticAsset("/airsonic/script/utils.js", "javascript");
        assertStaticAsset("/airsonic/style/sonic_blue.css", "text/css");

        HttpResponse<String> missing = get("/no-such-mvc-page", true);
        assertEquals(missing.body(), 404, missing.statusCode());
        assertTrue(missing.body(), !missing.headers().firstValue("location").isPresent());
        assertEquals(404, get("/no-such-mvc-page.view", true).statusCode());
        HttpResponse<String> method = client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/airsonic/accessDenied"))
                        .timeout(Duration.ofSeconds(10)).method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, method.statusCode());
        assertTrue(method.headers().firstValue("allow").orElse("").contains("GET"));
    }

    private void assertStaticAsset(String path, String expectedType) throws Exception {
        HttpResponse<byte[]> asset = client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(path, 200, asset.statusCode());
        assertTrue(path + " content-type: " + asset.headers().firstValue("content-type").orElse(""),
                asset.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT).contains(expectedType));
        assertTrue(path, asset.body().length > 0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return get(path, false);
    }

    private HttpResponse<String> get(String path, boolean authenticated) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/airsonic" + path))
                .timeout(Duration.ofSeconds(10));
        if (authenticated) {
            builder.header("Authorization", "Basic YWRtaW46YWRtaW4=");
        }
        HttpRequest request = builder.GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
