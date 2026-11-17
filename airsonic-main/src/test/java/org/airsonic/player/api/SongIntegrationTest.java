package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.HomeRule;
import org.airsonic.player.util.MusicFolderTestData;
import org.airsonic.player.util.StringUtil;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SongIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private MediaFolderService folders;
    @Autowired private MediaScannerService scanner;
    @Autowired private MediaFileDao mediaFiles;
    @Autowired private SecurityService security;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test public void preservesSongContract() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true).get(0);
            User restricted = new User("song-restricted", null);
            restricted.setRoles(Set.of(User.Role.STREAM));
            security.createUser(restricted, "secret", "test");
            folders.setMusicFoldersForUser(restricted.getUsername(), Set.of(library.get(1).getId()));
            String id = "id=" + song.getId();
            String legacyId = StringUtil.utf8HexEncode(song.getFullPath(library.get(0).getPath()).toString());
            for (String suffix : List.of("", ".view")) {
                JsonNode result = body(send(suffix, "f=json&" + id, false, false)).path("song");
                assertEquals(song.getId().toString(), result.path("id").asText());
                assertEquals(song.getTitle(), result.path("title").asText());
                assertFalse(result.path("isDir").asBoolean());
                assertEquals(song.getId().toString(), body(send(suffix, "f=json&id=" + legacyId, false, false)).path("song").path("id").asText());
                assertEquals(10, body(send(suffix, "f=json", false, false)).path("error").path("code").asInt());
                assertEquals(70, body(send(suffix, "f=json&id=2147483647", false, false)).path("error").path("code").asInt());
                assertEquals(70, body(send(suffix, "f=json&id=" + album.getId(), false, false)).path("error").path("code").asInt());
                assertEquals(50, body(send(suffix, "f=json&" + id, false, true)).path("error").path("code").asInt());
                assertEquals(400, send(suffix, "f=json&id=abc", false, false).statusCode());
                assertTrue(send(suffix, "f=xml&" + id, false, false).body().contains("<song"));
                assertTrue(send(suffix, "f=jsonp&callback=client.test&" + id, false, false).body().startsWith("client.test("));
            }
            assertEquals("ok", body(send(".view", "f=json&" + id, true, false)).path("status").asText());
            assertEquals(403, send("", "f=json&" + id, true, false).statusCode());
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    @Test public void concurrentRequestsKeepCallerFolderRightsIsolated() throws Exception {
        List<MusicFolder> library = MusicFolderTestData.getTestMusicFolders();
        UUID cleanup = ScanningTestUtils.before(library, folders, scanner);
        try {
            MediaFile album = mediaFiles.getMediaFile("_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes", library.get(0).getId());
            MediaFile song = mediaFiles.getChildrenOf(album.getPath(), album.getFolderId(), true).get(0);
            User allowed = new User("song-allowed-concurrent", null);
            allowed.setRoles(Set.of(User.Role.STREAM));
            security.createUser(allowed, "allowed-secret", "test");
            folders.setMusicFoldersForUser(allowed.getUsername(), Set.of(library.get(0).getId()));

            User denied = new User("song-denied-concurrent", null);
            denied.setRoles(Set.of(User.Role.STREAM));
            security.createUser(denied, "denied-secret", "test");
            folders.setMusicFoldersForUser(denied.getUsername(), Set.of(library.get(1).getId()));

            List<CompletableFuture<HttpResponse<String>>> requests = new ArrayList<>();
            for (int requestNumber = 0; requestNumber < 20; requestNumber++) {
                requests.add(sendAsyncAs(allowed.getUsername(), "allowed-secret", "f=json&id=" + song.getId()));
                requests.add(sendAsyncAs(denied.getUsername(), "denied-secret", "f=json&id=" + song.getId()));
            }
            for (int requestNumber = 0; requestNumber < requests.size(); requestNumber++) {
                JsonNode response = body(requests.get(requestNumber).get(90, TimeUnit.SECONDS));
                if (requestNumber % 2 == 0) {
                    assertEquals("ok", response.path("status").asText());
                    assertEquals(song.getId().toString(), response.path("song").path("id").asText());
                } else {
                    assertEquals(50, response.path("error").path("code").asInt());
                }
            }
        } finally {
            ScanningTestUtils.after(cleanup, folders);
            folders.expunge();
        }
    }

    private HttpResponse<String> send(String suffix, String query, boolean post, boolean restricted) throws Exception {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=song-test&u=" + (restricted ? "song-restricted&p=secret" : "admin&p=admin") + "&" + query;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/getSong" + suffix + (post ? "" : "?" + params)));
        if (post) request.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(params));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private CompletableFuture<HttpResponse<String>> sendAsyncAs(String username, String password, String query) {
        String params = "v=" + TestCaseUtils.restApiVersion() + "&c=song-test&u=" + username + "&p=" + password + "&" + query;
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/airsonic/rest/getSong?" + params)).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(response.body(), 200, response.statusCode());
        return mapper.readTree(response.body()).path("subsonic-response");
    }
}
