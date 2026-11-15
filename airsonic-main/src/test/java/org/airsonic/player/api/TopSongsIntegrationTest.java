package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.HomeRule;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class TopSongsIntegrationTest {
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
    private SecurityService securityService;
    @MockitoBean
    private LastFmService lastFm;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void preservesTopSongsContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFileDao.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFileDao.getChildrenOf(album.getPath(), album.getFolderId(), true).get(0);
            when(lastFm.getTopSongs(anyString(), anyInt(), anyList())).thenReturn(List.of(song));
            securityService.createUser(new User("top-user", null), "secret", "top songs test");
            folders.setMusicFoldersForUser("top-user", List.of(library.get(0).getId()));
            for (String suffix : List.of("", ".view")) {
                String path = "/rest/getTopSongs" + suffix;
                HttpResponse<String> json = send(path + "?" + credentials() + "&f=json&artist=Test&count=3", null, null);
                assertSong(body(json), "topSongs", song);
                HttpResponse<String> xml = send(path + "?artist=Test", null, "admin:admin");
                assertEquals(200, xml.statusCode());
                assertTrue(xml.body(), xml.body().contains("<topSongs>"));
                HttpResponse<String> jsonp = send(path + "?" + credentials() + "&artist=Test&f=jsonp&callback=client.songs", null, null);
                assertTrue(jsonp.body(), jsonp.body().startsWith("client.songs("));
                assertError(send(path + "?" + credentials() + "&f=json", null, null), 10);
                assertSong(body(send(path + "?artist=Test&f=json&count=bad", null, "top-user:secret")), "topSongs", song);
                assertSong(body(send(path + "?" + credentials() + "&artist=Test&f=json&count=-2", null, null)), "topSongs", song);
            }
            assertSong(body(send("/rest/getTopSongs.view", credentials() + "&f=json&artist=Test", null)), "topSongs", song);
            assertEquals(403, send("/rest/getTopSongs", credentials() + "&f=json&artist=Test", null).statusCode());
            verify(lastFm, atLeastOnce()).getTopSongs(eq("Test"), eq(3), anyList());
            verify(lastFm, atLeastOnce()).getTopSongs(eq("Test"), eq(-2), anyList());
            verify(lastFm, atLeastOnce()).getTopSongs(eq("Test"), eq(50),
                    argThat(f -> f.size() == 1 && f.get(0).getId().equals(library.get(0).getId())));
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    private void assertSong(JsonNode body, String key, MediaFile song) {
        assertEquals("ok", body.path("status").asText());
        assertEquals(TestCaseUtils.restApiVersion(), body.path("version").asText());
        assertEquals(String.valueOf(song.getId()), body.path(key).path("song").path(0).path("id").asText());
        assertEquals(song.getName(), body.path(key).path("song").path(0).path("title").asText());
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
