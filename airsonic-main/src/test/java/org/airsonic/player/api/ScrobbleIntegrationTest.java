package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.PlayStatus;
import org.airsonic.player.service.AudioScrobblerService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.StatusService;
import org.airsonic.player.util.HomeRule;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ScrobbleIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @MockitoBean private AudioScrobblerService scrobbler;
    @MockitoSpyBean private StatusService status;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void preservesScrobbleContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            List<MediaFile> songs = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true);
            MediaFile first = songs.get(0), second = songs.get(1);
            int before = mediaFiles.getMediaFile(first.getId()).getPlayCount();
            String ids = "id=" + first.getId() + "&id=" + second.getId();
            for (String suffix : List.of("", ".view")) {
                clearInvocations(scrobbler, status);
                assertEquals("ok", body(send(suffix, "f=json&" + ids + "&time=1000&time=2000&submission=on", false)).path("status").asText());
                verify(scrobbler).register(argThat(f -> f.getId().equals(first.getId())), eq("admin"), eq(true), eq(Instant.ofEpochMilli(1000)));
                verify(scrobbler).register(argThat(f -> f.getId().equals(second.getId())), eq("admin"), eq(true), eq(Instant.ofEpochMilli(2000)));
                ArgumentCaptor<PlayStatus> plays = ArgumentCaptor.forClass(PlayStatus.class);
                verify(status, times(2)).addRemotePlay(plays.capture());
                assertEquals(Instant.ofEpochMilli(1000), plays.getAllValues().get(0).getTime());
                assertEquals(Instant.ofEpochMilli(2000), plays.getAllValues().get(1).getTime());
                clearInvocations(scrobbler, status);
                assertEquals(0, body(send(suffix, "f=json&" + ids + "&time=1000", false)).path("error").path("code").asInt());
                verifyNoInteractions(scrobbler);
                verify(status, never()).addRemotePlay(any());
                assertEquals(10, body(send(suffix, "f=json", false)).path("error").path("code").asInt());
                assertEquals(400, send(suffix, "f=json&id=bad", false).statusCode());
                assertEquals("ok", body(send(suffix, "f=json&id=2147483647", false)).path("status").asText());
                assertEquals("ok", body(send(suffix, "f=json&id=" + first.getId() + "&time=bad&submission=bad", false)).path("status").asText());
                verify(scrobbler).register(argThat(f -> f.getId().equals(first.getId())), eq("admin"), eq(false), isNull());
                assertTrue(send(suffix, "f=xml&id=2147483647", false).body().contains("<subsonic-response"));
                assertTrue(send(suffix, "f=jsonp&callback=client.test&id=2147483647", false).body().startsWith("client.test("));
            }
            assertTrue(mediaFiles.getMediaFile(first.getId()).getPlayCount() >= before + 4);
            clearInvocations(scrobbler);
            assertEquals("ok", body(send(".view", "f=json&id=" + first.getId(), true)).path("status").asText());
            verify(scrobbler).register(any(), eq("admin"), eq(true), isNull());
            assertEquals(403, send("", "f=json&id=" + first.getId(), true).statusCode());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }
    private HttpResponse<String> send(String suffix, String query, boolean post) throws Exception {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=scrobble-test&u=admin&p=admin&" + query;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/scrobble" + suffix + (post ? "" : "?" + params)));
        if (post) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(params));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
}
