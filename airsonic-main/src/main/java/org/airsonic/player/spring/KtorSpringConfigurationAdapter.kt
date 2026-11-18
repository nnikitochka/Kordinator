package org.airsonic.player.spring

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean

/** Spring bootstrap adapter for the explicit Ktor bean factories. */
@Configuration
class KtorSpringConfigurationAdapter : KtorConfiguration() {
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

    @Bean override fun albumDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.AlbumDao = graph.albumDao
    @Bean override fun artistDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.ArtistDao = graph.artistDao
    @Bean override fun avatarDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.AvatarDao = graph.avatarDao
    @Bean override fun bookmarkDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.BookmarkDao = graph.bookmarkDao
    @Bean override fun coverArtDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.CoverArtDao = graph.coverArtDao
    @Bean override fun databaseDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.DatabaseDao = graph.databaseDao
    @Bean override fun mediaFileDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.MediaFileDao = graph.mediaFileDao
    @Bean override fun musicFolderDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.MusicFolderDao = graph.musicFolderDao
    @Bean override fun playQueueDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.PlayQueueDao = graph.playQueueDao
    @Bean override fun playlistDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.PlaylistDao = graph.playlistDao
    @Bean("musicFileInfoDao") override fun ratingDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.RatingDao = graph.ratingDao
    @Bean override fun shareDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.ShareDao = graph.shareDao
    @Bean override fun transcodingDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.TranscodingDao = graph.transcodingDao
    @Bean override fun userDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.UserDao = graph.userDao

    @Bean
    override fun playerDao(runtime: org.airsonic.player.runtime.RuntimeCompositionRoot): org.airsonic.player.dao.PlayerDao =
        runtime.createPlayerDao()

    @Bean
    override fun playerDaoPlayQueueFactory(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.dao.PlayerDaoPlayQueueFactory = runtime.createPlayerDaoPlayQueueFactory()

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
        mediaFileDao: org.airsonic.player.dao.MediaFileDao,
    ): org.airsonic.player.service.playlist.DefaultPlaylistExportHandler =
        runtime.createDefaultPlaylistExportHandler(mediaFileDao)

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
        mediaFileDao: org.airsonic.player.dao.MediaFileDao,
        playlistDao: org.airsonic.player.dao.PlaylistDao,
        coverArtService: org.airsonic.player.service.CoverArtService,
    ): org.airsonic.player.service.playlist.XspfPlaylistExportHandler =
        runtime.createXspfPlaylistExportHandler(mediaFileDao, playlistDao, coverArtService)

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
        coverArtDao: org.airsonic.player.dao.CoverArtDao,
    ): org.airsonic.player.service.CoverArtService = runtime.createCoverArtService(coverArtDao)

    @Bean
    override fun coverArtImageService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        coverArtService: org.airsonic.player.service.CoverArtService,
        transcodingService: org.airsonic.player.service.TranscodingService,
        settingsService: org.airsonic.player.service.SettingsService,
        playlistService: org.airsonic.player.service.PlaylistService,
        artistDao: org.airsonic.player.dao.ArtistDao,
        albumDao: org.airsonic.player.dao.AlbumDao,
    ): org.airsonic.player.service.CoverArtImageService = runtime.createCoverArtImageService(
        mediaFileService, coverArtService, transcodingService, settingsService, playlistService, artistDao, albumDao)

    @Bean
    override fun coverArtSearchService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        securityService: org.airsonic.player.service.SecurityService,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        lastFmService: org.airsonic.player.service.LastFmService,
        coverArtService: org.airsonic.player.service.CoverArtService,
    ): org.airsonic.player.service.CoverArtSearchService = runtime.createCoverArtSearchService(
        securityService, mediaFileService, lastFmService, coverArtService)

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
        albumDao: org.airsonic.player.dao.AlbumDao,
    ): org.airsonic.player.service.upnp.AlbumUpnpProcessor = runtime.createAlbumUpnpProcessor(albumDao)

    @Bean
    override fun recentAlbumUpnpProcessor(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        albumDao: org.airsonic.player.dao.AlbumDao,
    ): org.airsonic.player.service.upnp.RecentAlbumUpnpProcessor = runtime.createRecentAlbumUpnpProcessor(albumDao)

    @Bean
    override fun artistUpnpProcessor(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        artistDao: org.airsonic.player.dao.ArtistDao,
    ): org.airsonic.player.service.upnp.ArtistUpnpProcessor = runtime.createArtistUpnpProcessor(artistDao)

    @Bean
    override fun genreUpnpProcessor(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.upnp.GenreUpnpProcessor = runtime.createGenreUpnpProcessor()

    @Bean
    override fun mediaFileUpnpProcessor(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileDao: org.airsonic.player.dao.MediaFileDao,
    ): org.airsonic.player.service.upnp.MediaFileUpnpProcessor = runtime.createMediaFileUpnpProcessor(mediaFileDao)

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
        userDao: org.airsonic.player.dao.UserDao,
        settingsService: org.airsonic.player.service.SettingsService,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
        credentialEncoders: org.airsonic.player.security.CredentialEncoderRegistry,
    ): org.airsonic.player.service.SecurityService =
        runtime.createSecurityService(userDao, settingsService, mediaFolderService, credentialEncoders)

    @Bean
    override fun configurationPropertiesService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.ConfigurationPropertiesService = runtime.createConfigurationPropertiesService()

    @Bean
    override fun settingsService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        userDao: org.airsonic.player.dao.UserDao,
        avatarDao: org.airsonic.player.dao.AvatarDao,
        cacheManager: javax.cache.CacheManager,
        properties: org.airsonic.player.service.ConfigurationPropertiesService,
    ): org.airsonic.player.service.SettingsService = runtime.createSettingsService()

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
        bookmarkDao: org.airsonic.player.dao.BookmarkDao,
        mediaFileService: org.airsonic.player.service.MediaFileService,
    ): org.airsonic.player.service.BookmarkService = runtime.createBookmarkService(bookmarkDao, mediaFileService)

    @Bean
    override fun lyricsService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.LyricsService = runtime.createLyricsService()

    @Bean
    override fun ratingService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        ratingDao: org.airsonic.player.dao.RatingDao,
        securityService: org.airsonic.player.service.SecurityService,
        mediaFileService: org.airsonic.player.service.MediaFileService,
    ): org.airsonic.player.service.RatingService = runtime.createRatingService(ratingDao, securityService, mediaFileService)

    @Bean
    override fun mediaDirectoryService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        ratingService: org.airsonic.player.service.RatingService,
        securityService: org.airsonic.player.service.SecurityService,
        settingsService: org.airsonic.player.service.SettingsService,
    ): org.airsonic.player.service.MediaDirectoryService = runtime.createMediaDirectoryService(
        mediaFileService, ratingService, securityService, settingsService)

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
        databaseDao: org.airsonic.player.dao.DatabaseDao,
        taskSchedulingService: org.airsonic.player.service.TaskSchedulingService,
    ): org.airsonic.player.service.DatabaseService =
        runtime.createDatabaseService(settingsService, databaseDao, taskSchedulingService)
}
