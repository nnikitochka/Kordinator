package org.airsonic.player.api;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class AirsonicRestApiIntTest {

    private static final String CLIENT_NAME = "airsonic";
    private static final String AIRSONIC_USER = "admin";
    private static final String AIRSONIC_PASSWORD = "admin";
    private static final String EXPECTED_FORMAT = "json";

    private static String AIRSONIC_API_VERSION = TestCaseUtils.restApiVersion();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MediaScannerService mediaScannerService;

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private MediaFileDao mediaFileDao;

    @ClassRule
    public static final HomeRule classRule = new HomeRule(); // sets airsonic.home to a temporary dir

    @Test
    public void pingTest() throws Exception {
        mvc.perform(get("/rest/ping")
                .param("v", AIRSONIC_API_VERSION)
                .param("c", CLIENT_NAME)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.version").value(AIRSONIC_API_VERSION))
                .andDo(print());
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

            mvc.perform(get("/rest/getMusicDirectory")
                    .param("v", AIRSONIC_API_VERSION)
                    .param("c", CLIENT_NAME)
                    .param("u", AIRSONIC_USER)
                    .param("p", AIRSONIC_PASSWORD)
                    .param("f", EXPECTED_FORMAT)
                    .param("id", String.valueOf(albumDir.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                    // The dominant color of the folder cover (cover.jpg) is known/deterministic.
                    .andExpect(jsonPath("$.subsonic-response.directory.accentColor").value("#A58866"))
                    .andExpect(jsonPath("$.subsonic-response.directory.child[0].accentColor").value("#A58866"))
                    .andDo(print());
            assertSearchEndpoints(testFolders);
        } finally {
            ScanningTestUtils.after(cleanupId, mediaFolderService);
        }
    }

    private void assertSearchEndpoints(List<MusicFolder> testFolders) throws Exception {
            mvc.perform(get("/rest/search")
                    .param("v", AIRSONIC_API_VERSION)
                    .param("c", CLIENT_NAME)
                    .param("u", AIRSONIC_USER)
                    .param("p", AIRSONIC_PASSWORD)
                    .param("f", "json")
                    .param("any", "Reed Neck Reel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                    .andExpect(jsonPath("$.subsonic-response.searchResult.match[0].title").value("Reed Neck Reel"));

            mvc.perform(post("/rest/search2").with(csrf())
                    .param("v", AIRSONIC_API_VERSION)
                    .param("c", CLIENT_NAME)
                    .param("u", AIRSONIC_USER)
                    .param("p", AIRSONIC_PASSWORD)
                    .param("f", "json")
                    .param("query", "Reed Neck Reel")
                    .param("musicFolderId", String.valueOf(testFolders.get(0).getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                    .andExpect(jsonPath("$.subsonic-response.searchResult2.song[0].title").value("Reed Neck Reel"));

            mvc.perform(get("/rest/search3")
                    .param("v", AIRSONIC_API_VERSION)
                    .param("c", CLIENT_NAME)
                    .param("u", AIRSONIC_USER)
                    .param("p", AIRSONIC_PASSWORD)
                    .param("f", "json")
                    .param("query", "Reed Neck Reel")
                    .param("musicFolderId", String.valueOf(testFolders.get(0).getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                    .andExpect(jsonPath("$.subsonic-response.searchResult3.song[0].title").value("Reed Neck Reel"));

            mvc.perform(get("/rest/search")
                    .param("v", AIRSONIC_API_VERSION)
                    .param("c", CLIENT_NAME)
                    .param("u", AIRSONIC_USER)
                    .param("p", AIRSONIC_PASSWORD)
                    .param("f", "xml")
                    .param("any", "Reed Neck Reel"))
                    .andExpect(status().isOk())
                    .andExpect(xpath("/subsonic-response/@status").string("ok"))
                    .andExpect(xpath("/subsonic-response/searchResult/match/@title").string("Reed Neck Reel"));

            mvc.perform(get("/rest/search2")
                    .param("v", AIRSONIC_API_VERSION)
                    .param("c", CLIENT_NAME)
                    .param("u", AIRSONIC_USER)
                    .param("p", AIRSONIC_PASSWORD)
                    .param("f", "xml")
                    .param("query", "Reed Neck Reel")
                    .param("musicFolderId", String.valueOf(testFolders.get(0).getId())))
                    .andExpect(status().isOk())
                    .andExpect(xpath("/subsonic-response/@status").string("ok"))
                    .andExpect(xpath("/subsonic-response/searchResult2/song/@title").string("Reed Neck Reel"));

            mvc.perform(get("/rest/search3")
                    .param("v", AIRSONIC_API_VERSION)
                    .param("c", CLIENT_NAME)
                    .param("u", AIRSONIC_USER)
                    .param("p", AIRSONIC_PASSWORD)
                    .param("f", "xml")
                    .param("query", "Reed Neck Reel")
                    .param("musicFolderId", String.valueOf(testFolders.get(0).getId())))
                    .andExpect(status().isOk())
                    .andExpect(xpath("/subsonic-response/@status").string("ok"))
                    .andExpect(xpath("/subsonic-response/searchResult3/song/@title").string("Reed Neck Reel"));

            mvc.perform(get("/rest/search3.view")
                    .param("v", AIRSONIC_API_VERSION)
                    .param("c", CLIENT_NAME)
                    .param("u", AIRSONIC_USER)
                    .param("p", AIRSONIC_PASSWORD)
                    .param("f", "xml")
                    .param("query", "Reed Neck Reel")
                    .param("musicFolderId", String.valueOf(testFolders.get(0).getId())))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/rest/search3"));
    }
}
