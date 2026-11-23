package org.airsonic.player.runtime;


import org.airsonic.player.dao.*;
import org.airsonic.player.health.HealthContributorRegistry;
import org.airsonic.player.health.StatusAggregator;
import org.airsonic.player.monitor.MetricsManager;
import org.airsonic.player.security.CredentialEncoderRegistry;
import org.airsonic.player.security.SubsonicCredentialAuthenticationService;
import org.airsonic.player.service.*;
import org.airsonic.player.service.metadata.DefaultMetaDataParser;
import org.airsonic.player.service.metadata.FFmpegParser;
import org.airsonic.player.service.metadata.JaudiotaggerParser;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.airsonic.player.service.playlist.*;
import org.airsonic.player.service.search.*;
import org.airsonic.player.service.upnp.*;

import java.util.Objects;
import javax.sql.DataSource;

/** Minimal framework-neutral composition root. Runtime services are added here as they are migrated. */
public final class RuntimeCompositionRoot {

    private final RuntimeConfiguration configuration;
    private final LifecycleRegistry lifecycleRegistry;
    private DataSource dataSource;
    private boolean dataSourceShutdownRegistered;
    private boolean liquibaseMigrated;
    private PersistenceGraph persistenceGraph;
    private javax.cache.CacheManager cacheManager;
    private MediaFolderService mediaFolderService;
    private MediaFileService mediaFileService;
    private PlayerDao playerDao;
    private VersionService versionService;
    private ServerInfoService serverInfoService;
    private StatusService statusService;
    private LyricsService lyricsService;
    private BookmarkService bookmarkService;
    private RatingService ratingService;
    private MusicIndexService musicIndexService;
    private SecurityService securityService;
    private SubsonicCredentialAuthenticationService subsonicCredentialAuthenticationService;
    private JWTSecurityService jwtSecurityService;
    private DatabaseService databaseService;
    private JukeboxLegacySubsonicService jukeboxLegacySubsonicService;
    private JukeboxService jukeboxService;
    private LastFmService lastFmService;
    private ShareService shareService;
    private AudioScrobblerService audioScrobblerService;
    private CaptionService captionService;
    private TranscodingService transcodingService;
    private PlayerService playerService;
    private DefaultMetaDataParser defaultMetaDataParser;
    private FFmpegParser ffmpegParser;
    private JaudiotaggerParser jaudiotaggerParser;
    private MetaDataParserFactory metaDataParserFactory;
    private CoverArtService coverArtService;
    private TagEditService tagEditService;
    private DownloadService downloadService;
    private CoverArtImageService coverArtImageService;
    private PlayerDaoPlayQueueFactory playerDaoPlayQueueFactory;
    private PlaylistService playlistService;
    private TaskSchedulingService taskSchedulingService;
    private PathWatcherService pathWatcherService;
    private MediaScannerService mediaScannerService;
    private AnalyzerFactory analyzerFactory;
    private DocumentFactory documentFactory;
    private IndexManager indexManager;
    private QueryFactory queryFactory;
    private SearchServiceUtilities searchServiceUtilities;
    private SearchServiceImpl searchService;
    private ConfigurationPropertiesService configurationPropertiesService;
    private SettingsService settingsService;
    private GeneralSettingsAdministrationService generalSettingsAdministrationService;
    private MusicFolderAdministrationService musicFolderAdministrationService;
    private CoverArtAccentColorBackfill coverArtAccentColorBackfill;
    private UPnPService upnpService;
    private AlbumUpnpProcessor albumUpnpProcessor;
    private RecentAlbumUpnpProcessor recentAlbumUpnpProcessor;
    private ArtistUpnpProcessor artistUpnpProcessor;
    private GenreUpnpProcessor genreUpnpProcessor;
    private MediaFileUpnpProcessor mediaFileUpnpProcessor;
    private PlaylistUpnpProcessor playlistUpnpProcessor;
    private RootUpnpProcessor rootUpnpProcessor;
    private DispatchingContentDirectory dispatchingContentDirectory;
    private FolderBasedContentDirectory folderBasedContentDirectory;
    private DefaultPlaylistExportHandler defaultPlaylistExportHandler;
    private DefaultPlaylistImportHandler defaultPlaylistImportHandler;
    private XspfPlaylistExportHandler xspfPlaylistExportHandler;
    private XspfPlaylistImportHandler xspfPlaylistImportHandler;
    private HealthContributorRegistry healthContributorRegistry;
    private MetricsManager metricsManager;
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private StatusAggregator statusAggregator;
    private org.airsonic.player.ktor.KordinatorSessionService kordinatorSessionService;
    private org.airsonic.player.ktor.KordinatorLibraryRoutes kordinatorLibraryRoutes;

