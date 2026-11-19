package org.airsonic.player.spring

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.airsonic.player.runtime.RuntimeDataSourceFactory
import org.airsonic.player.runtime.RuntimeLiquibaseMigrator

/** Spring bootstrap adapter for the explicit Ktor bean factories. */
@Configuration
class KtorSpringConfigurationAdapter : KtorConfiguration() {
    @Bean
    fun dataSource(runtime: org.airsonic.player.runtime.RuntimeCompositionRoot): javax.sql.DataSource =
        RuntimeDataSourceFactory.create(runtime.configuration).also { dataSource ->
            try {
                RuntimeLiquibaseMigrator.update(runtime.configuration, dataSource)
            } catch (failure: Throwable) {
                (dataSource as? AutoCloseable)?.close()
                throw failure
            }
            if (dataSource is AutoCloseable) {
                runtime.lifecycleRegistry.registerShutdown("dataSource", dataSource)
            }
        }

    @Bean
    fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()

    @Bean
    fun jCacheCacheManager(runtime: org.airsonic.player.runtime.RuntimeCompositionRoot): javax.cache.CacheManager =
        runtime.createCacheManager()

    @Bean(destroyMethod = "close")
    fun metricsManager(runtime: org.airsonic.player.runtime.RuntimeCompositionRoot): org.airsonic.player.monitor.MetricsManager =
        runtime.createMetricsManager()

    @Bean
    fun serverHealthContributors(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        dataSource: javax.sql.DataSource,
    ): org.airsonic.player.health.HealthContributorRegistry = runtime.createHealthContributorRegistry(dataSource)

    @Bean
    override fun persistenceGraph(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        dataSource: javax.sql.DataSource,
    ): org.airsonic.player.runtime.PersistenceGraph = runtime.createPersistenceGraph(dataSource)

    @Bean
    override fun airsonicTransactionManager(
        persistenceGraph: org.airsonic.player.runtime.PersistenceGraph,
    ): org.airsonic.player.dao.TransactionManager = persistenceGraph.transactionManager

    @Bean
    override fun jdbcHelper(persistenceGraph: org.airsonic.player.runtime.PersistenceGraph): org.airsonic.player.dao.JdbcHelper =
        persistenceGraph.jdbcHelper

    @Bean
    override fun daoGraph(persistenceGraph: org.airsonic.player.runtime.PersistenceGraph): org.airsonic.player.runtime.DaoGraph =
        persistenceGraph.daos

    // Transitional aliases remain for the existing Spring-based DAO test fixtures.
    // Runtime service factories consume DaoGraph directly and do not depend on these beans.
    @Bean fun albumDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.AlbumDao = graph.albumDao
    @Bean fun artistDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.ArtistDao = graph.artistDao
    @Bean fun mediaFileDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.MediaFileDao = graph.mediaFileDao
    @Bean fun musicFolderDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.MusicFolderDao = graph.musicFolderDao
    @Bean fun shareDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.ShareDao = graph.shareDao
    @Bean fun userDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.UserDao = graph.userDao

