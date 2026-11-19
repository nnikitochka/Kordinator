package org.airsonic.player.runtime;

import java.util.Objects;
import javax.sql.DataSource;

/** Minimal framework-neutral composition root. Runtime services are added here as they are migrated. */
public final class RuntimeCompositionRoot {

    private final RuntimeConfiguration configuration;
    private final LifecycleRegistry lifecycleRegistry;
    private PersistenceGraph persistenceGraph;
    private javax.cache.CacheManager cacheManager;
    private org.airsonic.player.service.MediaFolderService mediaFolderService;
    private org.airsonic.player.service.MediaFileService mediaFileService;
    private org.airsonic.player.dao.PlayerDao playerDao;
    private org.airsonic.player.service.VersionService versionService;
    private org.airsonic.player.service.ServerInfoService serverInfoService;
    private org.airsonic.player.service.StatusService statusService;
    private org.airsonic.player.service.PasswordRecoveryService passwordRecoveryService;
    private org.airsonic.player.service.LyricsService lyricsService;
    private org.airsonic.player.service.BookmarkService bookmarkService;
    private org.airsonic.player.service.RatingService ratingService;
    private org.airsonic.player.service.MusicIndexService musicIndexService;
    private org.airsonic.player.service.SecurityService securityService;
    private org.airsonic.player.security.SubsonicCredentialAuthenticationService subsonicCredentialAuthenticationService;
    private org.airsonic.player.service.JWTSecurityService jwtSecurityService;
    private org.airsonic.player.service.DatabaseService databaseService;
    private org.airsonic.player.service.JukeboxLegacySubsonicService jukeboxLegacySubsonicService;
    private org.airsonic.player.service.JukeboxService jukeboxService;
    private org.airsonic.player.service.PlayQueueService playQueueService;
    private org.airsonic.player.service.LastFmService lastFmService;
    private org.airsonic.player.service.ShareService shareService;
    private org.airsonic.player.service.UploadFileService uploadFileService;
    private org.airsonic.player.service.AudioScrobblerService audioScrobblerService;
    private org.airsonic.player.service.CaptionService captionService;
    private org.airsonic.player.service.VideoPlaybackService videoPlaybackService;
    private org.airsonic.player.service.TranscodingService transcodingService;
    private org.airsonic.player.service.PlayerService playerService;
    private org.airsonic.player.service.metadata.DefaultMetaDataParser defaultMetaDataParser;
    private org.airsonic.player.service.metadata.FFmpegParser ffmpegParser;
    private org.airsonic.player.service.metadata.JaudiotaggerParser jaudiotaggerParser;
    private org.airsonic.player.service.metadata.MetaDataParserFactory metaDataParserFactory;
    private org.airsonic.player.service.CoverArtService coverArtService;
    private org.airsonic.player.service.hls.HlsPlaylistService hlsPlaylistService;
    private org.airsonic.player.service.hls.HlsSegmentService hlsSegmentService;
    private org.airsonic.player.service.TagEditService tagEditService;
    private org.airsonic.player.service.DownloadService downloadService;
    private org.airsonic.player.service.UserAdministrationService userAdministrationService;
    private org.airsonic.player.service.MusicIndexStateService musicIndexStateService;
    private org.airsonic.player.service.CoverArtImageService coverArtImageService;
    private org.airsonic.player.dao.PlayerDaoPlayQueueFactory playerDaoPlayQueueFactory;
    private org.airsonic.player.service.MediaStreamService mediaStreamService;
    private org.airsonic.player.service.PlaylistService playlistService;
    private org.airsonic.player.service.TaskSchedulingService taskSchedulingService;
    private org.airsonic.player.service.PathWatcherService pathWatcherService;
    private org.airsonic.player.service.MediaScannerService mediaScannerService;
    private org.airsonic.player.service.search.AnalyzerFactory analyzerFactory;
    private org.airsonic.player.service.search.DocumentFactory documentFactory;
    private org.airsonic.player.service.search.IndexManager indexManager;
    private org.airsonic.player.service.search.QueryFactory queryFactory;
    private org.airsonic.player.service.search.SearchServiceUtilities searchServiceUtilities;
    private org.airsonic.player.service.search.SearchServiceImpl searchService;
    private org.airsonic.player.service.ConfigurationPropertiesService configurationPropertiesService;
    private org.airsonic.player.service.SettingsService settingsService;
    private org.airsonic.player.service.GeneralSettingsAdministrationService generalSettingsAdministrationService;
    private org.airsonic.player.service.MusicFolderAdministrationService musicFolderAdministrationService;
    private org.airsonic.player.service.CoverArtAccentColorBackfill coverArtAccentColorBackfill;
    private org.airsonic.player.service.UPnPService upnpService;
    private org.airsonic.player.i18n.LocaleService localeService;
    private org.airsonic.player.service.upnp.AlbumUpnpProcessor albumUpnpProcessor;
    private org.airsonic.player.service.upnp.RecentAlbumUpnpProcessor recentAlbumUpnpProcessor;
    private org.airsonic.player.service.upnp.ArtistUpnpProcessor artistUpnpProcessor;
    private org.airsonic.player.service.upnp.GenreUpnpProcessor genreUpnpProcessor;
    private org.airsonic.player.service.upnp.MediaFileUpnpProcessor mediaFileUpnpProcessor;
    private org.airsonic.player.service.upnp.PlaylistUpnpProcessor playlistUpnpProcessor;
    private org.airsonic.player.service.upnp.RootUpnpProcessor rootUpnpProcessor;
    private org.airsonic.player.service.upnp.DispatchingContentDirectory dispatchingContentDirectory;
    private org.airsonic.player.service.upnp.FolderBasedContentDirectory folderBasedContentDirectory;
    private org.airsonic.player.service.playlist.DefaultPlaylistExportHandler defaultPlaylistExportHandler;
    private org.airsonic.player.service.playlist.DefaultPlaylistImportHandler defaultPlaylistImportHandler;
    private org.airsonic.player.service.playlist.XspfPlaylistExportHandler xspfPlaylistExportHandler;
    private org.airsonic.player.service.playlist.XspfPlaylistImportHandler xspfPlaylistImportHandler;
    private org.airsonic.player.health.HealthContributorRegistry healthContributorRegistry;
    private org.airsonic.player.monitor.MetricsManager metricsManager;

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

