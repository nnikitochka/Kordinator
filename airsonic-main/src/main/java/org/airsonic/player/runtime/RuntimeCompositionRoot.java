package org.airsonic.player.runtime;

import org.airsonic.player.ktor.album.AlbumRoutes;

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
    private org.airsonic.player.service.MediaFolderService mediaFolderService;
    private org.airsonic.player.service.MediaFileService mediaFileService;
    private org.airsonic.player.dao.PlayerDao playerDao;
    private org.airsonic.player.service.VersionService versionService;
    private org.airsonic.player.service.ServerInfoService serverInfoService;
    private org.airsonic.player.service.StatusService statusService;
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
    private org.airsonic.player.service.TagEditService tagEditService;
    private org.airsonic.player.service.DownloadService downloadService;
    private org.airsonic.player.service.CoverArtImageService coverArtImageService;
    private org.airsonic.player.dao.PlayerDaoPlayQueueFactory playerDaoPlayQueueFactory;
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
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private org.airsonic.player.controller.JAXBWriter subsonicJaxbWriter;
    private org.airsonic.player.controller.rest.SubsonicResponseMapper subsonicResponseMapper;
    private org.airsonic.player.controller.rest.RestPlayerResolver restPlayerResolver;
    private org.airsonic.player.health.StatusAggregator statusAggregator;
    private org.airsonic.player.controller.rest.starred.StarredController starredController;
    private org.airsonic.player.controller.rest.jukebox.JukeboxController jukeboxController;
    private org.airsonic.player.controller.rest.playlists.PlaylistExportController playlistExportController;
    private org.airsonic.player.controller.rest.playlists.PlaylistImportController playlistImportController;
    private org.airsonic.player.controller.rest.scrobble.ScrobbleController scrobbleController;
    private org.airsonic.player.controller.rest.download.DownloadRestController downloadRestController;
    private org.airsonic.player.controller.rest.tags.TagEditController tagEditController;
    private org.airsonic.player.controller.rest.cover.CoverArtRestController coverArtRestController;
    private org.airsonic.player.controller.rest.avatar.AvatarRestController avatarRestController;
    private org.airsonic.player.controller.rest.avatar.AvatarUploadController avatarUploadController;
    private org.airsonic.player.controller.rest.shares.PublicShareController publicShareController;
    private org.airsonic.player.controller.captions.CaptionsRestController captionsRestController;
    private org.airsonic.player.controller.rest.shares.SharesController sharesController;
    private org.airsonic.player.ktor.KordinatorSessionService kordinatorSessionService;
    private org.airsonic.player.ktor.KordinatorAuthRoutes kordinatorAuthRoutes;
    private org.airsonic.player.ktor.KordinatorPlaylistRoutes kordinatorPlaylistRoutes;
    private AlbumRoutes albumRoutes;
    private org.airsonic.player.ktor.KordinatorArtistRoutes kordinatorArtistRoutes;
    private org.airsonic.player.ktor.KordinatorSearchRoutes kordinatorSearchRoutes;
    private org.airsonic.player.ktor.KordinatorFavoriteRoutes kordinatorFavoriteRoutes;
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

    public synchronized org.airsonic.player.controller.JAXBWriter createSubsonicJaxbWriter() {
        if (subsonicJaxbWriter == null) {
            subsonicJaxbWriter = new org.airsonic.player.controller.JAXBWriter();
        }
        return subsonicJaxbWriter;
    }

    public synchronized org.airsonic.player.controller.rest.SubsonicResponseMapper createSubsonicResponseMapper(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.RatingService ratingService,
            org.airsonic.player.service.CoverArtService coverArtService,
            org.airsonic.player.service.TranscodingService transcodingService) {
        if (subsonicResponseMapper == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before SubsonicResponseMapper");
            }
            DaoGraph daos = persistenceGraph.getDaos();
            subsonicResponseMapper = new org.airsonic.player.controller.rest.SubsonicResponseMapper(
                    mediaFileService, daos.getMediaFileDao(), ratingService, coverArtService, transcodingService,
                    daos.getAlbumDao(), daos.getArtistDao(), createSubsonicJaxbWriter());
        }
        return subsonicResponseMapper;
    }

    public synchronized org.airsonic.player.controller.rest.RestPlayerResolver createRestPlayerResolver(
            org.airsonic.player.service.PlayerService playerService) {
        if (restPlayerResolver == null) {
            restPlayerResolver = new org.airsonic.player.controller.rest.RestPlayerResolver(playerService);
        }
        return restPlayerResolver;
    }

    public synchronized org.airsonic.player.health.StatusAggregator createStatusAggregator() {
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

    public synchronized org.airsonic.player.controller.rest.starred.StarredController createStarredController(
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.controller.rest.SubsonicResponseMapper responseMapper) {
        if (starredController == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before StarredController");
            }
            DaoGraph daos = persistenceGraph.getDaos();
            starredController = new org.airsonic.player.controller.rest.starred.StarredController(
                    daos.getMediaFileDao(), daos.getAlbumDao(), daos.getArtistDao(),
                    mediaFolderService, responseMapper, createSubsonicJaxbWriter());
        }
        return starredController;
    }

    public synchronized org.airsonic.player.controller.rest.jukebox.JukeboxController createJukeboxController(
            org.airsonic.player.service.PlayQueueService playQueueService,
            org.airsonic.player.service.JukeboxService jukeboxService,
            org.airsonic.player.controller.rest.SubsonicResponseMapper responseMapper) {
        if (jukeboxController == null) {
            jukeboxController = new org.airsonic.player.controller.rest.jukebox.JukeboxController(
                    playQueueService, jukeboxService, responseMapper, createSubsonicJaxbWriter());
        }
        return jukeboxController;
    }

    public synchronized org.airsonic.player.controller.rest.playlists.PlaylistExportController
            createPlaylistExportController(org.airsonic.player.service.PlaylistService playlistService) {
        if (playlistExportController == null) {
            playlistExportController = new org.airsonic.player.controller.rest.playlists.PlaylistExportController(
                    playlistService, createSubsonicJaxbWriter());
        }
        return playlistExportController;
    }

    public synchronized org.airsonic.player.controller.rest.playlists.PlaylistImportController
            createPlaylistImportController(org.airsonic.player.service.PlaylistService playlistService) {
        if (playlistImportController == null) {
            playlistImportController = new org.airsonic.player.controller.rest.playlists.PlaylistImportController(
                    playlistService, createSubsonicJaxbWriter());
        }
        return playlistImportController;
    }

    public synchronized org.airsonic.player.controller.rest.scrobble.ScrobbleController createScrobbleController(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.StatusService statusService,
            org.airsonic.player.service.AudioScrobblerService audioScrobblerService) {
        if (scrobbleController == null) {
            scrobbleController = new org.airsonic.player.controller.rest.scrobble.ScrobbleController(
                    mediaFileService, statusService, audioScrobblerService, createSubsonicJaxbWriter());
        }
        return scrobbleController;
    }

    public synchronized org.airsonic.player.controller.rest.download.DownloadRestController createDownloadRestController() {
        if (downloadRestController == null) {
            downloadRestController = new org.airsonic.player.controller.rest.download.DownloadRestController(
                    createSubsonicJaxbWriter());
        }
        return downloadRestController;
    }

    public synchronized org.airsonic.player.controller.rest.tags.TagEditController createTagEditController(
            org.airsonic.player.service.TagEditService tagEditService) {
        if (tagEditController == null) {
            tagEditController = new org.airsonic.player.controller.rest.tags.TagEditController(
                    tagEditService, createSubsonicJaxbWriter());
        }
        return tagEditController;
    }

    public synchronized org.airsonic.player.controller.rest.cover.CoverArtRestController createCoverArtRestController(
            org.airsonic.player.service.CoverArtImageService coverArtImageService) {
        if (coverArtRestController == null) {
            coverArtRestController = new org.airsonic.player.controller.rest.cover.CoverArtRestController(
                    coverArtImageService);
        }
        return coverArtRestController;
    }

    public synchronized org.airsonic.player.controller.rest.avatar.AvatarRestController createAvatarRestController(
            org.airsonic.player.service.SettingsService settingsService) {
        if (avatarRestController == null) {
            avatarRestController = new org.airsonic.player.controller.rest.avatar.AvatarRestController(settingsService);
        }
        return avatarRestController;
    }

    public synchronized org.airsonic.player.controller.rest.avatar.AvatarUploadController
            createAvatarUploadController(org.airsonic.player.service.SettingsService settingsService) {
        if (avatarUploadController == null) {
            avatarUploadController = new org.airsonic.player.controller.rest.avatar.AvatarUploadController(
                    settingsService, createSubsonicJaxbWriter());
        }
        return avatarUploadController;
    }

    public synchronized org.airsonic.player.controller.rest.shares.PublicShareController createPublicShareController(
            org.airsonic.player.service.ShareService shareService,
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.service.JWTSecurityService jwtSecurityService,
            org.airsonic.player.service.metadata.MetaDataParserFactory metadataParsers,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        if (publicShareController == null) {
            publicShareController = new org.airsonic.player.controller.rest.shares.PublicShareController(
                    shareService, mediaFileService, mediaFolderService, jwtSecurityService,
                    metadataParsers, objectMapper);
        }
        return publicShareController;
    }

    public synchronized org.airsonic.player.controller.captions.CaptionsRestController createCaptionsRestController(
            org.airsonic.player.service.CaptionService captionService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        if (captionsRestController == null) {
            captionsRestController = new org.airsonic.player.controller.captions.CaptionsRestController(
                    captionService, mediaFolderService, objectMapper);
        }
        return captionsRestController;
    }

    public synchronized org.airsonic.player.controller.rest.shares.SharesController createSharesController(
            org.airsonic.player.service.ShareService shareService,
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.controller.rest.SubsonicResponseMapper responseMapper,
            org.airsonic.player.service.PlayerService playerService,
            org.airsonic.player.service.PlaylistService playlistService,
            org.airsonic.player.service.SecurityService securityService) {
        if (sharesController == null) {
            sharesController = new org.airsonic.player.controller.rest.shares.SharesController(
                    shareService, mediaFileService, mediaFolderService, responseMapper, createSubsonicJaxbWriter(),
                    playerService, playlistService, securityService);
        }
        return sharesController;
    }

    public synchronized org.airsonic.player.ktor.KordinatorSessionService createKordinatorSessionService(
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.security.CredentialEncoderRegistry credentialEncoders,
            org.airsonic.player.service.SettingsService settingsService) {
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

    public synchronized org.airsonic.player.ktor.KordinatorAuthRoutes createKordinatorAuthRoutes(
            org.airsonic.player.ktor.KordinatorSessionService sessionService) {
        if (kordinatorAuthRoutes == null) {
            kordinatorAuthRoutes = new org.airsonic.player.ktor.KordinatorAuthRoutes(
                    sessionService, new org.airsonic.player.security.LoginRateLimiter());
        }
        return kordinatorAuthRoutes;
    }

    public synchronized org.airsonic.player.ktor.KordinatorPlaylistRoutes createKordinatorPlaylistRoutes(
            org.airsonic.player.service.PlaylistService playlistService,
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (kordinatorPlaylistRoutes == null) {
            kordinatorPlaylistRoutes = new org.airsonic.player.ktor.KordinatorPlaylistRoutes(
                    playlistService, securityService, mediaFileService);
        }
        return kordinatorPlaylistRoutes;
    }

    public synchronized AlbumRoutes createKordinatorAlbumRoutes(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.service.SecurityService securityService) {
        if (albumRoutes == null) {
            albumRoutes = new AlbumRoutes(
                    mediaFileService, mediaFolderService, securityService);
        }
        return albumRoutes;
    }

    public synchronized org.airsonic.player.ktor.KordinatorArtistRoutes createKordinatorArtistRoutes(
            org.airsonic.player.service.MediaFolderService mediaFolderService) {
        if (kordinatorArtistRoutes == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before KordinatorArtistRoutes");
            }
            DaoGraph daos = persistenceGraph.getDaos();
            kordinatorArtistRoutes = new org.airsonic.player.ktor.KordinatorArtistRoutes(
                    daos.getArtistDao(), daos.getAlbumDao(), mediaFolderService);
        }
        return kordinatorArtistRoutes;
    }

    public synchronized org.airsonic.player.ktor.KordinatorSearchRoutes createKordinatorSearchRoutes(
            org.airsonic.player.service.SearchService searchService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.service.SecurityService securityService) {
        if (kordinatorSearchRoutes == null) {
            kordinatorSearchRoutes = new org.airsonic.player.ktor.KordinatorSearchRoutes(
                    searchService, mediaFolderService, securityService);
        }
        return kordinatorSearchRoutes;
    }

    public synchronized org.airsonic.player.ktor.KordinatorFavoriteRoutes createKordinatorFavoriteRoutes(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.service.SecurityService securityService) {
        if (kordinatorFavoriteRoutes == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before KordinatorFavoriteRoutes");
            }
            DaoGraph daos = persistenceGraph.getDaos();
            kordinatorFavoriteRoutes = new org.airsonic.player.ktor.KordinatorFavoriteRoutes(
                    daos.getMediaFileDao(), daos.getAlbumDao(), daos.getArtistDao(),
                    mediaFileService, mediaFolderService, securityService);
        }
        return kordinatorFavoriteRoutes;
    }

    public synchronized org.airsonic.player.ktor.KordinatorLibraryRoutes createKordinatorLibraryRoutes(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.service.SecurityService securityService) {
        if (kordinatorLibraryRoutes == null) {
            kordinatorLibraryRoutes = new org.airsonic.player.ktor.KordinatorLibraryRoutes(
                    mediaFileService, mediaFolderService, securityService);
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

    public synchronized org.airsonic.player.monitor.MetricsManager createMetricsManager() {
        if (metricsManager == null) {
            metricsManager = RuntimeHealthFactory.createMetricsManager(configuration);
            lifecycleRegistry.registerShutdown("metricsManager", metricsManager);
        }
        return metricsManager;
    }

    public synchronized org.airsonic.player.health.HealthContributorRegistry createHealthContributorRegistry(
            DataSource dataSource) {
        if (healthContributorRegistry == null) {
            healthContributorRegistry = RuntimeHealthFactory.createHealthContributorRegistry(dataSource);
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
            mediaScannerService = RuntimeScannerFactory.createMediaScannerService(
                    playlistService, settingsService, indexManager, mediaFileService, mediaFolderService,
                    coverArtService, daos.getMediaFileDao(), daos.getArtistDao(), daos.getAlbumDao(),
                    createTaskSchedulingService(), scannerParallelism);
            mediaScannerService.init();
        }
        return mediaScannerService;
    }

    public synchronized org.airsonic.player.service.search.AnalyzerFactory createAnalyzerFactory() {
        if (analyzerFactory == null) {
            analyzerFactory = RuntimeSearchServiceFactory.createAnalyzerFactory();
        }
        return analyzerFactory;
    }

    public synchronized org.airsonic.player.service.search.DocumentFactory createDocumentFactory() {
        if (documentFactory == null) {
            documentFactory = RuntimeSearchServiceFactory.createDocumentFactory();
        }
        return documentFactory;
    }

    public synchronized org.airsonic.player.service.search.IndexManager createIndexManager() {
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

    public synchronized org.airsonic.player.service.search.QueryFactory createQueryFactory() {
        if (queryFactory == null) {
            queryFactory = RuntimeSearchServiceFactory.createQueryFactory(createAnalyzerFactory());
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
            searchServiceUtilities = RuntimeSearchServiceFactory.createSearchServiceUtilities(
                    daos.getArtistDao(), daos.getAlbumDao(), mediaFileService);
        }
        return searchServiceUtilities;
    }

    public synchronized org.airsonic.player.service.search.SearchServiceImpl createSearchService(
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (searchService == null) {
            searchService = RuntimeSearchServiceFactory.createSearchService(
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

    public synchronized org.airsonic.player.service.SettingsService createSettingsService() {
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

    public synchronized org.airsonic.player.service.GeneralSettingsAdministrationService
            createGeneralSettingsAdministrationService(org.airsonic.player.service.SettingsService settingsService,
                    org.airsonic.player.service.PlaylistService playlistService) {
        if (generalSettingsAdministrationService == null) {
            generalSettingsAdministrationService = RuntimeSettingsServiceFactory.createGeneralSettingsAdministrationService(
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
            musicFolderAdministrationService = RuntimeSettingsServiceFactory.createMusicFolderAdministrationService(
                    settingsService, mediaScannerService, mediaFolderService, artistDao, albumDao, mediaFileDao,
                    indexManager, playlistService, coverArtService);
        }
        return musicFolderAdministrationService;
    }

    public synchronized org.airsonic.player.service.CoverArtAccentColorBackfill createCoverArtAccentColorBackfill(
            org.airsonic.player.service.CoverArtService coverArtService) {
        if (coverArtAccentColorBackfill == null) {
            coverArtAccentColorBackfill = RuntimeSettingsServiceFactory.createCoverArtAccentColorBackfill(coverArtService);
            lifecycleRegistry.registerStartup("coverArtAccentColorBackfill",
                    coverArtAccentColorBackfill::backfillAccentColors);
        }
        return coverArtAccentColorBackfill;
    }

    public synchronized org.airsonic.player.service.UPnPService createUpnpService(
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.upnp.CustomContentDirectory contentDirectory) {
        if (upnpService == null) {
            upnpService = RuntimeUpnpFactory.createUpnpService(settingsService, contentDirectory);
            upnpService.init();
            lifecycleRegistry.registerShutdown("upnpService", upnpService);
        }
        return upnpService;
    }

    public synchronized org.airsonic.player.service.upnp.AlbumUpnpProcessor createAlbumUpnpProcessor(
            org.airsonic.player.dao.AlbumDao albumDao) {
        if (albumUpnpProcessor == null) {
            albumUpnpProcessor = RuntimeUpnpFactory.createAlbumProcessor(albumDao);
        }
        return albumUpnpProcessor;
    }

    public synchronized org.airsonic.player.service.upnp.RecentAlbumUpnpProcessor createRecentAlbumUpnpProcessor(
            org.airsonic.player.dao.AlbumDao albumDao) {
        if (recentAlbumUpnpProcessor == null) {
            recentAlbumUpnpProcessor = RuntimeUpnpFactory.createRecentAlbumProcessor(albumDao);
        }
        return recentAlbumUpnpProcessor;
    }

    public synchronized org.airsonic.player.service.upnp.ArtistUpnpProcessor createArtistUpnpProcessor(
            org.airsonic.player.dao.ArtistDao artistDao) {
        if (artistUpnpProcessor == null) {
            artistUpnpProcessor = RuntimeUpnpFactory.createArtistProcessor(artistDao);
        }
        return artistUpnpProcessor;
    }

    public synchronized org.airsonic.player.service.upnp.GenreUpnpProcessor createGenreUpnpProcessor() {
        if (genreUpnpProcessor == null) {
            genreUpnpProcessor = RuntimeUpnpFactory.createGenreProcessor();
        }
        return genreUpnpProcessor;
    }

    public synchronized org.airsonic.player.service.upnp.MediaFileUpnpProcessor createMediaFileUpnpProcessor(
            org.airsonic.player.dao.MediaFileDao mediaFileDao) {
        if (mediaFileUpnpProcessor == null) {
            mediaFileUpnpProcessor = RuntimeUpnpFactory.createMediaFileProcessor(mediaFileDao);
        }
        return mediaFileUpnpProcessor;
    }

    public synchronized org.airsonic.player.service.upnp.PlaylistUpnpProcessor createPlaylistUpnpProcessor(
            org.airsonic.player.service.PlaylistService playlistService) {
        if (playlistUpnpProcessor == null) {
            playlistUpnpProcessor = RuntimeUpnpFactory.createPlaylistProcessor(playlistService);
        }
        return playlistUpnpProcessor;
    }

    public synchronized org.airsonic.player.service.upnp.RootUpnpProcessor createRootUpnpProcessor(
            org.airsonic.player.service.search.IndexManager indexManager) {
        if (rootUpnpProcessor == null) {
            rootUpnpProcessor = RuntimeUpnpFactory.createRootProcessor(indexManager);
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

    public synchronized org.airsonic.player.service.MediaFolderService createMediaFolderService() {
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

    public synchronized org.airsonic.player.service.MediaFileService createMediaFileService(
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.dao.MediaFileDao mediaFileDao,
            org.airsonic.player.dao.AlbumDao albumDao,
            org.airsonic.player.service.metadata.JaudiotaggerParser parser,
            org.airsonic.player.service.metadata.MetaDataParserFactory metaDataParserFactory,
            org.airsonic.player.service.CoverArtService coverArtService) {
        if (mediaFileService == null) {
            mediaFileService = RuntimeMediaServiceFactory.createMediaFileService(
                    securityService, settingsService, mediaFileDao, albumDao, createMediaFolderService(),
                    parser, metaDataParserFactory, coverArtService, createCacheManager());
        }
        return mediaFileService;
    }

    public synchronized org.airsonic.player.service.metadata.DefaultMetaDataParser createDefaultMetaDataParser() {
        if (defaultMetaDataParser == null) {
            defaultMetaDataParser = RuntimeMediaServiceFactory.createDefaultMetaDataParser(createMediaFolderService());
        }
        return defaultMetaDataParser;
    }

    public synchronized org.airsonic.player.service.metadata.FFmpegParser createFFmpegParser() {
        if (ffmpegParser == null) {
            ffmpegParser = RuntimeMediaServiceFactory.createFFmpegParser(createMediaFolderService());
        }
        return ffmpegParser;
    }

    public synchronized org.airsonic.player.service.metadata.JaudiotaggerParser createJaudiotaggerParser() {
        if (jaudiotaggerParser == null) {
            jaudiotaggerParser = RuntimeMediaServiceFactory.createJaudiotaggerParser(createMediaFolderService());
        }
        return jaudiotaggerParser;
    }

    public synchronized org.airsonic.player.service.metadata.MetaDataParserFactory createMetaDataParserFactory() {
        if (metaDataParserFactory == null) {
            metaDataParserFactory = RuntimeMediaServiceFactory.createMetaDataParserFactory(
                    createJaudiotaggerParser(), createFFmpegParser(), createDefaultMetaDataParser());
        }
        return metaDataParserFactory;
    }

    public synchronized org.airsonic.player.service.CoverArtService createCoverArtService(
            org.airsonic.player.dao.CoverArtDao coverArtDao) {
        if (coverArtService == null) {
            coverArtService = RuntimeMediaServiceFactory.createCoverArtService(
                    coverArtDao, createMediaFolderService(), createJaudiotaggerParser(), createCacheManager());
        }
        return coverArtService;
    }

    public synchronized org.airsonic.player.service.TagEditService createTagEditService(
            org.airsonic.player.service.metadata.MetaDataParserFactory metaDataParserFactory,
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.SecurityService securityService) {
        if (tagEditService == null) {
            tagEditService = RuntimeMediaServiceFactory.createTagEditService(
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
            downloadService = RuntimeMediaServiceFactory.createDownloadService(statusService, securityService,
                    playlistService, settingsService, mediaFileService, createMediaFolderService(), coverArtService);
        }
        return downloadService;
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
            coverArtImageService = RuntimeMediaServiceFactory.createCoverArtImageService(
                    mediaFileService, coverArtService, transcodingService, settingsService, playlistService,
                    artistDao, albumDao, createJaudiotaggerParser());
            coverArtImageService.init();
        }
        return coverArtImageService;
    }

    public synchronized org.airsonic.player.service.playlist.DefaultPlaylistExportHandler createDefaultPlaylistExportHandler(
            org.airsonic.player.dao.MediaFileDao mediaFileDao) {
        if (defaultPlaylistExportHandler == null) {
            defaultPlaylistExportHandler = RuntimePlaylistServiceFactory.createDefaultExportHandler(
                    mediaFileDao, createMediaFolderService());
        }
        return defaultPlaylistExportHandler;
    }

    public synchronized org.airsonic.player.service.playlist.DefaultPlaylistImportHandler createDefaultPlaylistImportHandler(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.SettingsService settingsService) {
        if (defaultPlaylistImportHandler == null) {
            defaultPlaylistImportHandler = RuntimePlaylistServiceFactory.createDefaultImportHandler(
                    mediaFileService, settingsService);
        }
        return defaultPlaylistImportHandler;
    }

    public synchronized org.airsonic.player.service.playlist.XspfPlaylistExportHandler createXspfPlaylistExportHandler(
            org.airsonic.player.dao.MediaFileDao mediaFileDao,
            org.airsonic.player.dao.PlaylistDao playlistDao,
            org.airsonic.player.service.CoverArtService coverArtService) {
        if (xspfPlaylistExportHandler == null) {
            xspfPlaylistExportHandler = RuntimePlaylistServiceFactory.createXspfExportHandler(
                    mediaFileDao, playlistDao, createMediaFolderService(), coverArtService);
        }
        return xspfPlaylistExportHandler;
    }

    public synchronized org.airsonic.player.service.playlist.XspfPlaylistImportHandler createXspfPlaylistImportHandler(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.SettingsService settingsService) {
        if (xspfPlaylistImportHandler == null) {
            xspfPlaylistImportHandler = RuntimePlaylistServiceFactory.createXspfImportHandler(
                    mediaFileService, settingsService);
        }
        return xspfPlaylistImportHandler;
    }

    public synchronized org.airsonic.player.service.PlaylistService createPlaylistService(
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.CoverArtService coverArtService) throws Exception {
        if (playlistService == null) {
            if (persistenceGraph == null) {
                throw new IllegalStateException("Persistence graph must be created before PlaylistService");
            }
            DaoGraph daos = persistenceGraph.getDaos();
            java.util.List<org.airsonic.player.service.playlist.PlaylistExportHandler> exportHandlers = java.util.List.of(
                    createDefaultPlaylistExportHandler(daos.getMediaFileDao()),
                    createXspfPlaylistExportHandler(daos.getMediaFileDao(), daos.getPlaylistDao(), coverArtService));
            java.util.List<org.airsonic.player.service.playlist.PlaylistImportHandler> importHandlers = java.util.List.of(
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
            serverInfoService = RuntimeApplicationServiceFactory.createServerInfoService(versions, settingsService);
        }
        return serverInfoService;
    }

    public synchronized org.airsonic.player.service.StatusService createStatusService(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.TaskSchedulingService taskSchedulingService) {
        if (statusService == null) {
            statusService = RuntimeApplicationServiceFactory.createStatusService(
                    mediaFileService, mediaFolderService, settingsService, taskSchedulingService);
            statusService.cleanup();
        }
        return statusService;
    }

    public synchronized org.airsonic.player.service.LyricsService createLyricsService() {
        if (lyricsService == null) {
            lyricsService = RuntimeApplicationServiceFactory.createLyricsService();
        }
        return lyricsService;
    }

    public synchronized org.airsonic.player.service.BookmarkService createBookmarkService(
            org.airsonic.player.dao.BookmarkDao bookmarkDao,
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (bookmarkService == null) {
            bookmarkService = RuntimeApplicationServiceFactory.createBookmarkService(bookmarkDao, mediaFileService);
        }
        return bookmarkService;
    }

    public synchronized org.airsonic.player.service.RatingService createRatingService(
            org.airsonic.player.dao.RatingDao ratingDao,
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (ratingService == null) {
            ratingService = RuntimeApplicationServiceFactory.createRatingService(ratingDao, securityService, mediaFileService);
        }
        return ratingService;
    }

    public synchronized org.airsonic.player.service.MusicIndexService createMusicIndexService(
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (musicIndexService == null) {
            musicIndexService = RuntimeApplicationServiceFactory.createMusicIndexService(settingsService, mediaFileService);
        }
        return musicIndexService;
    }

    public synchronized org.airsonic.player.service.SecurityService createSecurityService(
            org.airsonic.player.dao.UserDao userDao,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.security.CredentialEncoderRegistry credentialEncoders) {
        if (securityService == null) {
            securityService = RuntimeSecurityServiceFactory.createSecurityService(
                    userDao, settingsService, mediaFolderService, createCacheManager(), credentialEncoders);
        }
        return securityService;
    }

    public synchronized org.airsonic.player.security.SubsonicCredentialAuthenticationService
            createSubsonicCredentialAuthenticationService(
                    org.airsonic.player.service.SecurityService securityService,
                    org.airsonic.player.security.CredentialEncoderRegistry credentialEncoders) {
        if (subsonicCredentialAuthenticationService == null) {
            subsonicCredentialAuthenticationService =
                    RuntimeSecurityServiceFactory.createCredentialAuthenticationService(
                            securityService, credentialEncoders);
        }
        return subsonicCredentialAuthenticationService;
    }

    public synchronized org.airsonic.player.service.JWTSecurityService createJwtSecurityService(
            org.airsonic.player.service.SettingsService settingsService) {
        if (jwtSecurityService == null) {
            jwtSecurityService = RuntimeSecurityServiceFactory.createJwtSecurityService(settingsService);
        }
        return jwtSecurityService;
    }

    public synchronized org.airsonic.player.service.DatabaseService createDatabaseService(
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.dao.DatabaseDao databaseDao,
            org.airsonic.player.service.TaskSchedulingService taskSchedulingService) {
        if (databaseService == null) {
            databaseService = RuntimeApplicationServiceFactory.createDatabaseService(
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
            jukeboxLegacySubsonicService = RuntimePlaybackServiceFactory.createLegacyJukeboxService(
                    transcodingService, audioScrobblerService, statusService, settingsService,
                    securityService, mediaFileService);
        }
        return jukeboxLegacySubsonicService;
    }

    public synchronized org.airsonic.player.service.JukeboxService createJukeboxService(
            org.airsonic.player.service.JukeboxLegacySubsonicService legacyJukebox) {
        if (jukeboxService == null) {
            jukeboxService = RuntimePlaybackServiceFactory.createJukeboxService(legacyJukebox);
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
            playQueueService = RuntimePlaybackServiceFactory.createPlayQueueService(
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
            lastFmService = RuntimeApplicationServiceFactory.createLastFmService(mediaFileDao, mediaFileService, artistDao);
            lastFmService.init();
        }
        return lastFmService;
    }

    public synchronized org.airsonic.player.service.ShareService createShareService(
            org.airsonic.player.dao.ShareDao shareDao,
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (shareService == null) {
            shareService = RuntimeApplicationServiceFactory.createShareService(shareDao, mediaFileService);
        }
        return shareService;
    }

    public synchronized org.airsonic.player.service.AudioScrobblerService createAudioScrobblerService(
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.SecurityService securityService) {
        if (audioScrobblerService == null) {
            audioScrobblerService = RuntimeApplicationServiceFactory.createAudioScrobblerService(
                    settingsService, securityService);
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
            captionService = RuntimeApplicationServiceFactory.createCaptionService(
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
            videoPlaybackService = RuntimeApplicationServiceFactory.createVideoPlaybackService(
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
            transcodingService = RuntimePlaybackServiceFactory.createTranscodingService(
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
            playerService = RuntimePlaybackServiceFactory.createPlayerService(
                    playerDao, statusService, securityService, transcodingService);
            playerService.init();
        }
        return playerService;
    }
}