    @Bean
    override fun analyzerFactory(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.search.AnalyzerFactory = runtime.createAnalyzerFactory()

    @Bean
    override fun documentFactory(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.search.DocumentFactory = runtime.createDocumentFactory()

    @Bean
    override fun queryFactory(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.search.QueryFactory = runtime.createQueryFactory()

    @Bean
    override fun searchServiceUtilities(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
    ): org.airsonic.player.service.search.SearchServiceUtilities = runtime.createSearchServiceUtilities(mediaFileService)

    @Bean
    override fun indexManager(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.search.IndexManager = runtime.createIndexManager()

    @Bean
    override fun searchServiceImpl(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
    ): org.airsonic.player.service.search.SearchServiceImpl = runtime.createSearchService(mediaFileService)

    @Bean
    override fun defaultMetaDataParser(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.metadata.DefaultMetaDataParser = runtime.createDefaultMetaDataParser()

    @Bean
    override fun ffmpegParser(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.metadata.FFmpegParser = runtime.createFFmpegParser()

    @Bean
    override fun jaudiotaggerParser(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.metadata.JaudiotaggerParser = runtime.createJaudiotaggerParser()

    @Bean
    override fun metaDataParserFactory(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.metadata.MetaDataParserFactory = runtime.createMetaDataParserFactory()

    @Bean
    override fun defaultPlaylistExportHandler(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
    ): org.airsonic.player.service.playlist.DefaultPlaylistExportHandler =
        runtime.createDefaultPlaylistExportHandler(graph.mediaFileDao)

    @Bean
    override fun defaultPlaylistImportHandler(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        settingsService: org.airsonic.player.service.SettingsService,
    ): org.airsonic.player.service.playlist.DefaultPlaylistImportHandler =
        runtime.createDefaultPlaylistImportHandler(mediaFileService, settingsService)

    @Bean
    override fun xspfPlaylistExportHandler(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
        coverArtService: org.airsonic.player.service.CoverArtService,
    ): org.airsonic.player.service.playlist.XspfPlaylistExportHandler =
        runtime.createXspfPlaylistExportHandler(graph.mediaFileDao, graph.playlistDao, coverArtService)

    @Bean
    override fun xspfPlaylistImportHandler(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        settingsService: org.airsonic.player.service.SettingsService,
    ): org.airsonic.player.service.playlist.XspfPlaylistImportHandler =
        runtime.createXspfPlaylistImportHandler(mediaFileService, settingsService)

    @Bean
    override fun playlistService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        securityService: org.airsonic.player.service.SecurityService,
        settingsService: org.airsonic.player.service.SettingsService,
        exportHandlers: List<org.airsonic.player.service.playlist.PlaylistExportHandler>,
        importHandlers: List<org.airsonic.player.service.playlist.PlaylistImportHandler>,
    ): org.airsonic.player.service.PlaylistService =
        runtime.createPlaylistService(securityService, settingsService, exportHandlers, importHandlers)

    @Bean
    override fun hlsPlaylistService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        jwtSecurityService: org.airsonic.player.service.JWTSecurityService,
    ): org.airsonic.player.service.hls.HlsPlaylistService = runtime.createHlsPlaylistService(jwtSecurityService)

    @Bean
    override fun hlsSegmentService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        securityService: org.airsonic.player.service.SecurityService,
        statusService: org.airsonic.player.service.StatusService,
        settingsService: org.airsonic.player.service.SettingsService,
        transcodingService: org.airsonic.player.service.TranscodingService,
    ): org.airsonic.player.service.hls.HlsSegmentService = runtime.createHlsSegmentService(
        mediaFileService, securityService, statusService, settingsService, transcodingService)

    @Bean
    override fun coverArtService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
    ): org.airsonic.player.service.CoverArtService = runtime.createCoverArtService(graph.coverArtDao)

    @Bean
    override fun coverArtImageService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        coverArtService: org.airsonic.player.service.CoverArtService,
        transcodingService: org.airsonic.player.service.TranscodingService,
        settingsService: org.airsonic.player.service.SettingsService,
        playlistService: org.airsonic.player.service.PlaylistService,
        graph: org.airsonic.player.runtime.DaoGraph,
    ): org.airsonic.player.service.CoverArtImageService = runtime.createCoverArtImageService(
        mediaFileService, coverArtService, transcodingService, settingsService, playlistService,
        graph.artistDao, graph.albumDao)

    @Bean
    override fun coverArtAccentColorBackfill(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        coverArtService: org.airsonic.player.service.CoverArtService,
    ): org.airsonic.player.service.CoverArtAccentColorBackfill = runtime.createCoverArtAccentColorBackfill(coverArtService)

    @Bean
    override fun tagEditService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        metaDataParserFactory: org.airsonic.player.service.metadata.MetaDataParserFactory,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        securityService: org.airsonic.player.service.SecurityService,
    ): org.airsonic.player.service.TagEditService = runtime.createTagEditService(
        metaDataParserFactory, mediaFileService, securityService)

    @Bean
    override fun albumUpnpProcessor(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
    ): org.airsonic.player.service.upnp.AlbumUpnpProcessor = runtime.createAlbumUpnpProcessor(graph.albumDao)

    @Bean
    override fun recentAlbumUpnpProcessor(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
    ): org.airsonic.player.service.upnp.RecentAlbumUpnpProcessor = runtime.createRecentAlbumUpnpProcessor(graph.albumDao)

    @Bean
    override fun artistUpnpProcessor(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
    ): org.airsonic.player.service.upnp.ArtistUpnpProcessor = runtime.createArtistUpnpProcessor(graph.artistDao)

    @Bean
    override fun genreUpnpProcessor(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.upnp.GenreUpnpProcessor = runtime.createGenreUpnpProcessor()

    @Bean
    override fun mediaFileUpnpProcessor(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
    ): org.airsonic.player.service.upnp.MediaFileUpnpProcessor = runtime.createMediaFileUpnpProcessor(graph.mediaFileDao)

    @Bean
    override fun playlistUpnpProcessor(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        playlistService: org.airsonic.player.service.PlaylistService,
    ): org.airsonic.player.service.upnp.PlaylistUpnpProcessor = runtime.createPlaylistUpnpProcessor(playlistService)

    @Bean
    override fun rootUpnpProcessor(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        indexManager: org.airsonic.player.service.search.IndexManager,
    ): org.airsonic.player.service.upnp.RootUpnpProcessor = runtime.createRootUpnpProcessor(indexManager)

    @Bean
    override fun dispatchingContentDirectory(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
        playlistService: org.airsonic.player.service.PlaylistService,
        searchService: org.airsonic.player.service.SearchService,
        musicIndexService: org.airsonic.player.service.MusicIndexService,
        indexManager: org.airsonic.player.service.search.IndexManager,
        settingsService: org.airsonic.player.service.SettingsService,
        playerService: org.airsonic.player.service.PlayerService,
        transcodingService: org.airsonic.player.service.TranscodingService,
        jwtSecurityService: org.airsonic.player.service.JWTSecurityService,
    ): org.airsonic.player.service.upnp.DispatchingContentDirectory = runtime.createDispatchingContentDirectory(
        mediaFileService, mediaFolderService, playlistService, searchService, musicIndexService, indexManager,
        settingsService, playerService, transcodingService, jwtSecurityService)

    @Bean
    override fun folderBasedContentDirectory(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
        playlistService: org.airsonic.player.service.PlaylistService,
        indexManager: org.airsonic.player.service.search.IndexManager,
        settingsService: org.airsonic.player.service.SettingsService,
        playerService: org.airsonic.player.service.PlayerService,
        transcodingService: org.airsonic.player.service.TranscodingService,
        jwtSecurityService: org.airsonic.player.service.JWTSecurityService,
    ): org.airsonic.player.service.upnp.FolderBasedContentDirectory = runtime.createFolderBasedContentDirectory(
        mediaFileService, mediaFolderService, playlistService, indexManager, settingsService, playerService,
        transcodingService, jwtSecurityService)

    @Bean
    override fun upnpService(
        settingsService: org.airsonic.player.service.SettingsService,
        @org.springframework.beans.factory.annotation.Qualifier("dispatchingContentDirectory")
        contentDirectory: org.airsonic.player.service.upnp.CustomContentDirectory,
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.UPnPService = runtime.createUpnpService(settingsService, contentDirectory)

    @Bean
    override fun jwtSecurityService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        settingsService: org.airsonic.player.service.SettingsService,
    ): org.airsonic.player.service.JWTSecurityService = runtime.createJwtSecurityService(settingsService)

    @Bean
    override fun credentialEncoderRegistry(): org.airsonic.player.security.CredentialEncoderRegistry =
        org.airsonic.player.security.SpringCredentialEncoderRegistry()

    @Bean
    override fun securityService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
        settingsService: org.airsonic.player.service.SettingsService,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
        credentialEncoders: org.airsonic.player.security.CredentialEncoderRegistry,
    ): org.airsonic.player.service.SecurityService =
        runtime.createSecurityService(graph.userDao, settingsService, mediaFolderService, credentialEncoders)

    @Bean
    override fun configurationPropertiesService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.ConfigurationPropertiesService = runtime.createConfigurationPropertiesService()

    @Bean
    override fun settingsService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
        cacheManager: javax.cache.CacheManager,
        properties: org.airsonic.player.service.ConfigurationPropertiesService,
    ): org.airsonic.player.service.SettingsService = runtime.createSettingsService()

    @Bean
    override fun mediaFolderService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.MediaFolderService = runtime.createMediaFolderService()

    @Bean
    override fun mediaFileService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        securityService: org.airsonic.player.service.SecurityService,
        settingsService: org.airsonic.player.service.SettingsService,
        graph: org.airsonic.player.runtime.DaoGraph,
        parser: org.airsonic.player.service.metadata.JaudiotaggerParser,
        metaDataParserFactory: org.airsonic.player.service.metadata.MetaDataParserFactory,
        coverArtService: org.airsonic.player.service.CoverArtService,
        localeService: org.airsonic.player.i18n.LocaleService,
    ): org.airsonic.player.service.MediaFileService = runtime.createMediaFileService(
        securityService, settingsService, graph.mediaFileDao, graph.albumDao, parser, metaDataParserFactory, coverArtService, localeService)

