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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
        LyricsService lyricsService = runtimeRoot.createLyricsService();
        assertSame(lyricsService, runtimeRoot.createLyricsService());

        ConfigurationPropertiesService properties = config.configurationPropertiesService();
        assertNotNull(properties);

        StandardEnvironment environment = new StandardEnvironment();
        SettingsService settingsService = config.settingsService(userDao, avatarDao, environment, cacheManager, properties);
        assertNotNull(settingsService);
        VersionService versionService = runtimeRoot.createVersionService();
        assertSame(versionService, runtimeRoot.createVersionService());
        ServerInfoService serverInfoService = runtimeRoot.createServerInfoService(versionService, settingsService);
        assertSame(serverInfoService, runtimeRoot.createServerInfoService(versionService, settingsService));

        // MediaFolderService and PlayerDao
        assertNotNull(mediaFolderService);

        PlayerDao playerDao = runtimeRoot.createPlayerDao();
        assertSame(playerDao, runtimeRoot.createPlayerDao());
        assertNotNull(playerDao);

        // Security & scheduling services
        SecurityService securityService = runtimeRoot.createSecurityService(userDao, settingsService, mediaFolderService);
        assertSame(securityService, runtimeRoot.createSecurityService(userDao, settingsService, mediaFolderService));
        assertNotNull(securityService);
        org.airsonic.player.service.metadata.JaudiotaggerParser parser = runtimeRoot.createJaudiotaggerParser();
        assertSame(parser, runtimeRoot.createJaudiotaggerParser());
        org.airsonic.player.service.metadata.MetaDataParserFactory metaDataParserFactory =
                runtimeRoot.createMetaDataParserFactory();
        assertSame(metaDataParserFactory, runtimeRoot.createMetaDataParserFactory());
        CoverArtService coverArtService = runtimeRoot.createCoverArtService(daoGraph.getCoverArtDao());
        assertSame(coverArtService, runtimeRoot.createCoverArtService(daoGraph.getCoverArtDao()));
        org.airsonic.player.i18n.LocaleResolver localeResolver = mock(org.airsonic.player.i18n.LocaleResolver.class);
        MediaFileService mediaFileService = runtimeRoot.createMediaFileService(securityService, settingsService,
                mediaFileDao, albumDao, parser, metaDataParserFactory, coverArtService, localeResolver);
        assertSame(mediaFileService, runtimeRoot.createMediaFileService(securityService, settingsService,
                mediaFileDao, albumDao, parser, metaDataParserFactory, coverArtService, localeResolver));
        BookmarkService bookmarkService = runtimeRoot.createBookmarkService(daoGraph.getBookmarkDao(), mediaFileService);
        assertSame(bookmarkService, runtimeRoot.createBookmarkService(daoGraph.getBookmarkDao(), mediaFileService));
        AudioScrobblerService audioScrobblerService =
                runtimeRoot.createAudioScrobblerService(settingsService, securityService);
        assertSame(audioScrobblerService, runtimeRoot.createAudioScrobblerService(settingsService, securityService));
        UploadFileService uploadFileService = runtimeRoot.createUploadFileService(securityService);
        assertSame(uploadFileService, runtimeRoot.createUploadFileService(securityService));
        RatingService ratingService = runtimeRoot.createRatingService(daoGraph.getRatingDao(), securityService,
                mediaFileService);
        assertSame(ratingService, runtimeRoot.createRatingService(daoGraph.getRatingDao(), securityService,
                mediaFileService));
        MediaDirectoryService mediaDirectoryService = runtimeRoot.createMediaDirectoryService(mediaFileService,
                ratingService, securityService, settingsService);
        assertSame(mediaDirectoryService, runtimeRoot.createMediaDirectoryService(mediaFileService,
                ratingService, securityService, settingsService));
        MusicIndexService musicIndexService = runtimeRoot.createMusicIndexService(settingsService, mediaFileService);
        assertSame(musicIndexService, runtimeRoot.createMusicIndexService(settingsService, mediaFileService));
        PasswordRecoveryService passwordRecoveryService =
                runtimeRoot.createPasswordRecoveryService(settingsService, securityService);
        assertSame(passwordRecoveryService,
                runtimeRoot.createPasswordRecoveryService(settingsService, securityService));

        JWTSecurityService jwtSecurityService = runtimeRoot.createJwtSecurityService(settingsService);
        assertSame(jwtSecurityService, runtimeRoot.createJwtSecurityService(settingsService));
        assertNotNull(jwtSecurityService);
        LastFmService lastFmService = runtimeRoot.createLastFmService(mediaFileDao, mediaFileService, artistDao);
        assertSame(lastFmService, runtimeRoot.createLastFmService(mediaFileDao, mediaFileService, artistDao));
        ShareService shareService = runtimeRoot.createShareService(daoGraph.getShareDao(), mediaFileService, jwtSecurityService);
        assertSame(shareService, runtimeRoot.createShareService(daoGraph.getShareDao(), mediaFileService, jwtSecurityService));
        CoverArtSearchService coverArtSearchService = runtimeRoot.createCoverArtSearchService(
                securityService, mediaFileService, lastFmService, coverArtService);
        assertSame(coverArtSearchService, runtimeRoot.createCoverArtSearchService(
                securityService, mediaFileService, lastFmService, coverArtService));
        org.airsonic.player.service.metadata.MetaDataParserFactory parserFactory =
                mock(org.airsonic.player.service.metadata.MetaDataParserFactory.class);
        CaptionService captionService = runtimeRoot.createCaptionService(mediaFileService, mediaFolderService,
                settingsService, parserFactory, jwtSecurityService);
        assertSame(captionService, runtimeRoot.createCaptionService(mediaFileService, mediaFolderService,
                settingsService, parserFactory, jwtSecurityService));
        VideoPlaybackService videoPlaybackService = runtimeRoot.createVideoPlaybackService(mediaFileService,
                captionService, jwtSecurityService, bookmarkService, settingsService);
        assertSame(videoPlaybackService, runtimeRoot.createVideoPlaybackService(mediaFileService,
                captionService, jwtSecurityService, bookmarkService, settingsService));

        TaskSchedulingService scheduler = config.taskSchedulingService(lifecycleRegistry);
        assertNotNull(scheduler);
        StatusService statusService = runtimeRoot.createStatusService(
                mediaFileService, mediaFolderService, settingsService, scheduler);
        assertSame(statusService, runtimeRoot.createStatusService(
                mediaFileService, mediaFolderService, settingsService, scheduler));
        TranscodingService transcodingService = runtimeRoot.createTranscodingService(
                daoGraph.getTranscodingDao(), settingsService, mediaFolderService);
        assertSame(transcodingService, runtimeRoot.createTranscodingService(
                daoGraph.getTranscodingDao(), settingsService, mediaFolderService));
        PlayerDao playerServiceDao = mock(PlayerDao.class);
        PlayerService playerService = runtimeRoot.createPlayerService(playerServiceDao, statusService,
                securityService, transcodingService);
        assertSame(playerService, runtimeRoot.createPlayerService(playerServiceDao, statusService,
                securityService, transcodingService));
        verify(playerServiceDao).deleteOldPlayers(60);
        JukeboxLegacySubsonicService legacyJukebox = runtimeRoot.createJukeboxLegacySubsonicService(
                transcodingService, audioScrobblerService, statusService, settingsService, securityService,
                mediaFileService);
        assertSame(legacyJukebox, runtimeRoot.createJukeboxLegacySubsonicService(
                transcodingService, audioScrobblerService, statusService, settingsService, securityService,
                mediaFileService));
        JukeboxService jukeboxService = runtimeRoot.createJukeboxService(legacyJukebox);
        assertSame(jukeboxService, runtimeRoot.createJukeboxService(legacyJukebox));
        PlaylistService playlistService = mock(PlaylistService.class);
        SearchService searchService = mock(SearchService.class);
        PlayQueueService playQueueService = runtimeRoot.createPlayQueueService(
                searchService, jukeboxService, settingsService, mediaFileService,
                lastFmService, ratingService, playlistService, mediaFileDao, mediaFolderService,
                playQueueDao, jwtSecurityService);
        assertSame(playQueueService, runtimeRoot.createPlayQueueService(
                searchService, jukeboxService, settingsService, mediaFileService,
                lastFmService, ratingService, playlistService, mediaFileDao, mediaFolderService,
                playQueueDao, jwtSecurityService));

        PathWatcherService pathWatcher = config.pathWatcherService(lifecycleRegistry);
        assertNotNull(pathWatcher);

        MetricsManager metricsManager = config.metricsManager(runtimeRoot);
        assertNotNull(metricsManager);

        DatabaseService databaseService = runtimeRoot.createDatabaseService(settingsService, databaseDao, scheduler);
        assertSame(databaseService, runtimeRoot.createDatabaseService(settingsService, databaseDao, scheduler));

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
