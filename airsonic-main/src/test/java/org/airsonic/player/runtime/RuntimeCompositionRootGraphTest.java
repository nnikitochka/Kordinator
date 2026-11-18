package org.airsonic.player.runtime;

import org.airsonic.player.dao.*;
import org.airsonic.player.monitor.MetricsManager;
import org.airsonic.player.service.*;
import org.airsonic.player.spring.KtorConfiguration;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;

import javax.cache.CacheManager;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeCompositionRootGraphTest {

    @TempDir
    Path tempHome;

    private String oldAirsonicHome;

    @BeforeEach
    void setUp() {
        oldAirsonicHome = System.getProperty("airsonic.home");
        System.setProperty("airsonic.home", tempHome.toAbsolutePath().toString());
    }

    @AfterEach
    void tearDown() {
        if (oldAirsonicHome != null) {
            System.setProperty("airsonic.home", oldAirsonicHome);
        } else {
            System.clearProperty("airsonic.home");
        }
    }

    @Test
    void assemblesGraphExplicitlyAndManagesLifecycleWithoutSpringContext() throws Exception {
        LifecycleRegistry lifecycleRegistry = new LifecycleRegistry();
        RuntimeConfiguration runtimeConfig = new RuntimeConfiguration("DOWN, UP");
        RuntimeCompositionRoot runtimeRoot = new RuntimeCompositionRoot(runtimeConfig, lifecycleRegistry);

        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:graphassembly_" + UUID.randomUUID().toString().replace('-', '_'));
        dataSource.setUser("sa");
        dataSource.setPassword("");

        PersistenceGraph persistence = runtimeRoot.createPersistenceGraph(dataSource);
        assertSame(persistence, runtimeRoot.createPersistenceGraph(dataSource));
        TransactionManager txManager = persistence.getTransactionManager();
        JdbcHelper jdbcHelper = persistence.getJdbcHelper();
        assertNotNull(txManager);
        assertNotNull(jdbcHelper);

        DaoGraph daoGraph = persistence.getDaos();
        UserDao userDao = daoGraph.getUserDao();
        PlayQueueDao playQueueDao = daoGraph.getPlayQueueDao();
        AlbumDao albumDao = daoGraph.getAlbumDao();
        ArtistDao artistDao = daoGraph.getArtistDao();
        AvatarDao avatarDao = daoGraph.getAvatarDao();
        BookmarkDao bookmarkDao = daoGraph.getBookmarkDao();
        CoverArtDao coverArtDao = daoGraph.getCoverArtDao();
        DatabaseDao databaseDao = daoGraph.getDatabaseDao();
        MediaFileDao mediaFileDao = daoGraph.getMediaFileDao();
        MusicFolderDao musicFolderDao = daoGraph.getMusicFolderDao();
        PlaylistDao playlistDao = daoGraph.getPlaylistDao();
        RatingDao ratingDao = daoGraph.getRatingDao();
        ShareDao shareDao = daoGraph.getShareDao();
        TranscodingDao transcodingDao = daoGraph.getTranscodingDao();

        KtorConfiguration config = new KtorConfiguration();

        assertNotNull(userDao);
        assertNotNull(playQueueDao);
        assertNotNull(albumDao);
        assertNotNull(artistDao);
        assertNotNull(avatarDao);
        assertNotNull(bookmarkDao);
        assertNotNull(coverArtDao);
        assertNotNull(databaseDao);
        assertNotNull(mediaFileDao);
        assertNotNull(musicFolderDao);
        assertNotNull(playlistDao);
        assertNotNull(ratingDao);
        assertNotNull(shareDao);
        assertNotNull(transcodingDao);

        // Cache & Settings
        MediaFolderService mediaFolderService = runtimeRoot.createMediaFolderService();
        assertSame(mediaFolderService, runtimeRoot.createMediaFolderService());
        CacheManager cacheManager = runtimeRoot.createCacheManager();
        assertSame(cacheManager, runtimeRoot.createCacheManager());
        assertNotNull(cacheManager);

        ConfigurationPropertiesService properties = config.configurationPropertiesService();
        assertNotNull(properties);

        StandardEnvironment environment = new StandardEnvironment();
        SettingsService settingsService = config.settingsService(userDao, avatarDao, environment, cacheManager, properties);
        assertNotNull(settingsService);

        // MediaFolderService and PlayerDao
        assertNotNull(mediaFolderService);

        PlayerDao playerDao = runtimeRoot.createPlayerDao();
        assertSame(playerDao, runtimeRoot.createPlayerDao());
        assertNotNull(playerDao);

        // Security & scheduling services
        SecurityService securityService = config.securityService(userDao, settingsService, mediaFolderService, cacheManager);
        assertNotNull(securityService);

        JWTSecurityService jwtSecurityService = config.jwtSecurityService(settingsService);
        assertNotNull(jwtSecurityService);

        TaskSchedulingService scheduler = config.taskSchedulingService(lifecycleRegistry);
        assertNotNull(scheduler);

        PathWatcherService pathWatcher = config.pathWatcherService(lifecycleRegistry);
        assertNotNull(pathWatcher);

        MetricsManager metricsManager = config.metricsManager(runtimeRoot);
        assertNotNull(metricsManager);

        DatabaseService databaseService = config.databaseService(settingsService, databaseDao, scheduler);
        assertNotNull(databaseService);

        // Custom lifecycle items to verify ordering and shutdown
        AtomicBoolean customStarted = new AtomicBoolean(false);
        AtomicBoolean customClosed = new AtomicBoolean(false);
        lifecycleRegistry.registerStartup("customStartup", () -> customStarted.set(true));
        lifecycleRegistry.registerShutdown("customShutdown", () -> customClosed.set(true));

        assertFalse(lifecycleRegistry.isShuttingDown());
        assertFalse(lifecycleRegistry.isShutdownComplete());

        lifecycleRegistry.start();
        assertTrue(customStarted.get(), "Startup hook should have run");

        lifecycleRegistry.shutdown();
        assertTrue(customClosed.get(), "Shutdown hook should have run");
        assertTrue(cacheManager.isClosed(), "Runtime lifecycle should close its cache manager");
        assertTrue(lifecycleRegistry.isShutdownComplete(), "Lifecycle should be marked as completed");
    }
}