    @Bean
    override fun mediaScannerService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        playlistService: org.airsonic.player.service.PlaylistService,
        settingsService: org.airsonic.player.service.SettingsService,
        indexManager: org.airsonic.player.service.search.IndexManager,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
        coverArtService: org.airsonic.player.service.CoverArtService,
    ): org.airsonic.player.service.MediaScannerService = runtime.createMediaScannerService(
        playlistService, settingsService, indexManager, mediaFileService, mediaFolderService, coverArtService)

    @Bean
    override fun musicIndexStateService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        scanner: org.airsonic.player.service.MediaScannerService,
        settings: org.airsonic.player.service.SettingsService,
    ): org.airsonic.player.service.MusicIndexStateService = runtime.createMusicIndexStateService(scanner, settings)

    @Bean
    override fun transcodingService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
        settingsService: org.airsonic.player.service.SettingsService,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
        playerServiceProvider: org.springframework.beans.factory.ObjectProvider<org.airsonic.player.service.PlayerService>,
    ): org.airsonic.player.service.TranscodingService = runtime.createTranscodingService(
        graph.transcodingDao, settingsService, mediaFolderService, playerServiceProvider::getObject)

    @Bean
    override fun playerService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        statusService: org.airsonic.player.service.StatusService,
        securityService: org.airsonic.player.service.SecurityService,
        transcodingService: org.airsonic.player.service.TranscodingService,
    ): org.airsonic.player.service.PlayerService = runtime.createPlayerService(
        runtime.createPlayerDao(), statusService, securityService, transcodingService)

    @Bean
    override fun taskSchedulingService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.TaskSchedulingService = runtime.createTaskSchedulingService()

    @Bean
    override fun pathWatcherService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.PathWatcherService = runtime.createPathWatcherService()

    @Bean
    override fun jukeboxLegacySubsonicService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        transcodingService: org.airsonic.player.service.TranscodingService,
        audioScrobblerService: org.airsonic.player.service.AudioScrobblerService,
        statusService: org.airsonic.player.service.StatusService,
        settingsService: org.airsonic.player.service.SettingsService,
        securityService: org.airsonic.player.service.SecurityService,
        mediaFileService: org.airsonic.player.service.MediaFileService,
    ): org.airsonic.player.service.JukeboxLegacySubsonicService = runtime.createJukeboxLegacySubsonicService(
        transcodingService, audioScrobblerService, statusService, settingsService, securityService, mediaFileService)

    @Bean
    override fun jukeboxService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        legacyJukebox: org.airsonic.player.service.JukeboxLegacySubsonicService,
    ): org.airsonic.player.service.JukeboxService = runtime.createJukeboxService(legacyJukebox)

    @Bean
    override fun playQueueService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        searchService: org.airsonic.player.service.SearchService,
        jukeboxService: org.airsonic.player.service.JukeboxService,
        settingsService: org.airsonic.player.service.SettingsService,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        lastFmService: org.airsonic.player.service.LastFmService,
        ratingService: org.airsonic.player.service.RatingService,
        playlistService: org.airsonic.player.service.PlaylistService,
        graph: org.airsonic.player.runtime.DaoGraph,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
        jwtSecurityService: org.airsonic.player.service.JWTSecurityService,
    ): org.airsonic.player.service.PlayQueueService = runtime.createPlayQueueService(
        searchService, jukeboxService, settingsService, mediaFileService, lastFmService, ratingService, playlistService,
        graph.mediaFileDao, mediaFolderService, graph.playQueueDao, jwtSecurityService)

    @Bean
    override fun captionService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
        settingsService: org.airsonic.player.service.SettingsService,
        metaDataParserFactory: org.airsonic.player.service.metadata.MetaDataParserFactory,
        jwtSecurityService: org.airsonic.player.service.JWTSecurityService,
    ): org.airsonic.player.service.CaptionService = runtime.createCaptionService(
        mediaFileService, mediaFolderService, settingsService, metaDataParserFactory, jwtSecurityService)

    @Bean
    override fun videoPlaybackService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        captionService: org.airsonic.player.service.CaptionService,
        jwtSecurityService: org.airsonic.player.service.JWTSecurityService,
        bookmarkService: org.airsonic.player.service.BookmarkService,
        settingsService: org.airsonic.player.service.SettingsService,
    ): org.airsonic.player.service.VideoPlaybackService = runtime.createVideoPlaybackService(
        mediaFileService, captionService, jwtSecurityService, bookmarkService, settingsService)

    @Bean
    override fun mediaStreamService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        statusService: org.airsonic.player.service.StatusService,
        securityService: org.airsonic.player.service.SecurityService,
        settingsService: org.airsonic.player.service.SettingsService,
        transcodingService: org.airsonic.player.service.TranscodingService,
        audioScrobblerService: org.airsonic.player.service.AudioScrobblerService,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        searchService: org.airsonic.player.service.SearchService,
    ): org.airsonic.player.service.MediaStreamService = runtime.createMediaStreamService(
        statusService, securityService, settingsService, transcodingService, audioScrobblerService, mediaFileService, searchService)

    @Bean
    override fun lastFmService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
        mediaFileService: org.airsonic.player.service.MediaFileService,
    ): org.airsonic.player.service.LastFmService = runtime.createLastFmService(
        graph.mediaFileDao, mediaFileService, graph.artistDao)

    @Bean
    override fun shareService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        jwtSecurityService: org.airsonic.player.service.JWTSecurityService,
    ): org.airsonic.player.service.ShareService = runtime.createShareService(
        graph.shareDao, mediaFileService, jwtSecurityService)

    @Bean
    override fun uploadFileService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        securityService: org.airsonic.player.service.SecurityService,
    ): org.airsonic.player.service.UploadFileService = runtime.createUploadFileService(securityService)

    @Bean
    override fun downloadService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        statusService: org.airsonic.player.service.StatusService,
        securityService: org.airsonic.player.service.SecurityService,
        playlistService: org.airsonic.player.service.PlaylistService,
        settingsService: org.airsonic.player.service.SettingsService,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        coverArtService: org.airsonic.player.service.CoverArtService,
    ): org.airsonic.player.service.DownloadService = runtime.createDownloadService(
        statusService, securityService, playlistService, settingsService, mediaFileService, coverArtService)

    @Bean
    override fun userAdministrationService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        securityService: org.airsonic.player.service.SecurityService,
        settingsService: org.airsonic.player.service.SettingsService,
    ): org.airsonic.player.service.UserAdministrationService =
        runtime.createUserAdministrationService(securityService, settingsService)

    @Bean
    override fun versionService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.VersionService = runtime.createVersionService()

    @Bean
    override fun serverInfoService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        versionService: org.airsonic.player.service.VersionService,
        settingsService: org.airsonic.player.service.SettingsService,
    ): org.airsonic.player.service.ServerInfoService = runtime.createServerInfoService(versionService, settingsService)

    @Bean
    override fun localeService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        settingsService: org.airsonic.player.service.SettingsService,
    ): org.airsonic.player.i18n.LocaleService = runtime.createLocaleService(settingsService)

    @Bean
    override fun statusService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
        settingsService: org.airsonic.player.service.SettingsService,
        taskSchedulingService: org.airsonic.player.service.TaskSchedulingService,
    ): org.airsonic.player.service.StatusService = runtime.createStatusService(
        mediaFileService, mediaFolderService, settingsService, taskSchedulingService)

    @Bean
    override fun passwordRecoveryService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        settingsService: org.airsonic.player.service.SettingsService,
        securityService: org.airsonic.player.service.SecurityService,
    ): org.airsonic.player.service.PasswordRecoveryService =
        runtime.createPasswordRecoveryService(settingsService, securityService)

    @Bean
    override fun audioScrobblerService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        settingsService: org.airsonic.player.service.SettingsService,
        securityService: org.airsonic.player.service.SecurityService,
    ): org.airsonic.player.service.AudioScrobblerService =
        runtime.createAudioScrobblerService(settingsService, securityService)

    @Bean
    override fun bookmarkService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
        mediaFileService: org.airsonic.player.service.MediaFileService,
    ): org.airsonic.player.service.BookmarkService = runtime.createBookmarkService(graph.bookmarkDao, mediaFileService)

    @Bean
    override fun lyricsService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.LyricsService = runtime.createLyricsService()

    @Bean
    override fun ratingService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
        securityService: org.airsonic.player.service.SecurityService,
        mediaFileService: org.airsonic.player.service.MediaFileService,
    ): org.airsonic.player.service.RatingService = runtime.createRatingService(graph.ratingDao, securityService, mediaFileService)

    @Bean
    override fun musicIndexService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        settingsService: org.airsonic.player.service.SettingsService,
        mediaFileService: org.airsonic.player.service.MediaFileService,
    ): org.airsonic.player.service.MusicIndexService = runtime.createMusicIndexService(settingsService, mediaFileService)

    @Bean
    override fun databaseService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        settingsService: org.airsonic.player.service.SettingsService,
        graph: org.airsonic.player.runtime.DaoGraph,
        taskSchedulingService: org.airsonic.player.service.TaskSchedulingService,
    ): org.airsonic.player.service.DatabaseService =
        runtime.createDatabaseService(settingsService, graph.databaseDao, taskSchedulingService)
}
