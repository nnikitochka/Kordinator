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

import javax.cache.CacheManager;
import java.nio.file.Path;
import java.util.List;
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
        RuntimeConfiguration runtimeConfig = new RuntimeConfiguration(java.util.Map.of(
                RuntimeConfiguration.HEALTH_STATUS_ORDER_KEY, "DOWN, UP",
                "MediaScannerParallelism", "2",
                "IndexCreationInterval", "5"));
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

        org.airsonic.player.service.search.AnalyzerFactory analyzerFactory = runtimeRoot.createAnalyzerFactory();
        assertSame(analyzerFactory, config.analyzerFactory(runtimeRoot));
        assertSame(analyzerFactory, runtimeRoot.createAnalyzerFactory());
        org.airsonic.player.service.search.DocumentFactory documentFactory = runtimeRoot.createDocumentFactory();
        assertSame(documentFactory, config.documentFactory(runtimeRoot));
        assertSame(documentFactory, runtimeRoot.createDocumentFactory());
        org.airsonic.player.service.search.IndexManager indexManager = runtimeRoot.createIndexManager();
        assertSame(indexManager, config.indexManager(runtimeRoot));
        assertSame(indexManager, runtimeRoot.createIndexManager());
        org.airsonic.player.service.search.QueryFactory queryFactory = runtimeRoot.createQueryFactory();
        assertSame(queryFactory, config.queryFactory(runtimeRoot));
        assertSame(queryFactory, runtimeRoot.createQueryFactory());
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

        ConfigurationPropertiesService properties = runtimeRoot.createConfigurationPropertiesService();
        assertSame(properties, config.configurationPropertiesService(runtimeRoot));
        assertSame(properties, runtimeRoot.createConfigurationPropertiesService());
        assertNotNull(properties);

        SettingsService settingsService = runtimeRoot.createSettingsService();
        assertSame(settingsService, config.settingsService(runtimeRoot, userDao, avatarDao, cacheManager, properties));
        assertSame(settingsService, runtimeRoot.createSettingsService());
        assertEquals(5, settingsService.getIndexCreationInterval());
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
        CoverArtAccentColorBackfill coverArtBackfill = runtimeRoot.createCoverArtAccentColorBackfill(coverArtService);
        assertSame(coverArtBackfill, runtimeRoot.createCoverArtAccentColorBackfill(coverArtService));
        assertSame(coverArtBackfill, config.coverArtAccentColorBackfill(runtimeRoot, coverArtService));
        assertEquals(1, lifecycleRegistry.getStartupItems().stream()
                .filter(item -> item.name().equals("coverArtAccentColorBackfill")).count());
        org.airsonic.player.service.upnp.CustomContentDirectory contentDirectory =
                mock(org.airsonic.player.service.upnp.CustomContentDirectory.class);
        org.airsonic.player.service.UPnPService upnpService = runtimeRoot.createUpnpService(settingsService,
                contentDirectory);
        assertSame(upnpService, runtimeRoot.createUpnpService(settingsService, contentDirectory));
        assertSame(upnpService, config.upnpService(settingsService, contentDirectory, runtimeRoot));
        org.airsonic.player.i18n.LocaleResolver localeResolver = mock(org.airsonic.player.i18n.LocaleResolver.class);
        MediaFileService mediaFileService = runtimeRoot.createMediaFileService(securityService, settingsService,
                mediaFileDao, albumDao, parser, metaDataParserFactory, coverArtService, localeResolver);
        assertSame(mediaFileService, runtimeRoot.createMediaFileService(securityService, settingsService,
                mediaFileDao, albumDao, parser, metaDataParserFactory, coverArtService, localeResolver));
        org.airsonic.player.service.search.SearchServiceUtilities searchServiceUtilities =
                runtimeRoot.createSearchServiceUtilities(mediaFileService);
        assertSame(searchServiceUtilities, config.searchServiceUtilities(runtimeRoot, mediaFileService));
        assertSame(searchServiceUtilities, runtimeRoot.createSearchServiceUtilities(mediaFileService));
        org.airsonic.player.service.search.SearchServiceImpl searchService =
                runtimeRoot.createSearchService(mediaFileService);
        assertSame(searchService, config.searchServiceImpl(runtimeRoot, mediaFileService));
        assertSame(searchService, runtimeRoot.createSearchService(mediaFileService));
        TagEditService tagEditService = runtimeRoot.createTagEditService(metaDataParserFactory,
                mediaFileService, securityService);
        assertSame(tagEditService, runtimeRoot.createTagEditService(metaDataParserFactory,
                mediaFileService, securityService));
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
        org.airsonic.player.service.hls.HlsPlaylistService hlsPlaylistService =
                runtimeRoot.createHlsPlaylistService(jwtSecurityService);
        assertSame(hlsPlaylistService, runtimeRoot.createHlsPlaylistService(jwtSecurityService));
        CaptionService captionService = runtimeRoot.createCaptionService(mediaFileService, mediaFolderService,
                settingsService, metaDataParserFactory, jwtSecurityService);
        assertSame(captionService, runtimeRoot.createCaptionService(mediaFileService, mediaFolderService,
                settingsService, metaDataParserFactory, jwtSecurityService));
        VideoPlaybackService videoPlaybackService = runtimeRoot.createVideoPlaybackService(mediaFileService,
                captionService, jwtSecurityService, bookmarkService, settingsService);
        assertSame(videoPlaybackService, runtimeRoot.createVideoPlaybackService(mediaFileService,
                captionService, jwtSecurityService, bookmarkService, settingsService));

        TaskSchedulingService scheduler = runtimeRoot.createTaskSchedulingService();
        assertSame(scheduler, config.taskSchedulingService(runtimeRoot));
        assertSame(scheduler, runtimeRoot.createTaskSchedulingService());
        assertNotNull(scheduler);
        StatusService statusService = runtimeRoot.createStatusService(
                mediaFileService, mediaFolderService, settingsService, scheduler);
        assertSame(statusService, runtimeRoot.createStatusService(
                mediaFileService, mediaFolderService, settingsService, scheduler));
        TranscodingService transcodingService = runtimeRoot.createTranscodingService(
                daoGraph.getTranscodingDao(), settingsService, mediaFolderService);
        assertSame(transcodingService, runtimeRoot.createTranscodingService(
                daoGraph.getTranscodingDao(), settingsService, mediaFolderService));
        org.airsonic.player.service.hls.HlsSegmentService hlsSegmentService =
                runtimeRoot.createHlsSegmentService(mediaFileService, securityService, statusService,
                        settingsService, transcodingService);
        assertSame(hlsSegmentService, runtimeRoot.createHlsSegmentService(mediaFileService, securityService,
                statusService, settingsService, transcodingService));
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
        PathWatcherService pathWatcher = runtimeRoot.createPathWatcherService();
        assertSame(pathWatcher, config.pathWatcherService(runtimeRoot));
        assertSame(pathWatcher, runtimeRoot.createPathWatcherService());
        assertNotNull(pathWatcher);
        List<org.airsonic.player.service.playlist.PlaylistExportHandler> exportHandlers = List.of(
                runtimeRoot.createDefaultPlaylistExportHandler(mediaFileDao),
                runtimeRoot.createXspfPlaylistExportHandler(mediaFileDao, playlistDao, coverArtService));
        List<org.airsonic.player.service.playlist.PlaylistImportHandler> importHandlers = List.of(
                runtimeRoot.createDefaultPlaylistImportHandler(mediaFileService, settingsService),
                runtimeRoot.createXspfPlaylistImportHandler(mediaFileService, settingsService));
        PlaylistService playlistService = runtimeRoot.createPlaylistService(
                securityService, settingsService, exportHandlers, importHandlers);
        assertSame(playlistService, runtimeRoot.createPlaylistService(
                securityService, settingsService, exportHandlers, importHandlers));
        CoverArtImageService coverArtImageService = runtimeRoot.createCoverArtImageService(mediaFileService,
                coverArtService, transcodingService, settingsService, playlistService, artistDao, albumDao);
        assertSame(coverArtImageService, runtimeRoot.createCoverArtImageService(mediaFileService,
                coverArtService, transcodingService, settingsService, playlistService, artistDao, albumDao));
        DownloadService downloadService = runtimeRoot.createDownloadService(statusService, securityService,
                playlistService, settingsService, mediaFileService, coverArtService);
        assertSame(downloadService, runtimeRoot.createDownloadService(statusService, securityService,
                playlistService, settingsService, mediaFileService, coverArtService));
        UserAdministrationService userAdministrationService =
                runtimeRoot.createUserAdministrationService(securityService, settingsService);
        assertSame(userAdministrationService,
                runtimeRoot.createUserAdministrationService(securityService, settingsService));
        settingsService.setIndexCreationInterval(-1);
        MediaScannerService mediaScannerService = runtimeRoot.createMediaScannerService(
                playlistService, settingsService, indexManager, mediaFileService, mediaFolderService, coverArtService);
        assertSame(mediaScannerService, config.mediaScannerService(runtimeRoot, playlistService, settingsService,
                indexManager, mediaFileService, mediaFolderService, coverArtService));
        assertSame(mediaScannerService, runtimeRoot.createMediaScannerService(
                playlistService, settingsService, indexManager, mediaFileService, mediaFolderService, coverArtService));
        GeneralSettingsAdministrationService generalSettingsAdministrationService =
                runtimeRoot.createGeneralSettingsAdministrationService(settingsService, playlistService);
        assertSame(generalSettingsAdministrationService, runtimeRoot.createGeneralSettingsAdministrationService(
                settingsService, playlistService));
        MusicFolderAdministrationService musicFolderAdministrationService =
                runtimeRoot.createMusicFolderAdministrationService(settingsService, mediaScannerService,
                        mediaFolderService, artistDao, albumDao, mediaFileDao, indexManager, playlistService,
                        coverArtService);
        assertSame(musicFolderAdministrationService, runtimeRoot.createMusicFolderAdministrationService(
                settingsService, mediaScannerService, mediaFolderService, artistDao, albumDao, mediaFileDao,
                indexManager, playlistService, coverArtService));
        MusicIndexStateService musicIndexStateService = runtimeRoot.createMusicIndexStateService(
                mediaScannerService, settingsService);
        assertSame(musicIndexStateService,
                runtimeRoot.createMusicIndexStateService(mediaScannerService, settingsService));
        PlayQueueService playQueueService = runtimeRoot.createPlayQueueService(
                searchService, jukeboxService, settingsService, mediaFileService,
                lastFmService, ratingService, playlistService, mediaFileDao, mediaFolderService,
                playQueueDao, jwtSecurityService);
        assertSame(playQueueService, runtimeRoot.createPlayQueueService(
                searchService, jukeboxService, settingsService, mediaFileService,
                lastFmService, ratingService, playlistService, mediaFileDao, mediaFolderService,
                playQueueDao, jwtSecurityService));
        org.airsonic.player.dao.PlayerDaoPlayQueueFactory playQueueFactory =
                runtimeRoot.createPlayerDaoPlayQueueFactory();
        assertSame(playQueueFactory, runtimeRoot.createPlayerDaoPlayQueueFactory());
        MediaStreamService mediaStreamService = runtimeRoot.createMediaStreamService(statusService, securityService,
                settingsService, transcodingService, audioScrobblerService, mediaFileService, searchService);
        assertSame(mediaStreamService, runtimeRoot.createMediaStreamService(statusService, securityService,
                settingsService, transcodingService, audioScrobblerService, mediaFileService, searchService));

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
