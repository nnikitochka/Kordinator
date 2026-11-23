package org.airsonic.player.runtime;

import org.airsonic.player.dao.*;
import org.airsonic.player.service.*;
import org.airsonic.player.monitor.MetricsManager;
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
        org.airsonic.player.controller.JAXBWriter jaxbWriter = runtimeRoot.createSubsonicJaxbWriter();
        assertSame(jaxbWriter, runtimeRoot.createSubsonicJaxbWriter());

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

        org.airsonic.player.service.search.AnalyzerFactory analyzerFactory = runtimeRoot.createAnalyzerFactory();
        assertSame(analyzerFactory, runtimeRoot.createAnalyzerFactory());
        org.airsonic.player.service.search.DocumentFactory documentFactory = runtimeRoot.createDocumentFactory();
        assertSame(documentFactory, runtimeRoot.createDocumentFactory());
        org.airsonic.player.service.search.IndexManager indexManager = runtimeRoot.createIndexManager();
        assertSame(indexManager, runtimeRoot.createIndexManager());
        org.airsonic.player.service.search.QueryFactory queryFactory = runtimeRoot.createQueryFactory();
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
        assertSame(properties, runtimeRoot.createConfigurationPropertiesService());
        assertNotNull(properties);

        SettingsService settingsService = runtimeRoot.createSettingsService();
        assertSame(settingsService, runtimeRoot.createSettingsService());
        assertEquals(5, settingsService.getIndexCreationInterval());
        assertNotNull(settingsService);
        VersionService versionService = runtimeRoot.createVersionService();
        assertSame(versionService, runtimeRoot.createVersionService());
        ServerInfoService serverInfoService = runtimeRoot.createServerInfoService(versionService, settingsService);
        assertSame(serverInfoService, runtimeRoot.createServerInfoService(versionService, settingsService));
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // MediaFolderService and PlayerDao
        assertNotNull(mediaFolderService);

        PlayerDao playerDao = runtimeRoot.createPlayerDao();
        assertSame(playerDao, runtimeRoot.createPlayerDao());
        assertNotNull(playerDao);

        // Security & scheduling services
        org.airsonic.player.security.CredentialEncoderRegistry credentialEncoders =
                mock(org.airsonic.player.security.CredentialEncoderRegistry.class);
        SecurityService securityService = runtimeRoot.createSecurityService(userDao, settingsService, mediaFolderService,
                credentialEncoders);
        assertSame(securityService, runtimeRoot.createSecurityService(userDao, settingsService, mediaFolderService,
                credentialEncoders));
        assertNotNull(securityService);
        org.airsonic.player.security.SubsonicCredentialAuthenticationService credentialAuthenticationService =
                runtimeRoot.createSubsonicCredentialAuthenticationService(securityService, credentialEncoders);
        assertSame(credentialAuthenticationService,
                runtimeRoot.createSubsonicCredentialAuthenticationService(securityService, credentialEncoders));
        assertNotNull(runtimeRoot.createDefaultMetaDataParser());
        assertSame(runtimeRoot.createDefaultMetaDataParser(), runtimeRoot.createDefaultMetaDataParser());
        assertNotNull(runtimeRoot.createFFmpegParser());
        assertSame(runtimeRoot.createFFmpegParser(), runtimeRoot.createFFmpegParser());
        org.airsonic.player.service.metadata.JaudiotaggerParser parser = runtimeRoot.createJaudiotaggerParser();
        assertSame(parser, runtimeRoot.createJaudiotaggerParser());
        org.airsonic.player.service.metadata.MetaDataParserFactory metaDataParserFactory =
                runtimeRoot.createMetaDataParserFactory();
        assertSame(metaDataParserFactory, runtimeRoot.createMetaDataParserFactory());
        CoverArtService coverArtService = runtimeRoot.createCoverArtService(daoGraph.getCoverArtDao());
        assertSame(coverArtService, runtimeRoot.createCoverArtService(daoGraph.getCoverArtDao()));
        CoverArtAccentColorBackfill coverArtBackfill = runtimeRoot.createCoverArtAccentColorBackfill(coverArtService);
        assertSame(coverArtBackfill, runtimeRoot.createCoverArtAccentColorBackfill(coverArtService));
        assertEquals(1, lifecycleRegistry.getStartupItems().stream()
                .filter(item -> item.name().equals("coverArtAccentColorBackfill")).count());
        MediaFileService mediaFileService = runtimeRoot.createMediaFileService(securityService, settingsService,
                mediaFileDao, albumDao, parser, metaDataParserFactory, coverArtService);
        assertSame(mediaFileService, runtimeRoot.createMediaFileService(securityService, settingsService,
                mediaFileDao, albumDao, parser, metaDataParserFactory, coverArtService));
        org.airsonic.player.service.search.SearchServiceUtilities searchServiceUtilities =
                runtimeRoot.createSearchServiceUtilities(mediaFileService);
        assertSame(searchServiceUtilities, runtimeRoot.createSearchServiceUtilities(mediaFileService));
        org.airsonic.player.service.search.SearchServiceImpl searchService =
                runtimeRoot.createSearchService(mediaFileService);
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
        RatingService ratingService = runtimeRoot.createRatingService(daoGraph.getRatingDao(), securityService,
                mediaFileService);
        assertSame(ratingService, runtimeRoot.createRatingService(daoGraph.getRatingDao(), securityService,
                mediaFileService));
        MusicIndexService musicIndexService = runtimeRoot.createMusicIndexService(settingsService, mediaFileService);
        assertSame(musicIndexService, runtimeRoot.createMusicIndexService(settingsService, mediaFileService));
        JWTSecurityService jwtSecurityService = runtimeRoot.createJwtSecurityService(settingsService);
        assertSame(jwtSecurityService, runtimeRoot.createJwtSecurityService(settingsService));
        assertNotNull(jwtSecurityService);
        LastFmService lastFmService = runtimeRoot.createLastFmService(mediaFileDao, mediaFileService, artistDao);
        assertSame(lastFmService, runtimeRoot.createLastFmService(mediaFileDao, mediaFileService, artistDao));
        ShareService shareService = runtimeRoot.createShareService(daoGraph.getShareDao(), mediaFileService);
        assertSame(shareService, runtimeRoot.createShareService(daoGraph.getShareDao(), mediaFileService));
        CaptionService captionService = runtimeRoot.createCaptionService(mediaFileService, mediaFolderService,
                settingsService, metaDataParserFactory, jwtSecurityService);
        assertSame(captionService, runtimeRoot.createCaptionService(mediaFileService, mediaFolderService,
                settingsService, metaDataParserFactory, jwtSecurityService));
        VideoPlaybackService videoPlaybackService = runtimeRoot.createVideoPlaybackService(mediaFileService,
                captionService, jwtSecurityService, bookmarkService, settingsService);
        assertSame(videoPlaybackService, runtimeRoot.createVideoPlaybackService(mediaFileService,
                captionService, jwtSecurityService, bookmarkService, settingsService));

        TaskSchedulingService scheduler = runtimeRoot.createTaskSchedulingService();
        assertSame(scheduler, runtimeRoot.createTaskSchedulingService());
        assertNotNull(scheduler);
        MetricsManager metricsManager = runtimeRoot.createMetricsManager();
        assertSame(metricsManager, runtimeRoot.createMetricsManager());
        assertTrue(lifecycleRegistry.getShutdownItems().stream().anyMatch(item -> item.name().equals("metricsManager")));
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
        org.airsonic.player.controller.rest.RestPlayerResolver playerResolver =
                runtimeRoot.createRestPlayerResolver(playerService);
        assertSame(playerResolver, runtimeRoot.createRestPlayerResolver(playerService));
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
        assertSame(pathWatcher, runtimeRoot.createPathWatcherService());
        assertNotNull(pathWatcher);
        PlaylistService playlistService = runtimeRoot.createPlaylistService(
                securityService, settingsService, mediaFileService, coverArtService);
        assertSame(playlistService, runtimeRoot.createPlaylistService(
                securityService, settingsService, mediaFileService, coverArtService));
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
        org.airsonic.player.controller.rest.SubsonicResponseMapper responseMapper =
                runtimeRoot.createSubsonicResponseMapper(mediaFileService, ratingService, coverArtService, transcodingService);
        assertSame(responseMapper, runtimeRoot.createSubsonicResponseMapper(
                mediaFileService, ratingService, coverArtService, transcodingService));
        org.airsonic.player.controller.rest.search.SearchService subsonicSearchService =
                runtimeRoot.createSubsonicSearchService(searchService, mediaFolderService, responseMapper);
        assertSame(subsonicSearchService,
                runtimeRoot.createSubsonicSearchService(searchService, mediaFolderService, responseMapper));
        assertSame(runtimeRoot.createSubsonicSearchController(subsonicSearchService),
                runtimeRoot.createSubsonicSearchController(subsonicSearchService));
        org.airsonic.player.controller.rest.albums.AlbumsController albumsController =
                runtimeRoot.createSubsonicAlbumsController(mediaFileService, mediaFolderService,
                        ratingService, searchService, responseMapper);
        assertSame(albumsController, runtimeRoot.createSubsonicAlbumsController(
                mediaFileService, mediaFolderService, ratingService, searchService, responseMapper));

        org.airsonic.player.service.upnp.DispatchingContentDirectory dispatchingContentDirectory =
                runtimeRoot.createDispatchingContentDirectory(mediaFileService, mediaFolderService, playlistService,
                        searchService, musicIndexService, indexManager, settingsService, playerService,
                        transcodingService, jwtSecurityService);
        assertSame(dispatchingContentDirectory, runtimeRoot.createDispatchingContentDirectory(mediaFileService,
                mediaFolderService, playlistService, searchService, musicIndexService, indexManager,
                settingsService, playerService, transcodingService, jwtSecurityService));
        org.airsonic.player.service.UPnPService upnpService = runtimeRoot.createUpnpService(settingsService,
                dispatchingContentDirectory);
        assertSame(upnpService, runtimeRoot.createUpnpService(settingsService, dispatchingContentDirectory));
        runtimeRoot.createAlbumUpnpProcessor(albumDao);
        runtimeRoot.createRecentAlbumUpnpProcessor(albumDao);
        runtimeRoot.createArtistUpnpProcessor(artistDao);
        runtimeRoot.createGenreUpnpProcessor();
        runtimeRoot.createMediaFileUpnpProcessor(mediaFileDao);
        runtimeRoot.createPlaylistUpnpProcessor(playlistService);
        runtimeRoot.createRootUpnpProcessor(indexManager);
        org.airsonic.player.service.upnp.FolderBasedContentDirectory folderDirectory =
                runtimeRoot.createFolderBasedContentDirectory(mediaFileService, mediaFolderService, playlistService,
                        indexManager, settingsService, playerService, transcodingService, jwtSecurityService);
        assertNotNull(folderDirectory);

        DatabaseService databaseService = runtimeRoot.createDatabaseService(settingsService, databaseDao, scheduler);
        assertSame(databaseService, runtimeRoot.createDatabaseService(settingsService, databaseDao, scheduler));
        assertSame(runtimeRoot.createDLNASettingsController(settingsService, upnpService),
                runtimeRoot.createDLNASettingsController(settingsService, upnpService));
        assertSame(runtimeRoot.createPersonalSettingsController(settingsService, securityService),
                runtimeRoot.createPersonalSettingsController(settingsService, securityService));
        assertSame(runtimeRoot.createPlayerSettingsController(playerService, transcodingService, playQueueService),
                runtimeRoot.createPlayerSettingsController(playerService, transcodingService, playQueueService));
        assertSame(runtimeRoot.createAdvancedSettingsController(settingsService),
                runtimeRoot.createAdvancedSettingsController(settingsService));
        assertSame(runtimeRoot.createTranscodingSettingsController(transcodingService, settingsService),
                runtimeRoot.createTranscodingSettingsController(transcodingService, settingsService));
        assertSame(runtimeRoot.createDatabaseSettingsController(settingsService, databaseService,
                        statusService, securityService, properties),
                runtimeRoot.createDatabaseSettingsController(settingsService, databaseService,
                        statusService, securityService, properties));
        assertSame(runtimeRoot.createCredentialsManagementController(securityService, settingsService),
                runtimeRoot.createCredentialsManagementController(securityService, settingsService));
        assertSame(runtimeRoot.createBookmarksController(bookmarkService, mediaFileService, responseMapper),
                runtimeRoot.createBookmarksController(bookmarkService, mediaFileService, responseMapper));
        assertSame(runtimeRoot.createStarredController(mediaFolderService, responseMapper),
                runtimeRoot.createStarredController(mediaFolderService, responseMapper));
        assertSame(runtimeRoot.createJukeboxController(playQueueService, jukeboxService, responseMapper),
                runtimeRoot.createJukeboxController(playQueueService, jukeboxService, responseMapper));
        assertSame(runtimeRoot.createUsersController(securityService, settingsService, mediaFolderService,
                        userAdministrationService),
                runtimeRoot.createUsersController(securityService, settingsService, mediaFolderService,
                        userAdministrationService));
        assertSame(runtimeRoot.createPlaylistExportController(playlistService),
                runtimeRoot.createPlaylistExportController(playlistService));
        assertSame(runtimeRoot.createPlaylistImportController(playlistService),
                runtimeRoot.createPlaylistImportController(playlistService));
        assertSame(runtimeRoot.createScrobbleController(mediaFileService, statusService, audioScrobblerService),
                runtimeRoot.createScrobbleController(mediaFileService, statusService, audioScrobblerService));
        assertSame(runtimeRoot.createDownloadRestController(), runtimeRoot.createDownloadRestController());
        assertSame(runtimeRoot.createTagEditController(tagEditService),
                runtimeRoot.createTagEditController(tagEditService));
        assertSame(runtimeRoot.createCoverArtRestController(coverArtImageService),
                runtimeRoot.createCoverArtRestController(coverArtImageService));
        assertSame(runtimeRoot.createAvatarRestController(settingsService),
                runtimeRoot.createAvatarRestController(settingsService));
        assertSame(runtimeRoot.createAvatarUploadController(settingsService),
                runtimeRoot.createAvatarUploadController(settingsService));
        assertSame(runtimeRoot.createPublicShareController(shareService, mediaFileService, mediaFolderService,
                        jwtSecurityService, metaDataParserFactory, objectMapper),
                runtimeRoot.createPublicShareController(shareService, mediaFileService, mediaFolderService,
                        jwtSecurityService, metaDataParserFactory, objectMapper));
        assertSame(runtimeRoot.createCaptionsRestController(captionService, mediaFolderService, objectMapper),
                runtimeRoot.createCaptionsRestController(captionService, mediaFolderService, objectMapper));
        assertSame(runtimeRoot.createSharesController(shareService, mediaFileService, mediaFolderService,
                        responseMapper, playerService, playlistService, securityService),
                runtimeRoot.createSharesController(shareService, mediaFileService, mediaFolderService,
                        responseMapper, playerService, playlistService, securityService));
        org.airsonic.player.ktor.KordinatorSessionService sessionService =
                runtimeRoot.createKordinatorSessionService(securityService, credentialEncoders, settingsService);
        assertSame(sessionService, runtimeRoot.createKordinatorSessionService(
                securityService, credentialEncoders, settingsService));
        assertSame(runtimeRoot.createKordinatorAuthRoutes(sessionService),
                runtimeRoot.createKordinatorAuthRoutes(sessionService));
        assertSame(runtimeRoot.createKordinatorPlaylistRoutes(playlistService, securityService, mediaFileService),
                runtimeRoot.createKordinatorPlaylistRoutes(playlistService, securityService, mediaFileService));
        assertSame(runtimeRoot.createKordinatorAlbumRoutes(mediaFileService, mediaFolderService, securityService),
                runtimeRoot.createKordinatorAlbumRoutes(mediaFileService, mediaFolderService, securityService));
        assertSame(runtimeRoot.createKordinatorArtistRoutes(mediaFolderService),
                runtimeRoot.createKordinatorArtistRoutes(mediaFolderService));
        assertSame(runtimeRoot.createKordinatorSearchRoutes(searchService, mediaFolderService, securityService),
                runtimeRoot.createKordinatorSearchRoutes(searchService, mediaFolderService, securityService));
        assertSame(runtimeRoot.createKordinatorFavoriteRoutes(mediaFileService, mediaFolderService, securityService),
                runtimeRoot.createKordinatorFavoriteRoutes(mediaFileService, mediaFolderService, securityService));
        assertSame(runtimeRoot.createKordinatorLibraryRoutes(mediaFileService, mediaFolderService, securityService),
                runtimeRoot.createKordinatorLibraryRoutes(mediaFileService, mediaFolderService, securityService));
        assertSame(runtimeRoot.createGeneralSettingsController(generalSettingsAdministrationService),
                runtimeRoot.createGeneralSettingsController(generalSettingsAdministrationService));
        assertSame(runtimeRoot.createMusicFolderSettingsController(musicFolderAdministrationService),
                runtimeRoot.createMusicFolderSettingsController(musicFolderAdministrationService));
        assertSame(runtimeRoot.createPasswordController(securityService),
                runtimeRoot.createPasswordController(securityService));
        assertSame(runtimeRoot.createPlayQueueController(mediaFileService, responseMapper),
                runtimeRoot.createPlayQueueController(mediaFileService, responseMapper));
        assertSame(runtimeRoot.createIndexesController(settingsService, mediaFolderService, musicIndexService,
                        musicIndexStateService, ratingService, responseMapper, playerService),
                runtimeRoot.createIndexesController(settingsService, mediaFolderService, musicIndexService,
                        musicIndexStateService, ratingService, responseMapper, playerService));
        assertSame(runtimeRoot.createMusicDirectoryController(mediaFileService, securityService,
                        ratingService, responseMapper),
                runtimeRoot.createMusicDirectoryController(mediaFileService, securityService,
                        ratingService, responseMapper));
        org.airsonic.player.health.HealthContributorRegistry healthContributors =
                runtimeRoot.createHealthContributorRegistry(dataSource);
        assertSame(healthContributors, runtimeRoot.createHealthContributorRegistry(dataSource));
        assertSame(runtimeRoot.createStatusAggregator(), runtimeRoot.createStatusAggregator());
        io.micrometer.core.instrument.MeterRegistry meterRegistry = runtimeRoot.createMeterRegistry();
        assertSame(meterRegistry, runtimeRoot.createMeterRegistry());

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

    @Test
    void rootCreatesAndOwnsOneDataSourceForThePersistenceGraph() {
        LifecycleRegistry lifecycle = new LifecycleRegistry();
        RuntimeConfiguration configuration = new RuntimeConfiguration(java.util.Map.of(
                "spring.datasource.url", "jdbc:hsqldb:mem:runtime_root_datasource;hsqldb.tx=mvcc;sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false",
                "spring.datasource.username", "sa",
                "spring.datasource.password", "",
                "spring.liquibase.parameters.mysqlVarcharLimit", "255"));
        RuntimeCompositionRoot root = new RuntimeCompositionRoot(configuration, lifecycle);

        PersistenceGraph graph = root.createPersistenceGraph();
        javax.sql.DataSource dataSource = root.createDataSource();
        assertSame(graph, root.createPersistenceGraph(dataSource));
        assertSame(dataSource, root.createDataSource());
        assertSame(dataSource, graph.getDataSource());
        assertEquals(1, lifecycle.getShutdownItems().stream()
                .filter(item -> item.name().equals("dataSource")).count());

        root.getLifecycleRegistry().shutdown();
        assertTrue(((com.zaxxer.hikari.HikariDataSource) dataSource).isClosed());
    }

    @Test
    void healthContributorsAreOwnedByCompositionRoot() {
        RuntimeCompositionRoot runtimeRoot = new RuntimeCompositionRoot();
        JDBCDataSource dataSource = new JDBCDataSource();

        org.airsonic.player.health.HealthContributorRegistry registry =
                runtimeRoot.createHealthContributorRegistry(dataSource);

        assertSame(registry, runtimeRoot.createHealthContributorRegistry(dataSource));
        assertNotNull(registry.getContributor("db"));
        assertNotNull(registry.getContributor("diskSpace"));
        assertNotNull(registry.getContributor("ping"));
    }
}
