package org.airsonic.player.service.search;

import org.airsonic.player.api.ScanningTestUtils;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.media.MediaFolderService;
import org.airsonic.player.service.media.MediaScannerService;
import org.airsonic.player.runtime.DaoGraph;
import org.airsonic.player.runtime.MediaRuntimeFixture;
import org.airsonic.player.runtime.RuntimeDatabaseRule;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * Abstract class for scanning MusicFolder.
 */
public abstract class AbstractAirsonicHomeTest {

    @ClassRule
    public static final RuntimeDatabaseRule database = new RuntimeDatabaseRule();

    /*
     * Currently, Maven is executing test classes in series,
     * so this class can hold the state.
     * When executing in parallel, subclasses should override this.
     */
    private static AtomicBoolean dataBasePopulated = new AtomicBoolean();

    // Above.
    private static AtomicBoolean dataBaseReady = new AtomicBoolean();

    protected MediaScannerService mediaScannerService;

    protected MediaFolderService mediaFolderService;
    protected DaoGraph daoGraph;
    protected SearchService searchService;
    protected IndexManager indexManager;

    @Before
    public void setUpMediaServices() {
        MediaRuntimeFixture media = new MediaRuntimeFixture(database);
        mediaScannerService = media.getScanner();
        mediaFolderService = media.getMediaFolders();
        daoGraph = media.getDaos();
        searchService = media.getSearch();
        indexManager = media.getIndex();
    }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    public AtomicBoolean dataBasePopulated() {
        return dataBasePopulated;
    }

    public AtomicBoolean dataBaseReady() {
        return dataBaseReady;
    }

    public List<MusicFolder> getMusicFolders() {
        return MusicFolderTestData.getTestMusicFolders();
    }

    public static MediaFolderService cleanupMediaFolderService;

    public final UUID populateDatabaseOnlyOnce() {
        UUID id = null;
        if (!dataBasePopulated().get()) {
            dataBasePopulated().set(true);
            cleanupMediaFolderService = mediaFolderService;
            // wait for previous startup scan to finish
            while (mediaScannerService.isScanning()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            id = ScanningTestUtils.before(getMusicFolders(), mediaFolderService, mediaScannerService);

            dataBaseReady().set(true);
        } else {
            while (!dataBaseReady().get()) {
                try {
                    // The subsequent test method waits while reading DB data.
                    for (int i = 0; i < 10; i++) {
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        return id;
    }

    public static void cleanup(UUID id) {
        if (id != null) {
            ScanningTestUtils.after(id, cleanupMediaFolderService);
        }
    }
}