    public synchronized org.airsonic.player.monitor.MetricsManager createMetricsManager() {
        if (metricsManager == null) {
            metricsManager = new org.airsonic.player.monitor.MetricsManager(configuration);
            lifecycleRegistry.registerShutdown("metricsManager", metricsManager);
        }
        return metricsManager;
    }

    public synchronized org.airsonic.player.health.HealthContributorRegistry createHealthContributorRegistry(
            DataSource dataSource) {
        if (healthContributorRegistry == null) {
            org.airsonic.player.health.DefaultHealthContributorRegistry registry =
                    new org.airsonic.player.health.DefaultHealthContributorRegistry();
            registry.registerContributor("db", new org.airsonic.player.health.DatabaseHealthIndicator(dataSource));
            registry.registerContributor("diskSpace", new org.airsonic.player.health.DiskSpaceHealthIndicator(
                    org.airsonic.player.service.SettingsService.getAirsonicHome()));
            registry.registerContributor("ping", new org.airsonic.player.health.PingHealthIndicator());
            healthContributorRegistry = registry;
        }
        return healthContributorRegistry;
    }

    public synchronized org.airsonic.player.service.TaskSchedulingService createTaskSchedulingService() {
        if (taskSchedulingService == null) {
            taskSchedulingService = new org.airsonic.player.service.TaskSchedulingService();
            lifecycleRegistry.registerShutdown("taskSchedulingService", taskSchedulingService);
        }
        return taskSchedulingService;
    }

    public synchronized org.airsonic.player.service.PathWatcherService createPathWatcherService() throws Exception {
        if (pathWatcherService == null) {
            pathWatcherService = new org.airsonic.player.service.PathWatcherService();
            lifecycleRegistry.registerShutdown("pathWatcherService", pathWatcherService);
        }
        return pathWatcherService;
    }

