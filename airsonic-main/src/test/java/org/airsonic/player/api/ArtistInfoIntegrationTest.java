package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.ArtistBio;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.util.HomeRule;
import org.airsonic.player.util.MusicFolderTestData;
import org.airsonic.player.util.StringUtil;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ArtistInfoIntegrationTest {
    @ClassRule
    public static final HomeRule homeRule = new HomeRule();

    @LocalServerPort
    private int port;
    @Autowired
    private MediaFolderService folders;
    @Autowired
    private MediaScannerService scanner;
    @Autowired
    private MediaFileDao mediaFileDao;
    @Autowired
    private ArtistDao artistDao;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private SettingsService settings;
    @MockitoBean
    private LastFmService lastFm;

    @Autowired
    private ApplicationContext context;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void preservesBothClientContracts() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFileDao.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes",
                    library.get(0).getId());
            assertNotNull(album);
            MediaFile song = mediaFileDao.getChildrenOf(album.getPath(), album.getFolderId(), true).get(0);
            artistDao.createOrUpdateArtist(new Artist(0, "Similar test artist", 1, Instant.now(), true, library.get(0).getId()));
            Artist artist = artistDao.getArtist("Similar test artist");
            assertNotNull(artist);
            ArtistBio bio = new ArtistBio("Biography", "mbid", "https://example.org/lastfm", "small", "medium", "large");
            when(lastFm.getSimilarArtists(any(MediaFile.class), anyInt(), anyBoolean(), anyList())).thenReturn(List.of(album));
            when(lastFm.getSimilarArtists(any(Artist.class), anyInt(), anyBoolean(), anyList())).thenReturn(List.of(artist));
            when(lastFm.getArtistBio(any(MediaFile.class), any())).thenReturn(bio);
            when(lastFm.getTopSongs(any(MediaFile.class), anyInt(), anyList())).thenReturn(List.of(song));
            when(lastFm.getArtistBio(any(Artist.class), any())).thenReturn(bio);
            securityService.createUser(new User("artist-info-user", null), "secret", "artist info test");
            folders.setMusicFoldersForUser("artist-info-user", List.of(library.get(0).getId()));
            UserSettings userSettings = settings.getUserSettings("artist-info-user");
            userSettings.setLocale(Locale.FRENCH);
            settings.updateUserSettings(userSettings);
            for (String endpoint : List.of("getArtistInfo", "getArtistInfo2")) {
                boolean second = endpoint.endsWith("2");
                int id = second ? artist.getId() : album.getId();
                String key = second ? "artistInfo2" : "artistInfo";
                for (String suffix : List.of("", ".view")) {
                    String path = "/rest/" + endpoint + suffix;
                    HttpResponse<String> json = send(path + "?" + credentials() + "&f=json&id=" + id + "&count=3&includeNotPresent=true", null, null);
                    assertInfo(body(json), key, id);
                    HttpResponse<String> xml = send(path + "?id=" + id, null, "admin:admin");
                    assertEquals(200, xml.statusCode());
                    assertTrue(xml.body(), xml.body().contains("<" + key + ">"));
                    assertTrue(xml.body(), xml.body().contains("<biography>Biography</biography>"));
                    HttpResponse<String> jsonp = send(path + "?" + credentials() + "&id=" + id + "&f=jsonp&callback=client.info", null, null);
                    assertTrue(jsonp.body(), jsonp.body().startsWith("client.info("));
                    assertInfo(mapper.readTree(jsonp.body().substring("client.info(".length(), jsonp.body().length() - 2)).path("subsonic-response"), key, id);
                    assertError(send(path + "?" + credentials() + "&f=json", null, null), 10);
                    assertEquals(400, send(path + "?" + credentials() + "&f=json&id=bad", null, null).statusCode());
                    assertError(send(path + "?" + credentials() + "&f=json&id=2147483647", null, null), 70);
                    assertError(send(path + "?f=json", null, null), 10);
                    assertError(send(path + "?f=json&v=" + TestCaseUtils.restApiVersion() + "&c=test&u=admin&p=wrong", null, null), 40);
                }
                assertInfo(body(send("/rest/" + endpoint + ".view", credentials() + "&f=json&id=" + id, null)), key, id);
                assertEquals(403, send("/rest/" + endpoint, credentials() + "&f=json&id=" + id, null).statusCode());
                assertInfo(body(send("/rest/" + endpoint + "?f=json&id=" + id + "&count=bad&includeNotPresent=bad", null, "artist-info-user:secret")), key, id);
                assertInfo(body(send("/rest/" + endpoint + "?f=json&id=" + id + "&count=7&includeNotPresent=YES", null, "artist-info-user:secret")), key, id);
            }
            JsonNode website = body(send("/rest/getArtistInfo?f=json&id=" + album.getId()
                    + "&includeWebsiteDetails=true&count=8&topSongsCount=100", null, "artist-info-user:secret"));
            assertInfo(website, "artistInfo", album.getId());
            assertEquals(album.getName(), website.path("artistInfo").path("similarArtist").path(0).path("name").asText());
            assertEquals(String.valueOf(song.getId()), website.path("websiteArtistInfo").path("topSong").path(0).path("id").asText());
            verify(lastFm).getTopSongs(any(MediaFile.class), eq(100), anyList());
            JsonNode ordinary = body(send("/rest/getArtistInfo?f=json&id=" + album.getId(), null, "artist-info-user:secret"));
            assertTrue(ordinary.path("websiteArtistInfo").isMissingNode());
            securityService.createUser(new User("artist-info-restricted", null), "secret", "artist info test");
            folders.setMusicFoldersForUser("artist-info-restricted", List.of(library.get(1).getId()));
            assertError(send("/rest/getArtistInfo?f=json&id=" + album.getId() + "&includeWebsiteDetails=true", null,
                    "artist-info-restricted:secret"), 50);
            HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            String baseUrl = "http://localhost:" + port + "/airsonic";
            HttpResponse<String> login = browser.send(HttpRequest.newBuilder(URI.create(baseUrl + "/login")).GET().build(), HttpResponse.BodyHandlers.ofString());
            Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
            assertTrue(token.find());
            String loginForm = "j_username=artist-info-user&j_password=secret&_csrf=" + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
            assertEquals(302, browser.send(HttpRequest.newBuilder(URI.create(baseUrl + "/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(loginForm)).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            HttpResponse<String> page = browser.send(HttpRequest.newBuilder(URI.create(baseUrl + "/main.view?id=" + album.getId())).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("/airsonic/script/subsonic-artist-info.js"));
            assertFalse(page.body().contains("/app/artist/info"));
            token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(page.body());
            assertTrue(token.find());
            String form = "f=json&c=airsonic-web&includeWebsiteDetails=true&id=" + album.getId();
            URI endpoint = URI.create(baseUrl + "/rest/getArtistInfo");
            assertEquals(403, browser.send(HttpRequest.newBuilder(endpoint).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            JsonNode browserResponse = body(browser.send(HttpRequest.newBuilder(endpoint).header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-CSRF-TOKEN", token.group(1)).POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString()));
            assertEquals(String.valueOf(song.getId()), browserResponse.path("websiteArtistInfo").path("topSong").path(0).path("id").asText());
            assertFalse(context.containsBean("artistWSController"));
            String oldId = StringUtil.utf8HexEncode(album.getFullPath(library.get(0).getPath()).toString());
            assertInfo(body(send("/rest/getArtistInfo?" + credentials() + "&f=json&id=" + oldId, null, null)), "artistInfo", album.getId());
            verify(lastFm, atLeastOnce()).getArtistBio(any(MediaFile.class), eq(Locale.FRENCH));
            verify(lastFm, atLeastOnce()).getArtistBio(any(Artist.class), eq(Locale.FRENCH));
            verify(lastFm, atLeastOnce()).getSimilarArtists(any(MediaFile.class), eq(7), eq(true), anyList());
            verify(lastFm, atLeastOnce()).getSimilarArtists(any(Artist.class), eq(7), eq(true), anyList());
            verify(lastFm, atLeastOnce()).getSimilarArtists(any(MediaFile.class), eq(3), eq(true), anyList());
            verify(lastFm, atLeastOnce()).getSimilarArtists(any(Artist.class), eq(3), eq(true), anyList());
            verify(lastFm, atLeastOnce()).getSimilarArtists(any(MediaFile.class), eq(20), eq(false), argThat(f -> f.size() == 1 && f.get(0).getId().equals(library.get(0).getId())));
            verify(lastFm, atLeastOnce()).getSimilarArtists(any(Artist.class), eq(20), eq(false), argThat(f -> f.size() == 1 && f.get(0).getId().equals(library.get(0).getId())));
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    private void assertInfo(JsonNode body, String key, int id) {
        assertEquals("ok", body.path("status").asText());
        JsonNode info = body.path(key);
        assertEquals("Biography", info.path("biography").asText());
        assertEquals("mbid", info.path("musicBrainzId").asText());
        assertEquals("https://example.org/lastfm", info.path("lastFmUrl").asText());
        assertEquals("small", info.path("smallImageUrl").asText());
        assertEquals("medium", info.path("mediumImageUrl").asText());
        assertEquals("large", info.path("largeImageUrl").asText());
        assertEquals(String.valueOf(id), info.path("similarArtist").path(0).path("id").asText());
    }

    private void assertError(HttpResponse<String> response, int code) throws Exception {
        JsonNode result = body(response);
        assertEquals("failed", result.path("status").asText());
        assertEquals(response.body(), code, result.path("error").path("code").asInt());
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }

    private String credentials() {
        return "v=" + TestCaseUtils.restApiVersion() + "&c=similar-test&u=admin&p=admin";
    }

    private HttpResponse<String> send(String path, String form, String basic) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/airsonic" + path))
                .timeout(Duration.ofSeconds(10));
        if (basic != null) {
            request.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
        }
        if (form != null) {
            request.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
