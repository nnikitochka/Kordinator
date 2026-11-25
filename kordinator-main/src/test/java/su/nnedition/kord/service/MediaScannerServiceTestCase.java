package su.nnedition.kord.service;

import su.nnedition.kord.service.media.MediaScannerService;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.io.Resources;
import su.nnedition.kord.TestCaseUtils;
import su.nnedition.kord.api.ScanningTestUtils;
import su.nnedition.kord.domain.Album;
import su.nnedition.kord.domain.Artist;
import su.nnedition.kord.domain.MediaFile;
import su.nnedition.kord.domain.MusicFolder;
import su.nnedition.kord.domain.MusicFolder.Type;
import su.nnedition.kord.runtime.DaoGraph;
import su.nnedition.kord.runtime.MediaRuntimeFixture;
import su.nnedition.kord.runtime.RuntimeDatabaseRule;
import su.nnedition.kord.service.media.MediaFileService;
import su.nnedition.kord.service.media.MediaFolderService;
import su.nnedition.kord.util.MusicFolderTestData;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Verifies media scanning against a migrated test database and the /MEDIAS fixtures.
 */



public class MediaScannerServiceTestCase {

    private DaoGraph daoGraph;

    @Rule
    public final RuntimeDatabaseRule database = new RuntimeDatabaseRule();

    private final MetricRegistry metrics = new MetricRegistry();

    private MediaScannerService mediaScannerService;

    private MediaFileService mediaFileService;

    private MediaFolderService mediaFolderService;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private UUID cleanupId = null;

    @Before
    public void setup() {
        MediaRuntimeFixture media = new MediaRuntimeFixture(database);
        daoGraph = media.getDaos();
        mediaScannerService = media.getScanner();
        mediaFileService = media.getMediaFiles();
        mediaFolderService = media.getMediaFolders();
        TestCaseUtils.waitForScanFinish(mediaScannerService);
    }

    @After
    public void cleanup() {
        if (cleanupId != null) {
            ScanningTestUtils.after(cleanupId, mediaFolderService);
            cleanupId = null;
        }
    }

    /**
     * Tests the MediaScannerService by scanning the test media library into an empty database.
     */
    @Test
    public void testScanLibrary() {
        Timer globalTimer = metrics.timer(MetricRegistry.name(MediaScannerServiceTestCase.class, "Timer.global"));

        Timer.Context globalTimerContext = globalTimer.time();
        List<MusicFolder> testFolders = MusicFolderTestData.getTestMusicFolders();
        cleanupId = ScanningTestUtils.before(testFolders, mediaFolderService, mediaScannerService);
        globalTimerContext.stop();

        // Music Folder Music must have 3 children
        List<MediaFile> listeMusicChildren = daoGraph.getMediaFileDao().getChildrenOf("", testFolders.get(0).getId(), false);
        Assert.assertEquals(3, listeMusicChildren.size());
        // Music Folder Music2 must have 1 children
        List<MediaFile> listeMusic2Children = daoGraph.getMediaFileDao().getChildrenOf("", testFolders.get(1).getId(), false);
        Assert.assertEquals(1, listeMusic2Children.size());

        System.out.println("--- List of all artists ---");
        System.out.println("artistName#albumCount");
        List<Artist> allArtists = daoGraph.getArtistDao().getAlphabetialArtists(0, Integer.MAX_VALUE, daoGraph.getMusicFolderDao().getAllMusicFolders());
        allArtists.forEach(artist -> System.out.println(artist.getName() + "#" + artist.getAlbumCount()));
        System.out.println("--- *********************** ---");

        System.out.println("--- List of all albums ---");
        System.out.println("name#artist");
        List<Album> allAlbums = daoGraph.getAlbumDao().getAlphabeticalAlbums(0, Integer.MAX_VALUE, true, true, daoGraph.getMusicFolderDao().getAllMusicFolders());
        allAlbums.forEach(album -> System.out.println(album.getName() + "#" + album.getArtist()));
        Assert.assertEquals(5, allAlbums.size());
        System.out.println("--- *********************** ---");

        List<MediaFile> listeSongs = daoGraph.getMediaFileDao().getSongsByGenre("Baroque Instrumental", 0, Integer.MAX_VALUE, daoGraph.getMusicFolderDao().getAllMusicFolders());
        Assert.assertEquals(2, listeSongs.size());

        // display out metrics report
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.report();

        System.out.print("End");
    }

