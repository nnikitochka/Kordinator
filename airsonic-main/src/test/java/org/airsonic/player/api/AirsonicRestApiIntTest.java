package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.util.HomeRule;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AirsonicRestApiIntTest {

    private static final String CLIENT_NAME = "airsonic";
    private static final String AIRSONIC_USER = "admin";
    private static final String AIRSONIC_PASSWORD = "admin";
    private static final String EXPECTED_FORMAT = "json";
    private static final String AIRSONIC_API_VERSION = TestCaseUtils.restApiVersion();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    @ClassRule
    public static final HomeRule classRule = new HomeRule();

    @LocalServerPort
    private int port;

    @Autowired
    private MediaScannerService mediaScannerService;

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private MediaFileDao mediaFileDao;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    public void licenseTest() throws Exception {
        HttpResponse<String> response = get("/rest/getLicense", null);
        assertEquals(response.body(), 200, response.statusCode());
        JsonNode envelope = json.readTree(response.body()).path("subsonic-response");
        assertEquals("ok", envelope.path("status").asText());
        assertEquals(AIRSONIC_API_VERSION, envelope.path("version").asText());
    }

    @Test
    public void getMusicDirectoryExposesAccentColor() throws Exception {
        List<MusicFolder> testFolders = MusicFolderTestData.getTestMusicFolders();
        UUID cleanupId = ScanningTestUtils.before(testFolders, mediaFolderService, mediaScannerService);
        try {
            // The album directory media file, scanned from the test library.
            MediaFile albumDir = mediaFileDao.getMediaFile(
                    "_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", testFolders.get(0).getId());
            assertNotNull("Album directory not found after scan", albumDir);

            HttpResponse<String> response = get("/rest/getMusicDirectory", "id=" + albumDir.getId());
            assertEquals(response.body(), 200, response.statusCode());
            JsonNode envelope = json.readTree(response.body()).path("subsonic-response");
            assertEquals("ok", envelope.path("status").asText());
            // The dominant color of the folder cover (cover.jpg) is known/deterministic.
            assertEquals("#A58866", envelope.path("directory").path("accentColor").asText());
            assertEquals("#A58866", envelope.path("directory").path("child").get(0).path("accentColor").asText());
        } finally {
            ScanningTestUtils.after(cleanupId, mediaFolderService);
        }
    }

    private HttpResponse<String> get(String path, String extraParams) throws Exception {
        String params = "v=" + AIRSONIC_API_VERSION + "&c=" + CLIENT_NAME + "&u=" + AIRSONIC_USER
                + "&p=" + AIRSONIC_PASSWORD + "&f=" + EXPECTED_FORMAT;
        if (extraParams != null && !extraParams.isEmpty()) params += "&" + extraParams;
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic" + path + "?" + params))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
