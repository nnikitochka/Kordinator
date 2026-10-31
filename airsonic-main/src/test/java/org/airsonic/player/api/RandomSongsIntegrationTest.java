package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.RandomSearchCriteria;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.service.SecurityService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RandomSongsIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    @MockitoSpyBean private SearchService search;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void preservesRandomSongsContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true).get(0);
            doReturn(List.of(song)).when(search).getRandomSongs(any());
            User restricted = new User("random-restricted", null);
            restricted.setRoles(Set.of(User.Role.STREAM));
            security.createUser(restricted, "secret", "test");
            folders.setMusicFoldersForUser(restricted.getUsername(), Set.of(library.get(0).getId()));
            for (String suffix : List.of("", ".view")) {
                JsonNode response = body(send(suffix, "f=json&size=4&genre=Rock&fromYear=1990&toYear=2000&musicFolderId=" + library.get(0).getId(), false, false));
                assertEquals("ok", response.path("status").asText());
                assertEquals(song.getId().toString(), response.path("randomSongs").path("song").get(0).path("id").asText());
                RandomSearchCriteria criteria = lastCriteria();
                assertEquals(4, criteria.getCount());
                assertEquals("Rock", criteria.getGenre());
                assertEquals(Integer.valueOf(1990), criteria.getFromYear());
                assertEquals(Integer.valueOf(2000), criteria.getToYear());
                assertEquals(List.of(library.get(0).getId()), criteria.getMusicFolders().stream().map(MusicFolder::getId).toList());
                body(send(suffix, "f=json&size=bad", false, true));
                criteria = lastCriteria();
                assertEquals(10, criteria.getCount());
                assertNull(criteria.getGenre());
                assertNull(criteria.getFromYear());
                assertNull(criteria.getToYear());
                assertEquals(List.of(library.get(0).getId()), criteria.getMusicFolders().stream().map(MusicFolder::getId).toList());
                body(send(suffix, "f=json&size=-5", false, false));
                assertEquals(0, lastCriteria().getCount());
                body(send(suffix, "f=json&size=999", false, false));
                assertEquals(500, lastCriteria().getCount());
                body(send(suffix, "f=json&musicFolderId=" + library.get(1).getId(), false, true));
                assertTrue(lastCriteria().getMusicFolders().isEmpty());
                for (String invalid : List.of("fromYear", "toYear", "musicFolderId")) {
                    assertEquals(400, send(suffix, "f=json&" + invalid + "=bad", false, false).statusCode());
                }
                assertTrue(send(suffix, "f=xml", false, false).body().contains("<randomSongs"));
                assertTrue(send(suffix, "f=jsonp&callback=client.test", false, false).body().startsWith("client.test("));
            }
            assertEquals("ok", body(send(".view", "f=json", true, false)).path("status").asText());
            assertEquals(403, send("", "f=json", true, false).statusCode());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }
    private RandomSearchCriteria lastCriteria() {
        ArgumentCaptor<RandomSearchCriteria> captor = ArgumentCaptor.forClass(RandomSearchCriteria.class);
        verify(search, atLeastOnce()).getRandomSongs(captor.capture());
        return captor.getAllValues().getLast();
    }
    private HttpResponse<String> send(String suffix, String query, boolean post, boolean restricted) throws Exception {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=random-test&u=" + (restricted ? "random-restricted&p=secret" : "admin&p=admin") + "&" + query;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/getRandomSongs" + suffix + (post ? "" : "?" + params)));
        if (post) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(params));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
}
