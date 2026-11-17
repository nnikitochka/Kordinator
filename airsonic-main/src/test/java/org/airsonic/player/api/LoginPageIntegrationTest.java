package org.airsonic.player.api;

import org.airsonic.player.domain.User;
import org.airsonic.player.security.JndiLdapDirectoryAuthenticator;
import org.airsonic.player.security.LdapAuthenticator;
import org.airsonic.player.security.SpringLdapAuthenticationProvider;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class LoginPageIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    @LocalServerPort private int port;
    @Autowired private SettingsService settings;
    @Autowired private ApplicationContext context;
    @Autowired private SecurityService security;
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private FilterChainProxy filterChainProxy;

    @Test
    public void rendersLoginJspWithModelAndCsrfWithoutEnablingCors() throws Exception {
        assertFalse(context.containsBean("loginController"));
        String previousMessage = settings.getLoginMessage();
        settings.setLoginMessage("Integration login notice");
        try {
            HttpResponse<String> page = send(browser(), "/login", "GET", null, "https://example.com");
            assertEquals(200, page.statusCode());
            assertTrue(page.body(), page.body().contains("id=\"loginframe\""));
            assertTrue(page.body(), page.body().contains("Integration login notice"));
            assertTrue(page.body(), CSRF.matcher(page.body()).find());
            assertTrue(page.body(), page.body().contains("name=\"j_username\""));
            assertTrue(page.body(), page.body().contains("name=\"j_password\""));
            assertFalse(page.headers().firstValue("Access-Control-Allow-Origin").isPresent());

            HttpResponse<String> errorPage = send(browser(), "/login?error", "GET", null);
            assertEquals(200, errorPage.statusCode());
            assertTrue(errorPage.body(), errorPage.body().contains("class=\"warning\""));
            HttpResponse<String> logoutPage = send(browser(), "/login?logout", "GET", null);
            assertEquals(200, logoutPage.statusCode());
            assertTrue(logoutPage.body(), logoutPage.body().contains("class=\"loginmessagebottom\""));
        } finally {
            settings.setLoginMessage(previousMessage);
        }
    }

    @Test
    public void keepsLegacyCredentialRedirectAndSpringSecurityLoginLogoutFlow() throws Exception {
        HttpClient browser = browser();
        String legacyUrl = "/login?user=" + encode("legacy user&name") + "&password=" + encode("p@ss word");
        HttpResponse<String> legacy = send(browser, legacyUrl, "GET", null);
        assertEquals(302, legacy.statusCode());
        URI location = URI.create(legacy.headers().firstValue("Location").orElseThrow());
        assertEquals(location.toString(), "/login", location.getPath());
        Map<String, String> redirected = query(location.getRawQuery());
        assertEquals("legacy user&name", redirected.get("username"));
        assertEquals("p@ss word", redirected.get("password"));

        HttpResponse<String> loginPage = send(browser, "/login", "GET", null);
        String csrf = csrf(loginPage.body());
        String sessionBeforeLogin = sessionId(browser);
        HttpResponse<String> missingCsrf = post(browser(), "/login", "j_username=admin&j_password=admin");
        assertEquals(403, missingCsrf.statusCode());

        HttpResponse<String> success = post(browser, "/login", credentials("admin", "admin", csrf) + "&remember-me=on");
        assertEquals(302, success.statusCode());
        assertNotEquals("Successful login must rotate the CSRF session id", sessionBeforeLogin, sessionId(browser));
        assertTrue(success.headers().firstValue("Location").orElseThrow().endsWith("/airsonic/index"));
        assertTrue(success.headers().allValues("Set-Cookie").toString().contains("remember-me"));
        for (String method : List.of("GET", "HEAD", "TRACE", "OPTIONS")) {
            HttpResponse<String> response = send(browser, "/rest/ping", method, null);
            assertNotEquals(method + " must be exempt from CSRF checks", 403, response.statusCode());
        }
        assertNotEquals(403, post(browser, "/rest/getScanStatus.view", "f=json").statusCode());
        assertNotEquals(403, post(browser, "/search", "query=airsonic").statusCode());
        assertEquals(403, post(browser, "/rest/getScanStatus", "f=json").statusCode());
        assertEquals(200, send(browser, "/index", "GET", null).statusCode());
        HttpResponse<String> authenticatedLoginPage = send(browser, "/login.view", "GET", null);
        assertEquals(200, authenticatedLoginPage.statusCode());

        HttpResponse<String> logout = post(browser, "/logout", "_csrf=" + encode(csrf(authenticatedLoginPage.body())));
        HttpResponse<String> afterLogout = send(browser, "/index", "GET", null);
        assertEquals("Logout clears the session and remember-me cookies", 204, logout.statusCode());
        assertTrue(logout.headers().allValues("Set-Cookie").toString().contains("JSESSIONID"));
        assertTrue(logout.headers().allValues("Set-Cookie").toString().contains("remember-me"));
        assertFalse(logout.headers().firstValue("Location").isPresent());
        assertEquals("Logout must invalidate the authenticated session", 401, afterLogout.statusCode());

        HttpClient anonymous = browser();
        HttpResponse<String> protectedSuffix = send(anonymous, "/login.view", "GET", null);
        assertEquals(401, protectedSuffix.statusCode());
    }

    @Test
    public void wrongCredentialsStillReturnToTheKtorLoginPageWithErrorState() throws Exception {
        HttpClient browser = browser();
        HttpResponse<String> page = send(browser, "/login", "GET", null);
        HttpResponse<String> failure = post(browser, "/login", credentials("admin", "wrong-password", csrf(page.body())));
        assertEquals(302, failure.statusCode());
        assertTrue(failure.headers().firstValue("Location").orElseThrow().endsWith("/airsonic/login?error"));
        HttpResponse<String> errorPage = send(browser, "/login?error", "GET", null);
        assertEquals(200, errorPage.statusCode());
        assertTrue(errorPage.body(), errorPage.body().contains("class=\"warning\""));
    }

    @Test
    public void unavailableLocalLdapEndpointReturnsOrdinaryLoginFailure() throws Exception {
        String username = "ldap-outage-it-user";
        User ldapUser = new User(username, null, true, 0L, 0L, 0L, java.util.Set.of(User.Role.STREAM));
        boolean userCreated = false;
        boolean oldEnabled = settings.isLdapEnabled();
        String oldUrl = settings.getLdapUrl();
        String oldFilter = settings.getLdapSearchFilter();
        String oldManagerDn = settings.getLdapManagerDn();
        String oldManagerPassword = settings.getLdapManagerPassword();
        boolean oldShadowing = settings.isLdapAutoShadowing();
        UsernamePasswordAuthenticationFilter loginFilter = filterChainProxy.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .filter(UsernamePasswordAuthenticationFilter.class::isInstance)
                .map(UsernamePasswordAuthenticationFilter.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("Form login filter not found"));
        try {
            security.createUser(ldapUser, "ignored-local-password", "LDAP outage integration fixture");
            userCreated = true;
            int closedPort;
            try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
                closedPort = socket.getLocalPort();
            }
            settings.setLdapEnabled(true);
            settings.setLdapUrl("ldap://127.0.0.1:" + closedPort + "/dc=example,dc=test");
            settings.setLdapSearchFilter("(uid={0})");
            settings.setLdapManagerDn(null);
            settings.setLdapManagerPassword(null);
            settings.setLdapAutoShadowing(false);

            loginFilter.setAuthenticationManager(new ProviderManager(List.of(
                    new SpringLdapAuthenticationProvider(
                            new LdapAuthenticator(security, settings, new JndiLdapDirectoryAuthenticator(settings)))),
                    authenticationManager));

            HttpClient browser = browser();
            HttpResponse<String> page = send(browser, "/login", "GET", null);
            HttpResponse<String> failure = post(browser, "/login",
                    credentials(username, "ldap-password", csrf(page.body())));
            assertEquals(302, failure.statusCode());
            assertTrue(failure.headers().firstValue("Location").orElseThrow()
                    .endsWith("/airsonic/login?error"));
            HttpResponse<String> errorPage = send(browser, "/login?error", "GET", null);
            assertEquals(200, errorPage.statusCode());
            assertTrue(errorPage.body(), errorPage.body().contains("class=\"warning\""));
        } finally {
            loginFilter.setAuthenticationManager(authenticationManager);
            settings.setLdapEnabled(oldEnabled);
            settings.setLdapUrl(oldUrl);
            settings.setLdapSearchFilter(oldFilter);
            settings.setLdapManagerDn(oldManagerDn);
            settings.setLdapManagerPassword(oldManagerPassword);
            settings.setLdapAutoShadowing(oldShadowing);
            if (userCreated) security.deleteUser(username);
        }
    }

    private HttpClient browser() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private HttpResponse<String> send(HttpClient client, String path, String method, String form) throws Exception {
        return send(client, path, method, form, null);
    }

    private HttpResponse<String> send(HttpClient client, String path, String method, String form, String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/airsonic" + path))
                .timeout(Duration.ofSeconds(10));
        if (origin != null) request.header("Origin", origin);
        if (form != null) request.header("Content-Type", "application/x-www-form-urlencoded");
        request.method(method, form == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(form));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path, String form) throws Exception {
        return send(client, path, "POST", form);
    }

    private String csrf(String html) {
        Matcher matcher = CSRF.matcher(html);
        assertTrue(html, matcher.find());
        return matcher.group(1);
    }

    private String sessionId(HttpClient browser) {
        CookieManager cookies = (CookieManager) browser.cookieHandler().orElseThrow();
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> "JSESSIONID".equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected JSESSIONID cookie"));
    }

    private String credentials(String username, String password, String csrf) {
        return "j_username=" + encode(username) + "&j_password=" + encode(password) + "&_csrf=" + encode(csrf);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, String> query(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            values.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
        }
        return values;
    }
}