    public RuntimeCompositionRoot() {
        this(new RuntimeConfiguration(RuntimeConfiguration.DEFAULT_HEALTH_STATUS_ORDER), new LifecycleRegistry());
    }

    public RuntimeCompositionRoot(String healthStatusOrder) {
        this(new RuntimeConfiguration(healthStatusOrder), new LifecycleRegistry());
    }

    public RuntimeCompositionRoot(RuntimeConfiguration configuration) {
        this(configuration, new LifecycleRegistry());
    }

    public RuntimeCompositionRoot(RuntimeConfiguration configuration, LifecycleRegistry lifecycleRegistry) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.lifecycleRegistry = Objects.requireNonNull(lifecycleRegistry, "lifecycleRegistry");
    }

    public RuntimeConfiguration getConfiguration() {
        return configuration;
    }

    public LifecycleRegistry getLifecycleRegistry() {
        return lifecycleRegistry;
    }

    public synchronized StatusAggregator createStatusAggregator() {
        if (statusAggregator == null) {
            statusAggregator = RuntimeHealthFactory.createStatusAggregator(configuration);
        }
        return statusAggregator;
    }

    public synchronized io.micrometer.core.instrument.MeterRegistry createMeterRegistry() {
        if (meterRegistry == null) {
            meterRegistry = RuntimeHealthFactory.createMeterRegistry();
        }
        return meterRegistry;
    }

    public synchronized org.airsonic.player.ktor.KordinatorSessionService createKordinatorSessionService(
            SecurityService securityService,
            CredentialEncoderRegistry credentialEncoders,
            SettingsService settingsService) {
        if (kordinatorSessionService == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before KordinatorSessionService");
            }
            kordinatorSessionService = new org.airsonic.player.ktor.KordinatorSessionService(
                    securityService, credentialEncoders,
                    new org.airsonic.player.ktor.JdbcKordinatorSessionStore(persistenceGraph.getDataSource()),
                    settingsService.getNonDecodablePasswordEncoder());
        }
        return kordinatorSessionService;
    }

    public synchronized org.airsonic.player.ktor.KordinatorLibraryRoutes createKordinatorLibraryRoutes(
            MediaFileService mediaFileService,
            MediaFolderService mediaFolderService) {
        if (kordinatorLibraryRoutes == null) {
            kordinatorLibraryRoutes = new org.airsonic.player.ktor.KordinatorLibraryRoutes(
                    mediaFileService, mediaFolderService);
        }
        return kordinatorLibraryRoutes;
    }

    public synchronized DataSource createDataSource() {
        if (dataSource == null) {
            ownDataSource(RuntimePersistenceFactory.createDataSource(configuration));
        }
        return dataSource;
    }

    private void ownDataSource(DataSource candidate) {
        Objects.requireNonNull(candidate, "dataSource");
        if (dataSource != null && dataSource != candidate) {
            throw new IllegalStateException("Runtime composition root already owns another DataSource");
        }
        dataSource = candidate;
        if (candidate instanceof AutoCloseable closeable && !dataSourceShutdownRegistered) {
            lifecycleRegistry.registerShutdown("dataSource", closeable);
            dataSourceShutdownRegistered = true;
        }
    }

    public synchronized PersistenceGraph createPersistenceGraph() {
        migrateDatabase();
        return createPersistenceGraph(createDataSource());
    }

    public synchronized void migrateDatabase() {
        if (liquibaseMigrated) {
            return;
        }
        RuntimePersistenceFactory.migrateDatabase(configuration, createDataSource());
        liquibaseMigrated = true;
    }

    public synchronized MetricsManager createMetricsManager() {
        if (metricsManager == null) {
            metricsManager = RuntimeHealthFactory.createMetricsManager(configuration);
            lifecycleRegistry.registerShutdown("metricsManager", metricsManager);
        }
        return metricsManager;
    }

    public synchronized HealthContributorRegistry createHealthContributorRegistry(
            DataSource dataSource) {
        if (healthContributorRegistry == null) {
            healthContributorRegistry = RuntimeHealthFactory.createHealthContributorRegistry(dataSource);
        }
        return healthContributorRegistry;
    }

    public synchronized TaskSchedulingService createTaskSchedulingService() {
        if (taskSchedulingService == null) {
            taskSchedulingService = new TaskSchedulingService();
            lifecycleRegistry.registerShutdown("taskSchedulingService", taskSchedulingService);
        }
        return taskSchedulingService;
    }

    public synchronized PathWatcherService createPathWatcherService() throws Exception {
        if (pathWatcherService == null) {
            pathWatcherService = new PathWatcherService();
            lifecycleRegistry.registerShutdown("pathWatcherService", pathWatcherService);
        }
        return pathWatcherService;
    }

    public synchronized MediaScannerService createMediaScannerService(
            PlaylistService playlistService,
            SettingsService settingsService,
            IndexManager indexManager,
            MediaFileService mediaFileService,
            MediaFolderService mediaFolderService,
            CoverArtService coverArtService) {
        if (mediaScannerService == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before MediaScannerService");
            }
            int scannerParallelism = configuration.getIntProperty("MediaScannerParallelism",
                    Runtime.getRuntime().availableProcessors() + 1);
            DaoGraph daos = persistenceGraph.getDaos();
            mediaScannerService = RuntimeScannerFactory.createMediaScannerService(
                    playlistService, settingsService, indexManager, mediaFileService, mediaFolderService,
                    coverArtService, daos.getMediaFileDao(), daos.getArtistDao(), daos.getAlbumDao(),
                    createTaskSchedulingService(), scannerParallelism);
            mediaScannerService.init();
        }
        return mediaScannerService;
    }

    public synchronized AnalyzerFactory createAnalyzerFactory() {
        if (analyzerFactory == null) {
            analyzerFactory = RuntimeSearchServiceFactory.createAnalyzerFactory();
        }
        return analyzerFactory;
    }

    public synchronized DocumentFactory createDocumentFactory() {
        if (documentFactory == null) {
            documentFactory = RuntimeSearchServiceFactory.createDocumentFactory();
        }
        return documentFactory;
    }

    public synchronized IndexManager createIndexManager() {
        if (indexManager == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before IndexManager");
            }
            DaoGraph daos = persistenceGraph.getDaos();
            indexManager = RuntimeSearchServiceFactory.createIndexManager(
                    createAnalyzerFactory(), createDocumentFactory(), daos.getMediaFileDao(),
                    daos.getArtistDao(), daos.getAlbumDao());
        }
        return indexManager;
    }

    public synchronized QueryFactory createQueryFactory() {
        if (queryFactory == null) {
            queryFactory = RuntimeSearchServiceFactory.createQueryFactory(createAnalyzerFactory());
        }
        return queryFactory;
    }

    public synchronized SearchServiceUtilities createSearchServiceUtilities(
            MediaFileService mediaFileService) {
        if (searchServiceUtilities == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before SearchServiceUtilities");
            }
            DaoGraph daos = persistenceGraph.getDaos();
            searchServiceUtilities = RuntimeSearchServiceFactory.createSearchServiceUtilities(
                    daos.getArtistDao(), daos.getAlbumDao(), mediaFileService);
        }
        return searchServiceUtilities;
    }

    public synchronized SearchServiceImpl createSearchService(
            MediaFileService mediaFileService) {
        if (searchService == null) {
            searchService = RuntimeSearchServiceFactory.createSearchService(
                    createQueryFactory(), createIndexManager(), createSearchServiceUtilities(mediaFileService));
        }
        return searchService;
    }

    public synchronized ConfigurationPropertiesService createConfigurationPropertiesService() {
        if (configurationPropertiesService == null) {
            // The Spring environment bootstrap still exposes this same file-backed source during migration.
            configurationPropertiesService = ConfigurationPropertiesService.getInstance();
        }
        return configurationPropertiesService;
    }

    public synchronized SettingsService createSettingsService() {
        if (settingsService == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before SettingsService");
            }
            DaoGraph daos = persistenceGraph.getDaos();
            settingsService = RuntimeSettingsServiceFactory.createSettingsService(
                    daos.getUserDao(), daos.getAvatarDao(), configuration, createCacheManager(),
                    createConfigurationPropertiesService());
            settingsService.init();
        }
        return settingsService;
    }

    public synchronized GeneralSettingsAdministrationService
            createGeneralSettingsAdministrationService(SettingsService settingsService,
                    PlaylistService playlistService) {
        if (generalSettingsAdministrationService == null) {
            generalSettingsAdministrationService = RuntimeSettingsServiceFactory.createGeneralSettingsAdministrationService(
                    settingsService, playlistService);
        }
        return generalSettingsAdministrationService;
    }

    public synchronized MusicFolderAdministrationService
            createMusicFolderAdministrationService(SettingsService settingsService,
                    MediaScannerService mediaScannerService,
                    MediaFolderService mediaFolderService,
                    ArtistDao artistDao,
                    AlbumDao albumDao,
                    MediaFileDao mediaFileDao,
                    IndexManager indexManager,
                    PlaylistService playlistService,
                    CoverArtService coverArtService) {
        if (musicFolderAdministrationService == null) {
            musicFolderAdministrationService = RuntimeSettingsServiceFactory.createMusicFolderAdministrationService(
                    settingsService, mediaScannerService, mediaFolderService, artistDao, albumDao, mediaFileDao,
                    indexManager, playlistService, coverArtService);
        }
        return musicFolderAdministrationService;
    }

    public synchronized CoverArtAccentColorBackfill createCoverArtAccentColorBackfill(
            CoverArtService coverArtService) {
        if (coverArtAccentColorBackfill == null) {
            coverArtAccentColorBackfill = RuntimeSettingsServiceFactory.createCoverArtAccentColorBackfill(coverArtService);
            lifecycleRegistry.registerStartup("coverArtAccentColorBackfill",
                    coverArtAccentColorBackfill::backfillAccentColors);
        }
        return coverArtAccentColorBackfill;
    }

    public synchronized UPnPService createUpnpService(
            SettingsService settingsService,
            CustomContentDirectory contentDirectory) {
        if (upnpService == null) {
            upnpService = RuntimeUpnpFactory.createUpnpService(settingsService, contentDirectory);
            upnpService.init();
            lifecycleRegistry.registerShutdown("upnpService", upnpService);
        }
        return upnpService;
    }

    public synchronized AlbumUpnpProcessor createAlbumUpnpProcessor(
            AlbumDao albumDao) {
        if (albumUpnpProcessor == null) {
            albumUpnpProcessor = RuntimeUpnpFactory.createAlbumProcessor(albumDao);
        }
        return albumUpnpProcessor;
    }

    public synchronized RecentAlbumUpnpProcessor createRecentAlbumUpnpProcessor(
            AlbumDao albumDao) {
        if (recentAlbumUpnpProcessor == null) {
            recentAlbumUpnpProcessor = RuntimeUpnpFactory.createRecentAlbumProcessor(albumDao);
        }
        return recentAlbumUpnpProcessor;
    }

    public synchronized ArtistUpnpProcessor createArtistUpnpProcessor(
            ArtistDao artistDao) {
        if (artistUpnpProcessor == null) {
            artistUpnpProcessor = RuntimeUpnpFactory.createArtistProcessor(artistDao);
        }
        return artistUpnpProcessor;
    }

    public synchronized GenreUpnpProcessor createGenreUpnpProcessor() {
        if (genreUpnpProcessor == null) {
            genreUpnpProcessor = RuntimeUpnpFactory.createGenreProcessor();
        }
        return genreUpnpProcessor;
    }

    public synchronized MediaFileUpnpProcessor createMediaFileUpnpProcessor(
            MediaFileDao mediaFileDao) {
        if (mediaFileUpnpProcessor == null) {
            mediaFileUpnpProcessor = RuntimeUpnpFactory.createMediaFileProcessor(mediaFileDao);
        }
        return mediaFileUpnpProcessor;
    }

    public synchronized PlaylistUpnpProcessor createPlaylistUpnpProcessor(
            PlaylistService playlistService) {
        if (playlistUpnpProcessor == null) {
            playlistUpnpProcessor = RuntimeUpnpFactory.createPlaylistProcessor(playlistService);
        }
        return playlistUpnpProcessor;
    }

    public synchronized RootUpnpProcessor createRootUpnpProcessor(
            IndexManager indexManager) {
        if (rootUpnpProcessor == null) {
            rootUpnpProcessor = RuntimeUpnpFactory.createRootProcessor(indexManager);
        }
        return rootUpnpProcessor;
    }

    public synchronized DispatchingContentDirectory createDispatchingContentDirectory(
            MediaFileService mediaFileService,
            MediaFolderService mediaFolderService,
            PlaylistService playlistService,
            SearchService searchService,
            MusicIndexService musicIndexService,
            IndexManager indexManager,
            SettingsService settingsService,
            PlayerService playerService,
            TranscodingService transcodingService,
            JWTSecurityService jwtSecurityService) {
        if (dispatchingContentDirectory == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before UPnP processors");
            }
            dispatchingContentDirectory = RuntimeUpnpFactory.createDispatchingContentDirectory(
                    createMediaFileUpnpProcessor(persistenceGraph.getDaos().getMediaFileDao()),
                    createPlaylistUpnpProcessor(playlistService), createAlbumUpnpProcessor(persistenceGraph.getDaos().getAlbumDao()),
                    createRecentAlbumUpnpProcessor(persistenceGraph.getDaos().getAlbumDao()),
                    createArtistUpnpProcessor(persistenceGraph.getDaos().getArtistDao()), createGenreUpnpProcessor(),
                    createRootUpnpProcessor(indexManager), mediaFileService, mediaFolderService,
                    playlistService, searchService, musicIndexService);
            dispatchingContentDirectory.setSettingsService(settingsService);
            dispatchingContentDirectory.setPlayerService(playerService);
            dispatchingContentDirectory.setTranscodingService(transcodingService);
            dispatchingContentDirectory.setJwtSecurityService(jwtSecurityService);
            createMediaFileUpnpProcessor(persistenceGraph.getDaos().getMediaFileDao())
                    .setDispatchingContentDirectory(dispatchingContentDirectory);
            createPlaylistUpnpProcessor(playlistService).setDispatchingContentDirectory(dispatchingContentDirectory);
            createAlbumUpnpProcessor(persistenceGraph.getDaos().getAlbumDao())
                    .setDispatchingContentDirectory(dispatchingContentDirectory);
            createRecentAlbumUpnpProcessor(persistenceGraph.getDaos().getAlbumDao())
                    .setDispatchingContentDirectory(dispatchingContentDirectory);
            createArtistUpnpProcessor(persistenceGraph.getDaos().getArtistDao())
                    .setDispatchingContentDirectory(dispatchingContentDirectory);
            createGenreUpnpProcessor().setDispatchingContentDirectory(dispatchingContentDirectory);
            createRootUpnpProcessor(indexManager).setDispatchingContentDirectory(dispatchingContentDirectory);
        }
        return dispatchingContentDirectory;
    }

    public synchronized FolderBasedContentDirectory createFolderBasedContentDirectory(
            MediaFileService mediaFileService,
            MediaFolderService mediaFolderService,
            PlaylistService playlistService,
            IndexManager indexManager,
            SettingsService settingsService,
            PlayerService playerService,
            TranscodingService transcodingService,
            JWTSecurityService jwtSecurityService) {
        if (folderBasedContentDirectory == null) {
            folderBasedContentDirectory = RuntimeUpnpFactory.createFolderBasedContentDirectory(
                    mediaFileService, mediaFolderService, playlistService, indexManager);
            folderBasedContentDirectory.setSettingsService(settingsService);
            folderBasedContentDirectory.setPlayerService(playerService);
            folderBasedContentDirectory.setTranscodingService(transcodingService);
            folderBasedContentDirectory.setJwtSecurityService(jwtSecurityService);
        }
        return folderBasedContentDirectory;
    }


    public synchronized PersistenceGraph createPersistenceGraph(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        ownDataSource(dataSource);
        if (persistenceGraph != null) {
            if (persistenceGraph.getDataSource() != dataSource) {
                throw new IllegalStateException("Persistence graph already created for another DataSource");
            }
            return persistenceGraph;
        }

        persistenceGraph = RuntimePersistenceFactory.createPersistenceGraph(dataSource);
        return persistenceGraph;
    }

    public synchronized javax.cache.CacheManager createCacheManager() {
        if (cacheManager == null) {
            cacheManager = RuntimePersistenceFactory.createCacheManager();
            lifecycleRegistry.registerShutdown("cacheManager", cacheManager::close);
        }
        return cacheManager;
    }

    public synchronized MediaFolderService createMediaFolderService() {
        if (persistenceGraph == null) {
            throw new IllegalStateException("Persistence graph must be created before media services");
        }
        if (mediaFolderService == null) {
            mediaFolderService = RuntimeMediaServiceFactory.createMediaFolderService(
                    persistenceGraph.getDaos().getMusicFolderDao(),
                    persistenceGraph.getDaos().getMediaFileDao(), createCacheManager());
        }
        return mediaFolderService;
    }

    public synchronized MediaFileService createMediaFileService(
            SecurityService securityService,
            SettingsService settingsService,
            MediaFileDao mediaFileDao,
            AlbumDao albumDao,
            JaudiotaggerParser parser,
            MetaDataParserFactory metaDataParserFactory,
            CoverArtService coverArtService) {
        if (mediaFileService == null) {
            mediaFileService = RuntimeMediaServiceFactory.createMediaFileService(
                    securityService, settingsService, mediaFileDao, albumDao, createMediaFolderService(),
                    parser, metaDataParserFactory, coverArtService, createCacheManager());
        }
        return mediaFileService;
    }

    public synchronized DefaultMetaDataParser createDefaultMetaDataParser() {
        if (defaultMetaDataParser == null) {
            defaultMetaDataParser = RuntimeMediaServiceFactory.createDefaultMetaDataParser(createMediaFolderService());
        }
        return defaultMetaDataParser;
    }

    public synchronized FFmpegParser createFFmpegParser() {
        if (ffmpegParser == null) {
            ffmpegParser = RuntimeMediaServiceFactory.createFFmpegParser(createMediaFolderService());
        }
        return ffmpegParser;
    }

    public synchronized JaudiotaggerParser createJaudiotaggerParser() {
        if (jaudiotaggerParser == null) {
            jaudiotaggerParser = RuntimeMediaServiceFactory.createJaudiotaggerParser(createMediaFolderService());
        }
        return jaudiotaggerParser;
    }

    public synchronized MetaDataParserFactory createMetaDataParserFactory() {
        if (metaDataParserFactory == null) {
            metaDataParserFactory = RuntimeMediaServiceFactory.createMetaDataParserFactory(
                    createJaudiotaggerParser(), createFFmpegParser(), createDefaultMetaDataParser());
        }
        return metaDataParserFactory;
    }

    public synchronized CoverArtService createCoverArtService(
            CoverArtDao coverArtDao) {
        if (coverArtService == null) {
            coverArtService = RuntimeMediaServiceFactory.createCoverArtService(
                    coverArtDao, createMediaFolderService(), createJaudiotaggerParser(), createCacheManager());
        }
        return coverArtService;
    }

    public synchronized TagEditService createTagEditService(
            MetaDataParserFactory metaDataParserFactory,
            MediaFileService mediaFileService,
            SecurityService securityService) {
        if (tagEditService == null) {
            tagEditService = RuntimeMediaServiceFactory.createTagEditService(
                    metaDataParserFactory, mediaFileService, createMediaFolderService(), securityService);
        }
        return tagEditService;
    }

    public synchronized DownloadService createDownloadService(
            StatusService statusService,
            SecurityService securityService,
            PlaylistService playlistService,
            SettingsService settingsService,
            MediaFileService mediaFileService,
            CoverArtService coverArtService) {
        if (downloadService == null) {
            downloadService = RuntimeMediaServiceFactory.createDownloadService(statusService, securityService,
                    playlistService, settingsService, mediaFileService, createMediaFolderService(), coverArtService);
        }
        return downloadService;
    }

    public synchronized CoverArtImageService createCoverArtImageService(
            MediaFileService mediaFileService,
            CoverArtService coverArtService,
            TranscodingService transcodingService,
            SettingsService settingsService,
            PlaylistService playlistService,
            ArtistDao artistDao,
            AlbumDao albumDao) {
        if (coverArtImageService == null) {
            coverArtImageService = RuntimeMediaServiceFactory.createCoverArtImageService(
                    mediaFileService, coverArtService, transcodingService, settingsService, playlistService,
                    artistDao, albumDao, createJaudiotaggerParser());
            coverArtImageService.init();
        }
        return coverArtImageService;
    }

    public synchronized DefaultPlaylistExportHandler createDefaultPlaylistExportHandler(
            MediaFileDao mediaFileDao) {
        if (defaultPlaylistExportHandler == null) {
            defaultPlaylistExportHandler = RuntimePlaylistServiceFactory.createDefaultExportHandler(
                    mediaFileDao, createMediaFolderService());
        }
        return defaultPlaylistExportHandler;
    }

    public synchronized DefaultPlaylistImportHandler createDefaultPlaylistImportHandler(
            MediaFileService mediaFileService,
            SettingsService settingsService) {
        if (defaultPlaylistImportHandler == null) {
            defaultPlaylistImportHandler = RuntimePlaylistServiceFactory.createDefaultImportHandler(
                    mediaFileService, settingsService);
        }
        return defaultPlaylistImportHandler;
    }

    public synchronized XspfPlaylistExportHandler createXspfPlaylistExportHandler(
            MediaFileDao mediaFileDao,
            PlaylistDao playlistDao,
            CoverArtService coverArtService) {
        if (xspfPlaylistExportHandler == null) {
            xspfPlaylistExportHandler = RuntimePlaylistServiceFactory.createXspfExportHandler(
                    mediaFileDao, playlistDao, createMediaFolderService(), coverArtService);
        }
        return xspfPlaylistExportHandler;
    }

    public synchronized XspfPlaylistImportHandler createXspfPlaylistImportHandler(
            MediaFileService mediaFileService,
            SettingsService settingsService) {
        if (xspfPlaylistImportHandler == null) {
            xspfPlaylistImportHandler = RuntimePlaylistServiceFactory.createXspfImportHandler(
                    mediaFileService, settingsService);
        }
        return xspfPlaylistImportHandler;
    }

    public synchronized PlaylistService createPlaylistService(
            SecurityService securityService,
            SettingsService settingsService,
            MediaFileService mediaFileService,
            CoverArtService coverArtService) throws Exception {
        if (playlistService == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before PlaylistService");
            }
            DaoGraph daos = persistenceGraph.getDaos();
            java.util.List<PlaylistExportHandler> exportHandlers = java.util.List.of(
                    createDefaultPlaylistExportHandler(daos.getMediaFileDao()),
                    createXspfPlaylistExportHandler(daos.getMediaFileDao(), daos.getPlaylistDao(), coverArtService));
            java.util.List<PlaylistImportHandler> importHandlers = java.util.List.of(
                    createDefaultPlaylistImportHandler(mediaFileService, settingsService),
                    createXspfPlaylistImportHandler(mediaFileService, settingsService));
            playlistService = RuntimePlaylistServiceFactory.createPlaylistService(
                    daos.getMediaFileDao(), daos.getPlaylistDao(),
                    securityService, settingsService, exportHandlers, importHandlers,
                    createPathWatcherService(), createCacheManager());
            playlistService.init();
        }
        return playlistService;
    }

    public synchronized PlayerDao createPlayerDao() throws Exception {
        if (persistenceGraph == null) {
            throw new IllegalStateException("Persistence graph must be created before PlayerDao");
        }
        if (playerDao == null) {
            PlayerDaoPlayQueueFactory queueFactory = createPlayerDaoPlayQueueFactory();
            playerDao = new PlayerDao(queueFactory, persistenceGraph.getJdbcHelper());
            playerDao.register();
        }
        return playerDao;
    }

    public synchronized PlayerDaoPlayQueueFactory createPlayerDaoPlayQueueFactory() {
        if (playerDaoPlayQueueFactory == null) {
            playerDaoPlayQueueFactory = new PlayerDaoPlayQueueFactory(createMediaFolderService());
        }
        return playerDaoPlayQueueFactory;
    }

    public synchronized VersionService createVersionService() throws java.io.IOException {
        if (versionService == null) {
            versionService = new VersionService();
        }
        return versionService;
    }

    public synchronized ServerInfoService createServerInfoService(
            VersionService versions,
            SettingsService settingsService) {
        if (serverInfoService == null) {
            serverInfoService = RuntimeApplicationServiceFactory.createServerInfoService(versions, settingsService);
        }
        return serverInfoService;
    }

    public synchronized StatusService createStatusService(
            MediaFileService mediaFileService,
            MediaFolderService mediaFolderService,
            SettingsService settingsService,
            TaskSchedulingService taskSchedulingService) {
        if (statusService == null) {
            statusService = RuntimeApplicationServiceFactory.createStatusService(
                    mediaFileService, mediaFolderService, settingsService, taskSchedulingService);
            statusService.cleanup();
        }
        return statusService;
    }

    public synchronized LyricsService createLyricsService() {
        if (lyricsService == null) {
            lyricsService = RuntimeApplicationServiceFactory.createLyricsService();
        }
        return lyricsService;
    }

    public synchronized BookmarkService createBookmarkService(
            BookmarkDao bookmarkDao,
            MediaFileService mediaFileService) {
        if (bookmarkService == null) {
            bookmarkService = RuntimeApplicationServiceFactory.createBookmarkService(bookmarkDao, mediaFileService);
        }
        return bookmarkService;
    }

    public synchronized RatingService createRatingService(
            RatingDao ratingDao,
            SecurityService securityService,
            MediaFileService mediaFileService) {
        if (ratingService == null) {
            ratingService = RuntimeApplicationServiceFactory.createRatingService(ratingDao, securityService, mediaFileService);
        }
        return ratingService;
    }

    public synchronized MusicIndexService createMusicIndexService(
            SettingsService settingsService,
            MediaFileService mediaFileService) {
        if (musicIndexService == null) {
            musicIndexService = RuntimeApplicationServiceFactory.createMusicIndexService(settingsService, mediaFileService);
        }
        return musicIndexService;
    }

    public synchronized SecurityService createSecurityService(
            UserDao userDao,
            SettingsService settingsService,
            MediaFolderService mediaFolderService,
            CredentialEncoderRegistry credentialEncoders) {
        if (securityService == null) {
            securityService = RuntimeSecurityServiceFactory.createSecurityService(
                    userDao, settingsService, mediaFolderService, createCacheManager(), credentialEncoders);
        }
        return securityService;
    }

    public synchronized SubsonicCredentialAuthenticationService
            createSubsonicCredentialAuthenticationService(
                    SecurityService securityService,
                    CredentialEncoderRegistry credentialEncoders) {
        if (subsonicCredentialAuthenticationService == null) {
            subsonicCredentialAuthenticationService =
                    RuntimeSecurityServiceFactory.createCredentialAuthenticationService(
                            securityService, credentialEncoders);
        }
        return subsonicCredentialAuthenticationService;
    }

    public synchronized JWTSecurityService createJwtSecurityService(
            SettingsService settingsService) {
        if (jwtSecurityService == null) {
            jwtSecurityService = RuntimeSecurityServiceFactory.createJwtSecurityService(settingsService);
        }
        return jwtSecurityService;
    }

    public synchronized DatabaseService createDatabaseService(
            SettingsService settingsService,
            DatabaseDao databaseDao,
            TaskSchedulingService taskSchedulingService) {
        if (databaseService == null) {
            databaseService = RuntimeApplicationServiceFactory.createDatabaseService(
                    settingsService, databaseDao, taskSchedulingService);
            databaseService.init();
        }
        return databaseService;
    }

    public synchronized JukeboxLegacySubsonicService createJukeboxLegacySubsonicService(
            TranscodingService transcodingService,
            AudioScrobblerService audioScrobblerService,
            StatusService statusService,
            SettingsService settingsService,
            SecurityService securityService,
            MediaFileService mediaFileService) {
        if (jukeboxLegacySubsonicService == null) {
            jukeboxLegacySubsonicService = RuntimePlaybackServiceFactory.createLegacyJukeboxService(
                    transcodingService, audioScrobblerService, statusService, settingsService,
                    securityService, mediaFileService);
        }
        return jukeboxLegacySubsonicService;
    }

    public synchronized JukeboxService createJukeboxService(
            JukeboxLegacySubsonicService legacyJukebox) {
        if (jukeboxService == null) {
            jukeboxService = RuntimePlaybackServiceFactory.createJukeboxService(legacyJukebox);
        }
        return jukeboxService;
    }

    public synchronized LastFmService createLastFmService(
            MediaFileDao mediaFileDao,
            MediaFileService mediaFileService,
            ArtistDao artistDao) {
        if (lastFmService == null) {
            lastFmService = RuntimeApplicationServiceFactory.createLastFmService(mediaFileDao, mediaFileService, artistDao);
            lastFmService.init();
        }
        return lastFmService;
    }

    public synchronized ShareService createShareService(
            ShareDao shareDao,
            MediaFileService mediaFileService) {
        if (shareService == null) {
            shareService = RuntimeApplicationServiceFactory.createShareService(shareDao, mediaFileService);
        }
        return shareService;
    }

    public synchronized AudioScrobblerService createAudioScrobblerService(
            SettingsService settingsService,
            SecurityService securityService) {
        if (audioScrobblerService == null) {
            audioScrobblerService = RuntimeApplicationServiceFactory.createAudioScrobblerService(
                    settingsService, securityService);
        }
        return audioScrobblerService;
    }

    public synchronized CaptionService createCaptionService(
            MediaFileService mediaFileService,
            MediaFolderService mediaFolderService,
            SettingsService settingsService,
            MetaDataParserFactory metaDataParserFactory) {
        if (captionService == null) {
            captionService = RuntimeApplicationServiceFactory.createCaptionService(
                    mediaFileService, mediaFolderService, settingsService, metaDataParserFactory);
        }
        return captionService;
    }

    public synchronized TranscodingService createTranscodingService(
            TranscodingDao transcodingDao,
            SettingsService settingsService,
            MediaFolderService mediaFolderService) {
        return createTranscodingService(transcodingDao, settingsService, mediaFolderService, this::requirePlayerService);
    }

    public synchronized TranscodingService createTranscodingService(
            TranscodingDao transcodingDao,
            SettingsService settingsService,
            MediaFolderService mediaFolderService,
            java.util.function.Supplier<PlayerService> playerServiceSupplier) {
        if (transcodingService == null) {
            transcodingService = RuntimePlaybackServiceFactory.createTranscodingService(
                    transcodingDao, settingsService, mediaFolderService, playerServiceSupplier);
        }
        return transcodingService;
    }

    private synchronized PlayerService requirePlayerService() {
        if (playerService == null) {
            throw new IllegalStateException("PlayerService must be created before player transcodings are used");
        }
        return playerService;
    }

    public synchronized PlayerService createPlayerService(
            PlayerDao playerDao,
            StatusService statusService,
            SecurityService securityService,
            TranscodingService transcodingService) {
        if (playerService == null) {
            playerService = RuntimePlaybackServiceFactory.createPlayerService(
                    playerDao, statusService, securityService, transcodingService);
            playerService.init();
        }
        return playerService;
    }
}