    public synchronized org.airsonic.player.service.MediaScannerService createMediaScannerService(
            org.airsonic.player.service.PlaylistService playlistService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.search.IndexManager indexManager,
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.service.CoverArtService coverArtService) {
        if (mediaScannerService == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before MediaScannerService");
            }
            int scannerParallelism = configuration.getIntProperty("MediaScannerParallelism",
                    Runtime.getRuntime().availableProcessors() + 1);
            DaoGraph daos = persistenceGraph.getDaos();
            mediaScannerService = new org.airsonic.player.service.MediaScannerService(
                    playlistService, settingsService, indexManager, mediaFileService, mediaFolderService,
                    coverArtService, daos.getMediaFileDao(), daos.getArtistDao(), daos.getAlbumDao(),
                    createTaskSchedulingService(), scannerParallelism);
            mediaScannerService.init();
        }
        return mediaScannerService;
    }

    public synchronized org.airsonic.player.service.search.AnalyzerFactory createAnalyzerFactory() {
        if (analyzerFactory == null) {
            analyzerFactory = new org.airsonic.player.service.search.AnalyzerFactory();
        }
        return analyzerFactory;
    }

    public synchronized org.airsonic.player.service.search.DocumentFactory createDocumentFactory() {
        if (documentFactory == null) {
            documentFactory = new org.airsonic.player.service.search.DocumentFactory();
        }
        return documentFactory;
    }

    public synchronized org.airsonic.player.service.search.IndexManager createIndexManager() {
        if (indexManager == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before IndexManager");
            }
            DaoGraph daos = persistenceGraph.getDaos();
            indexManager = new org.airsonic.player.service.search.IndexManager(
                    createAnalyzerFactory(), createDocumentFactory(), daos.getMediaFileDao(),
                    daos.getArtistDao(), daos.getAlbumDao());
        }
        return indexManager;
    }

    public synchronized org.airsonic.player.service.search.QueryFactory createQueryFactory() {
        if (queryFactory == null) {
            queryFactory = new org.airsonic.player.service.search.QueryFactory(createAnalyzerFactory());
        }
        return queryFactory;
    }

    public synchronized org.airsonic.player.service.search.SearchServiceUtilities createSearchServiceUtilities(
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (searchServiceUtilities == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before SearchServiceUtilities");
            }
            DaoGraph daos = persistenceGraph.getDaos();
            searchServiceUtilities = new org.airsonic.player.service.search.SearchServiceUtilities(
                    daos.getArtistDao(), daos.getAlbumDao(), mediaFileService);
        }
        return searchServiceUtilities;
    }

    public synchronized org.airsonic.player.service.search.SearchServiceImpl createSearchService(
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (searchService == null) {
            searchService = new org.airsonic.player.service.search.SearchServiceImpl(
                    createQueryFactory(), createIndexManager(), createSearchServiceUtilities(mediaFileService));
        }
        return searchService;
    }

    public synchronized org.airsonic.player.service.ConfigurationPropertiesService createConfigurationPropertiesService() {
        if (configurationPropertiesService == null) {
            // The Spring environment bootstrap still exposes this same file-backed source during migration.
            configurationPropertiesService = org.airsonic.player.service.ConfigurationPropertiesService.getInstance();
        }
        return configurationPropertiesService;
    }

    public synchronized org.airsonic.player.i18n.LocaleService createLocaleService(
            org.airsonic.player.service.SettingsService settingsService) {
        if (localeService == null) {
            localeService = new org.airsonic.player.i18n.LocaleService(settingsService);
        }
        return localeService;
    }

    public synchronized org.airsonic.player.service.SettingsService createSettingsService() {
        if (settingsService == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before SettingsService");
            }
            DaoGraph daos = persistenceGraph.getDaos();
            settingsService = new org.airsonic.player.service.SettingsService(
                    daos.getUserDao(), daos.getAvatarDao(), configuration, createCacheManager(),
                    createConfigurationPropertiesService());
            settingsService.init();
        }
        return settingsService;
    }

    public synchronized org.airsonic.player.service.GeneralSettingsAdministrationService
            createGeneralSettingsAdministrationService(org.airsonic.player.service.SettingsService settingsService,
                    org.airsonic.player.service.PlaylistService playlistService) {
        if (generalSettingsAdministrationService == null) {
            generalSettingsAdministrationService = new org.airsonic.player.service.GeneralSettingsAdministrationService(
                    settingsService, playlistService);
        }
        return generalSettingsAdministrationService;
    }

    public synchronized org.airsonic.player.service.MusicFolderAdministrationService
            createMusicFolderAdministrationService(org.airsonic.player.service.SettingsService settingsService,
                    org.airsonic.player.service.MediaScannerService mediaScannerService,
                    org.airsonic.player.service.MediaFolderService mediaFolderService,
                    org.airsonic.player.dao.ArtistDao artistDao,
                    org.airsonic.player.dao.AlbumDao albumDao,
                    org.airsonic.player.dao.MediaFileDao mediaFileDao,
                    org.airsonic.player.service.search.IndexManager indexManager,
                    org.airsonic.player.service.PlaylistService playlistService,
                    org.airsonic.player.service.CoverArtService coverArtService) {
        if (musicFolderAdministrationService == null) {
            musicFolderAdministrationService = new org.airsonic.player.service.MusicFolderAdministrationService(
                    settingsService, mediaScannerService, mediaFolderService, artistDao, albumDao, mediaFileDao,
                    indexManager, playlistService, coverArtService);
        }
        return musicFolderAdministrationService;
    }

    public synchronized org.airsonic.player.service.CoverArtAccentColorBackfill createCoverArtAccentColorBackfill(
            org.airsonic.player.service.CoverArtService coverArtService) {
        if (coverArtAccentColorBackfill == null) {
            coverArtAccentColorBackfill = new org.airsonic.player.service.CoverArtAccentColorBackfill(coverArtService);
            lifecycleRegistry.registerStartup("coverArtAccentColorBackfill",
                    coverArtAccentColorBackfill::backfillAccentColors);
        }
        return coverArtAccentColorBackfill;
    }

    public synchronized org.airsonic.player.service.UPnPService createUpnpService(
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.upnp.CustomContentDirectory contentDirectory) {
        if (upnpService == null) {
            upnpService = new org.airsonic.player.service.UPnPService(settingsService, contentDirectory);
            upnpService.init();
            lifecycleRegistry.registerShutdown("upnpService", upnpService);
        }
        return upnpService;
    }

    public synchronized org.airsonic.player.service.upnp.AlbumUpnpProcessor createAlbumUpnpProcessor(
            org.airsonic.player.dao.AlbumDao albumDao) {
        if (albumUpnpProcessor == null) {
            albumUpnpProcessor = new org.airsonic.player.service.upnp.AlbumUpnpProcessor();
            albumUpnpProcessor.setAlbumDao(albumDao);
        }
        return albumUpnpProcessor;
    }

    public synchronized org.airsonic.player.service.upnp.RecentAlbumUpnpProcessor createRecentAlbumUpnpProcessor(
            org.airsonic.player.dao.AlbumDao albumDao) {
        if (recentAlbumUpnpProcessor == null) {
            recentAlbumUpnpProcessor = new org.airsonic.player.service.upnp.RecentAlbumUpnpProcessor();
            recentAlbumUpnpProcessor.setAlbumDao(albumDao);
        }
        return recentAlbumUpnpProcessor;
    }

    public synchronized org.airsonic.player.service.upnp.ArtistUpnpProcessor createArtistUpnpProcessor(
            org.airsonic.player.dao.ArtistDao artistDao) {
        if (artistUpnpProcessor == null) {
            artistUpnpProcessor = new org.airsonic.player.service.upnp.ArtistUpnpProcessor();
            artistUpnpProcessor.setArtistDao(artistDao);
        }
        return artistUpnpProcessor;
    }

    public synchronized org.airsonic.player.service.upnp.GenreUpnpProcessor createGenreUpnpProcessor() {
        if (genreUpnpProcessor == null) {
            genreUpnpProcessor = new org.airsonic.player.service.upnp.GenreUpnpProcessor();
        }
        return genreUpnpProcessor;
    }

    public synchronized org.airsonic.player.service.upnp.MediaFileUpnpProcessor createMediaFileUpnpProcessor(
            org.airsonic.player.dao.MediaFileDao mediaFileDao) {
        if (mediaFileUpnpProcessor == null) {
            mediaFileUpnpProcessor = new org.airsonic.player.service.upnp.MediaFileUpnpProcessor();
            mediaFileUpnpProcessor.setMediaFileDao(mediaFileDao);
        }
        return mediaFileUpnpProcessor;
    }

    public synchronized org.airsonic.player.service.upnp.PlaylistUpnpProcessor createPlaylistUpnpProcessor(
            org.airsonic.player.service.PlaylistService playlistService) {
        if (playlistUpnpProcessor == null) {
            playlistUpnpProcessor = new org.airsonic.player.service.upnp.PlaylistUpnpProcessor();
            playlistUpnpProcessor.setPlaylistService(playlistService);
        }
        return playlistUpnpProcessor;
    }

    public synchronized org.airsonic.player.service.upnp.RootUpnpProcessor createRootUpnpProcessor(
            org.airsonic.player.service.search.IndexManager indexManager) {
        if (rootUpnpProcessor == null) {
            rootUpnpProcessor = new org.airsonic.player.service.upnp.RootUpnpProcessor(indexManager);
        }
        return rootUpnpProcessor;
    }

    public synchronized org.airsonic.player.service.upnp.DispatchingContentDirectory createDispatchingContentDirectory(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.service.PlaylistService playlistService,
            org.airsonic.player.service.SearchService searchService,
            org.airsonic.player.service.MusicIndexService musicIndexService,
            org.airsonic.player.service.search.IndexManager indexManager,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.PlayerService playerService,
            org.airsonic.player.service.TranscodingService transcodingService,
            org.airsonic.player.service.JWTSecurityService jwtSecurityService) {
        if (dispatchingContentDirectory == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before UPnP processors");
            }
            dispatchingContentDirectory = new org.airsonic.player.service.upnp.DispatchingContentDirectory(
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

    public synchronized org.airsonic.player.service.upnp.FolderBasedContentDirectory createFolderBasedContentDirectory(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.service.PlaylistService playlistService,
            org.airsonic.player.service.search.IndexManager indexManager,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.PlayerService playerService,
            org.airsonic.player.service.TranscodingService transcodingService,
            org.airsonic.player.service.JWTSecurityService jwtSecurityService) {
        if (folderBasedContentDirectory == null) {
            folderBasedContentDirectory = new org.airsonic.player.service.upnp.FolderBasedContentDirectory(
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
        if (persistenceGraph != null) {
            if (persistenceGraph.getDataSource() != dataSource) {
                throw new IllegalStateException("Persistence graph already created for another DataSource");
            }
            return persistenceGraph;
        }

        JdbcFactory jdbcFactory = new JdbcFactory();
        org.airsonic.player.dao.TransactionManager transactionManager =
                jdbcFactory.transactionManager(dataSource);
        org.airsonic.player.dao.JdbcHelper jdbcHelper = jdbcFactory.jdbcHelper(dataSource, transactionManager);
        DaoGraph daoGraph = new DaoFactory().create(jdbcHelper, transactionManager);
        persistenceGraph = new PersistenceGraph(dataSource, transactionManager, jdbcHelper, daoGraph);
        if (dataSource instanceof AutoCloseable closeable) {
            lifecycleRegistry.registerShutdown("dataSource", closeable);
        }
        return persistenceGraph;
    }

    public synchronized javax.cache.CacheManager createCacheManager() {
        if (cacheManager == null) {
            cacheManager = new CacheFactory().createCacheManager();
            lifecycleRegistry.registerShutdown("cacheManager", cacheManager::close);
        }
        return cacheManager;
    }

    public synchronized org.airsonic.player.service.MediaFolderService createMediaFolderService() {
        if (persistenceGraph == null) {
            throw new IllegalStateException("Persistence graph must be created before media services");
        }
        if (mediaFolderService == null) {
            mediaFolderService = new org.airsonic.player.service.MediaFolderService(
                    persistenceGraph.getDaos().getMusicFolderDao(),
                    persistenceGraph.getDaos().getMediaFileDao(),
                    createCacheManager());
        }
        return mediaFolderService;
    }

    public synchronized org.airsonic.player.service.MediaFileService createMediaFileService(
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.dao.MediaFileDao mediaFileDao,
            org.airsonic.player.dao.AlbumDao albumDao,
            org.airsonic.player.service.metadata.JaudiotaggerParser parser,
            org.airsonic.player.service.metadata.MetaDataParserFactory metaDataParserFactory,
            org.airsonic.player.service.CoverArtService coverArtService,
            org.airsonic.player.i18n.LocaleService localeService) {
        if (mediaFileService == null) {
            mediaFileService = new org.airsonic.player.service.MediaFileService(
                    securityService, settingsService, mediaFileDao, createMediaFolderService(), albumDao,
                    parser, metaDataParserFactory, coverArtService, localeService, createCacheManager());
        }
        return mediaFileService;
    }

    public synchronized org.airsonic.player.service.metadata.DefaultMetaDataParser createDefaultMetaDataParser() {
        if (defaultMetaDataParser == null) {
            defaultMetaDataParser = new org.airsonic.player.service.metadata.DefaultMetaDataParser(createMediaFolderService());
        }
        return defaultMetaDataParser;
    }

    public synchronized org.airsonic.player.service.metadata.FFmpegParser createFFmpegParser() {
        if (ffmpegParser == null) {
            ffmpegParser = new org.airsonic.player.service.metadata.FFmpegParser(createMediaFolderService());
        }
        return ffmpegParser;
    }

    public synchronized org.airsonic.player.service.metadata.JaudiotaggerParser createJaudiotaggerParser() {
        if (jaudiotaggerParser == null) {
            jaudiotaggerParser = new org.airsonic.player.service.metadata.JaudiotaggerParser(createMediaFolderService());
        }
        return jaudiotaggerParser;
    }

    public synchronized org.airsonic.player.service.metadata.MetaDataParserFactory createMetaDataParserFactory() {
        if (metaDataParserFactory == null) {
            metaDataParserFactory = new org.airsonic.player.service.metadata.MetaDataParserFactory(
                    new java.util.ArrayList<>(java.util.List.of(
                            createJaudiotaggerParser(), createFFmpegParser(), createDefaultMetaDataParser())));
        }
        return metaDataParserFactory;
    }

    public synchronized org.airsonic.player.service.CoverArtService createCoverArtService(
            org.airsonic.player.dao.CoverArtDao coverArtDao) {
        if (coverArtService == null) {
            coverArtService = new org.airsonic.player.service.CoverArtService(
                    coverArtDao, createMediaFolderService(), createJaudiotaggerParser(), createCacheManager());
        }
        return coverArtService;
    }

    public synchronized org.airsonic.player.service.hls.HlsPlaylistService createHlsPlaylistService(
            org.airsonic.player.service.JWTSecurityService jwtSecurityService) {
        if (hlsPlaylistService == null) {
            hlsPlaylistService = new org.airsonic.player.service.hls.HlsPlaylistService(jwtSecurityService);
        }
        return hlsPlaylistService;
    }

    public synchronized org.airsonic.player.service.hls.HlsSegmentService createHlsSegmentService(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.StatusService statusService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.TranscodingService transcodingService) {
        if (hlsSegmentService == null) {
            hlsSegmentService = new org.airsonic.player.service.hls.HlsSegmentService(
                    mediaFileService, securityService, statusService, settingsService, transcodingService);
            hlsSegmentService.init();
        }
        return hlsSegmentService;
    }

    public synchronized org.airsonic.player.service.TagEditService createTagEditService(
            org.airsonic.player.service.metadata.MetaDataParserFactory metaDataParserFactory,
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.SecurityService securityService) {
        if (tagEditService == null) {
            tagEditService = new org.airsonic.player.service.TagEditService(
                    metaDataParserFactory, mediaFileService, createMediaFolderService(), securityService);
        }
        return tagEditService;
    }

    public synchronized org.airsonic.player.service.DownloadService createDownloadService(
            org.airsonic.player.service.StatusService statusService,
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.PlaylistService playlistService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.CoverArtService coverArtService) {
        if (downloadService == null) {
            downloadService = new org.airsonic.player.service.DownloadService(statusService, securityService,
                    playlistService, settingsService, mediaFileService, createMediaFolderService(), coverArtService);
        }
        return downloadService;
    }

    public synchronized org.airsonic.player.service.UserAdministrationService createUserAdministrationService(
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.SettingsService settingsService) {
        if (userAdministrationService == null) {
            userAdministrationService = new org.airsonic.player.service.UserAdministrationService(
                    securityService, settingsService, createMediaFolderService());
        }
        return userAdministrationService;
    }

    public synchronized org.airsonic.player.service.MusicIndexStateService createMusicIndexStateService(
            org.airsonic.player.service.MediaScannerService scanner,
            org.airsonic.player.service.SettingsService settingsService) {
        if (musicIndexStateService == null) {
            musicIndexStateService = new org.airsonic.player.service.MusicIndexStateService(
                    scanner, settingsService, createMediaFolderService());
        }
        return musicIndexStateService;
    }

    public synchronized org.airsonic.player.service.CoverArtImageService createCoverArtImageService(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.CoverArtService coverArtService,
            org.airsonic.player.service.TranscodingService transcodingService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.PlaylistService playlistService,
            org.airsonic.player.dao.ArtistDao artistDao,
            org.airsonic.player.dao.AlbumDao albumDao) {
        if (coverArtImageService == null) {
            coverArtImageService = new org.airsonic.player.service.CoverArtImageService(
                    mediaFileService, coverArtService, transcodingService, settingsService, playlistService,
                    artistDao, albumDao, createJaudiotaggerParser());
            coverArtImageService.init();
        }
        return coverArtImageService;
    }

    public synchronized org.airsonic.player.service.MediaStreamService createMediaStreamService(
            org.airsonic.player.service.StatusService statusService,
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.TranscodingService transcodingService,
            org.airsonic.player.service.AudioScrobblerService audioScrobblerService,
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.SearchService searchService) {
        if (mediaStreamService == null) {
            mediaStreamService = new org.airsonic.player.service.MediaStreamService(
                    statusService, securityService, settingsService, transcodingService, audioScrobblerService,
                    mediaFileService, searchService, createPlayerDaoPlayQueueFactory());
        }
        return mediaStreamService;
    }

    public synchronized org.airsonic.player.service.playlist.DefaultPlaylistExportHandler createDefaultPlaylistExportHandler(
            org.airsonic.player.dao.MediaFileDao mediaFileDao) {
        if (defaultPlaylistExportHandler == null) {
            defaultPlaylistExportHandler = new org.airsonic.player.service.playlist.DefaultPlaylistExportHandler(
                    mediaFileDao, createMediaFolderService());
        }
        return defaultPlaylistExportHandler;
    }

    public synchronized org.airsonic.player.service.playlist.DefaultPlaylistImportHandler createDefaultPlaylistImportHandler(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.SettingsService settingsService) {
        if (defaultPlaylistImportHandler == null) {
            defaultPlaylistImportHandler = new org.airsonic.player.service.playlist.DefaultPlaylistImportHandler(
                    mediaFileService, settingsService);
        }
        return defaultPlaylistImportHandler;
    }

    public synchronized org.airsonic.player.service.playlist.XspfPlaylistExportHandler createXspfPlaylistExportHandler(
            org.airsonic.player.dao.MediaFileDao mediaFileDao,
            org.airsonic.player.dao.PlaylistDao playlistDao,
            org.airsonic.player.service.CoverArtService coverArtService) {
        if (xspfPlaylistExportHandler == null) {
            xspfPlaylistExportHandler = new org.airsonic.player.service.playlist.XspfPlaylistExportHandler(
                    mediaFileDao, playlistDao, createMediaFolderService(), coverArtService);
        }
        return xspfPlaylistExportHandler;
    }

    public synchronized org.airsonic.player.service.playlist.XspfPlaylistImportHandler createXspfPlaylistImportHandler(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.SettingsService settingsService) {
        if (xspfPlaylistImportHandler == null) {
            xspfPlaylistImportHandler = new org.airsonic.player.service.playlist.XspfPlaylistImportHandler(
                    mediaFileService, settingsService);
        }
        return xspfPlaylistImportHandler;
    }

    public synchronized org.airsonic.player.service.PlaylistService createPlaylistService(
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.SettingsService settingsService,
            java.util.List<org.airsonic.player.service.playlist.PlaylistExportHandler> exportHandlers,
            java.util.List<org.airsonic.player.service.playlist.PlaylistImportHandler> importHandlers) throws Exception {
        if (playlistService == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before PlaylistService");
            }
            playlistService = new org.airsonic.player.service.PlaylistService(
                    persistenceGraph.getDaos().getMediaFileDao(), persistenceGraph.getDaos().getPlaylistDao(),
                    securityService, settingsService, exportHandlers, importHandlers,
                    createPathWatcherService(), createCacheManager());
            playlistService.init();
        }
        return playlistService;
    }

    public synchronized org.airsonic.player.dao.PlayerDao createPlayerDao() throws Exception {
        if (persistenceGraph == null) {
            throw new IllegalStateException("Persistence graph must be created before PlayerDao");
        }
        if (playerDao == null) {
            org.airsonic.player.dao.PlayerDaoPlayQueueFactory queueFactory = createPlayerDaoPlayQueueFactory();
            playerDao = new org.airsonic.player.dao.PlayerDao(queueFactory, persistenceGraph.getJdbcHelper());
            playerDao.register();
        }
        return playerDao;
    }

    public synchronized org.airsonic.player.dao.PlayerDaoPlayQueueFactory createPlayerDaoPlayQueueFactory() {
        if (playerDaoPlayQueueFactory == null) {
            playerDaoPlayQueueFactory = new org.airsonic.player.dao.PlayerDaoPlayQueueFactory(createMediaFolderService());
        }
        return playerDaoPlayQueueFactory;
    }

    public synchronized org.airsonic.player.service.VersionService createVersionService() throws java.io.IOException {
        if (versionService == null) {
            versionService = new org.airsonic.player.service.VersionService();
        }
        return versionService;
    }

    public synchronized org.airsonic.player.service.ServerInfoService createServerInfoService(
            org.airsonic.player.service.VersionService versions,
            org.airsonic.player.service.SettingsService settingsService) {
        if (serverInfoService == null) {
            serverInfoService = new org.airsonic.player.service.ServerInfoService(versions, settingsService);
        }
        return serverInfoService;
    }

    public synchronized org.airsonic.player.service.StatusService createStatusService(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.TaskSchedulingService taskSchedulingService) {
        if (statusService == null) {
            statusService = new org.airsonic.player.service.StatusService(
                    mediaFileService, mediaFolderService, settingsService, taskSchedulingService);
            statusService.cleanup();
        }
        return statusService;
    }

    public synchronized org.airsonic.player.service.PasswordRecoveryService createPasswordRecoveryService(
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.SecurityService securityService) {
        if (passwordRecoveryService == null) {
            passwordRecoveryService = new org.airsonic.player.service.PasswordRecoveryService(
                    settingsService, securityService);
        }
        return passwordRecoveryService;
    }

    public synchronized org.airsonic.player.service.LyricsService createLyricsService() {
        if (lyricsService == null) {
            lyricsService = new org.airsonic.player.service.LyricsService();
        }
        return lyricsService;
    }

    public synchronized org.airsonic.player.service.BookmarkService createBookmarkService(
            org.airsonic.player.dao.BookmarkDao bookmarkDao,
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (bookmarkService == null) {
            bookmarkService = new org.airsonic.player.service.BookmarkService(bookmarkDao, mediaFileService);
        }
        return bookmarkService;
    }

    public synchronized org.airsonic.player.service.RatingService createRatingService(
            org.airsonic.player.dao.RatingDao ratingDao,
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (ratingService == null) {
            ratingService = new org.airsonic.player.service.RatingService(ratingDao, securityService, mediaFileService);
        }
        return ratingService;
    }

    public synchronized org.airsonic.player.service.MusicIndexService createMusicIndexService(
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (musicIndexService == null) {
            musicIndexService = new org.airsonic.player.service.MusicIndexService(settingsService, mediaFileService);
        }
        return musicIndexService;
    }

    public synchronized org.airsonic.player.service.SecurityService createSecurityService(
            org.airsonic.player.dao.UserDao userDao,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.security.CredentialEncoderRegistry credentialEncoders) {
        if (securityService == null) {
            securityService = new org.airsonic.player.service.SecurityService(
                    userDao, settingsService, mediaFolderService, createCacheManager(), credentialEncoders);
        }
        return securityService;
    }

    public synchronized org.airsonic.player.security.SubsonicCredentialAuthenticationService
            createSubsonicCredentialAuthenticationService(
                    org.airsonic.player.service.SecurityService securityService,
                    org.airsonic.player.security.CredentialEncoderRegistry credentialEncoders) {
        if (subsonicCredentialAuthenticationService == null) {
            subsonicCredentialAuthenticationService = new org.airsonic.player.security.SubsonicCredentialAuthenticationService(
                    securityService, credentialEncoders);
        }
        return subsonicCredentialAuthenticationService;
    }

    public synchronized org.airsonic.player.service.JWTSecurityService createJwtSecurityService(
            org.airsonic.player.service.SettingsService settingsService) {
        if (jwtSecurityService == null) {
            org.airsonic.player.service.JWTSecurityService.initializeSigningKey(settingsService);
            jwtSecurityService = new org.airsonic.player.service.JWTSecurityService(settingsService);
        }
        return jwtSecurityService;
    }

    public synchronized org.airsonic.player.service.DatabaseService createDatabaseService(
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.dao.DatabaseDao databaseDao,
            org.airsonic.player.service.TaskSchedulingService taskSchedulingService) {
        if (databaseService == null) {
            databaseService = new org.airsonic.player.service.DatabaseService(
                    settingsService, databaseDao, taskSchedulingService);
            databaseService.init();
        }
        return databaseService;
    }

    public synchronized org.airsonic.player.service.JukeboxLegacySubsonicService createJukeboxLegacySubsonicService(
            org.airsonic.player.service.TranscodingService transcodingService,
            org.airsonic.player.service.AudioScrobblerService audioScrobblerService,
            org.airsonic.player.service.StatusService statusService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (jukeboxLegacySubsonicService == null) {
            jukeboxLegacySubsonicService = new org.airsonic.player.service.JukeboxLegacySubsonicService(
                    transcodingService, audioScrobblerService, statusService, settingsService,
                    securityService, mediaFileService);
        }
        return jukeboxLegacySubsonicService;
    }

    public synchronized org.airsonic.player.service.JukeboxService createJukeboxService(
            org.airsonic.player.service.JukeboxLegacySubsonicService legacyJukebox) {
        if (jukeboxService == null) {
            jukeboxService = new org.airsonic.player.service.JukeboxService(legacyJukebox);
        }
        return jukeboxService;
    }

    public synchronized org.airsonic.player.service.PlayQueueService createPlayQueueService(
            org.airsonic.player.service.SearchService searchService,
            org.airsonic.player.service.JukeboxService jukeboxService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.LastFmService lastFmService,
            org.airsonic.player.service.RatingService ratingService,
            org.airsonic.player.service.PlaylistService playlistService,
            org.airsonic.player.dao.MediaFileDao mediaFileDao,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.dao.PlayQueueDao playQueueDao,
            org.airsonic.player.service.JWTSecurityService jwtSecurityService) {
        if (playQueueService == null) {
            playQueueService = new org.airsonic.player.service.PlayQueueService(
                    searchService, jukeboxService, settingsService, mediaFileService, lastFmService,
                    ratingService, playlistService, mediaFileDao, mediaFolderService, playQueueDao, jwtSecurityService);
        }
        return playQueueService;
    }

    public synchronized org.airsonic.player.service.LastFmService createLastFmService(
            org.airsonic.player.dao.MediaFileDao mediaFileDao,
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.dao.ArtistDao artistDao) {
        if (lastFmService == null) {
            lastFmService = new org.airsonic.player.service.LastFmService(mediaFileDao, mediaFileService, artistDao);
            lastFmService.init();
        }
        return lastFmService;
    }

    public synchronized org.airsonic.player.service.ShareService createShareService(
            org.airsonic.player.dao.ShareDao shareDao,
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.JWTSecurityService jwtSecurityService) {
        if (shareService == null) {
            shareService = new org.airsonic.player.service.ShareService(shareDao, mediaFileService, jwtSecurityService);
        }
        return shareService;
    }

    public synchronized org.airsonic.player.service.UploadFileService createUploadFileService(
            org.airsonic.player.service.SecurityService securityService) {
        if (uploadFileService == null) {
            uploadFileService = new org.airsonic.player.service.UploadFileService(securityService);
        }
        return uploadFileService;
    }

    public synchronized org.airsonic.player.service.AudioScrobblerService createAudioScrobblerService(
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.SecurityService securityService) {
        if (audioScrobblerService == null) {
            audioScrobblerService = new org.airsonic.player.service.AudioScrobblerService(settingsService, securityService);
        }
        return audioScrobblerService;
    }

    public synchronized org.airsonic.player.service.CaptionService createCaptionService(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.metadata.MetaDataParserFactory metaDataParserFactory,
            org.airsonic.player.service.JWTSecurityService jwtSecurityService) {
        if (captionService == null) {
            captionService = new org.airsonic.player.service.CaptionService(
                    mediaFileService, mediaFolderService, settingsService, metaDataParserFactory, jwtSecurityService);
        }
        return captionService;
    }

    public synchronized org.airsonic.player.service.VideoPlaybackService createVideoPlaybackService(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.CaptionService captionService,
            org.airsonic.player.service.JWTSecurityService jwtSecurityService,
            org.airsonic.player.service.BookmarkService bookmarkService,
            org.airsonic.player.service.SettingsService settingsService) {
        if (videoPlaybackService == null) {
            videoPlaybackService = new org.airsonic.player.service.VideoPlaybackService(
                    mediaFileService, captionService, jwtSecurityService, bookmarkService, settingsService);
        }
        return videoPlaybackService;
    }

    public synchronized org.airsonic.player.service.TranscodingService createTranscodingService(
            org.airsonic.player.dao.TranscodingDao transcodingDao,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.MediaFolderService mediaFolderService) {
        return createTranscodingService(transcodingDao, settingsService, mediaFolderService, this::requirePlayerService);
    }

    public synchronized org.airsonic.player.service.TranscodingService createTranscodingService(
            org.airsonic.player.dao.TranscodingDao transcodingDao,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            java.util.function.Supplier<org.airsonic.player.service.PlayerService> playerServiceSupplier) {
        if (transcodingService == null) {
            transcodingService = new org.airsonic.player.service.TranscodingService(
                    transcodingDao, settingsService, mediaFolderService, playerServiceSupplier);
        }
        return transcodingService;
    }

    private synchronized org.airsonic.player.service.PlayerService requirePlayerService() {
        if (playerService == null) {
            throw new IllegalStateException("PlayerService must be created before player transcodings are used");
        }
        return playerService;
    }

    public synchronized org.airsonic.player.service.PlayerService createPlayerService(
            org.airsonic.player.dao.PlayerDao playerDao,
            org.airsonic.player.service.StatusService statusService,
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.TranscodingService transcodingService) {
        if (playerService == null) {
            playerService = new org.airsonic.player.service.PlayerService(
                    playerDao, statusService, securityService, transcodingService);
            playerService.init();
        }
        return playerService;
    }
}