    @Test
    public void testSpecialCharactersInFilename() throws Exception {
        String directoryName = "Muff1nman\u2019s \uFF0FMusic";
        String fileName = "Muff1nman\u2019s\uFF0FPiano.mp3";
        Path artistDir = temporaryFolder.newFolder(directoryName).toPath();
        Path musicFile = artistDir.resolve(fileName);
        Files.copy(Paths.get(Resources.getResource("MEDIAS/piano.mp3").toURI()), musicFile);

        MusicFolder musicFolder = new MusicFolder(1, temporaryFolder.getRoot().toPath(), "Music", Type.MEDIA, true, Instant.now());
        cleanupId = ScanningTestUtils.before(Arrays.asList(musicFolder), mediaFolderService, mediaScannerService);
        MediaFile mediaFile = mediaFileService.getMediaFile(musicFile);
        assertEquals(mediaFile.getRelativePath(), temporaryFolder.getRoot().toPath().relativize(musicFile));
        assertThat(mediaFile.getFolderId()).isEqualTo(musicFolder.getId());
        MediaFile relativeMediaFile = mediaFileService.getMediaFile(temporaryFolder.getRoot().toPath().relativize(musicFile), musicFolder);
        assertEquals(relativeMediaFile.getRelativePath(), mediaFile.getRelativePath());
    }

    @Test
    public void testNeverScanned() {
        mediaScannerService.neverScanned();
    }

    @Test
    public void testMusicBrainzReleaseIdTag() {

        // Add the "Music3" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusic3FolderPath();
        MusicFolder musicFolder = new MusicFolder(1, musicFolderFile, "Music3", Type.MEDIA, true, Instant.now());
        cleanupId = ScanningTestUtils.before(Arrays.asList(musicFolder), mediaFolderService, mediaScannerService);

        // Retrieve the "Music3" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = daoGraph.getMusicFolderDao().getMusicFolderForPath(musicFolder.getPath().toString());
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = daoGraph.getArtistDao().getAlphabetialArtists(0, Integer.MAX_VALUE, folders);
        Assert.assertEquals(1, allArtists.size());
        Artist artist = allArtists.get(0);
        Assert.assertEquals("TestArtist", artist.getName());
        Assert.assertEquals(1, artist.getAlbumCount());

        // Test that the album is correctly imported, along with its MusicBrainz release ID
        List<Album> allAlbums = daoGraph.getAlbumDao().getAlphabeticalAlbums(0, Integer.MAX_VALUE, true, true, folders);
        Assert.assertEquals(1, allAlbums.size());
        Album album = allAlbums.get(0);
        Assert.assertEquals("TestAlbum", album.getName());
        Assert.assertEquals("TestArtist", album.getArtist());
        Assert.assertEquals(1, album.getSongCount());
        Assert.assertEquals("0820752d-1043-4572-ab36-2df3b5cc15fa", album.getMusicBrainzReleaseId());
        Assert.assertEquals("TestAlbum", album.getPath());

        // Test that the music file is correctly imported, along with its MusicBrainz release ID and recording ID
        List<MediaFile> albumFiles = daoGraph.getMediaFileDao().getChildrenOf(allAlbums.get(0).getPath(), allAlbums.get(0).getFolderId(), false);
        Assert.assertEquals(1, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        Assert.assertEquals("Aria", file.getTitle());
        Assert.assertEquals("flac", file.getFormat());
        Assert.assertEquals("TestAlbum", file.getAlbumName());
        Assert.assertEquals("TestArtist", file.getArtist());
        Assert.assertEquals("TestArtist", file.getAlbumArtist());
        Assert.assertEquals(1, (long)file.getTrackNumber());
        Assert.assertEquals(2001, (long)file.getYear());
        Assert.assertEquals(album.getPath(), file.getParentPath());
        Assert.assertEquals(Paths.get(album.getPath()).resolve("01 - Aria.flac").toString(), file.getPath());
        Assert.assertEquals("0820752d-1043-4572-ab36-2df3b5cc15fa", file.getMusicBrainzReleaseId());
        Assert.assertEquals("831586f4-56f9-4785-ac91-447ae20af633", file.getMusicBrainzRecordingId());
    }
}
