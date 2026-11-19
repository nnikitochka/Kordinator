package org.airsonic.player.spring

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean

/** Spring bootstrap adapter for the explicit Ktor bean factories. */
@Configuration
class KtorSpringConfigurationAdapter : KtorConfiguration() {
    @Bean
    fun dataSource(runtime: org.airsonic.player.runtime.RuntimeCompositionRoot): javax.sql.DataSource =
        runtime.createDataSource().also { runtime.migrateDatabase() }

    @Bean
    override fun daoGraph(runtime: org.airsonic.player.runtime.RuntimeCompositionRoot): org.airsonic.player.runtime.DaoGraph =
        runtime.createPersistenceGraph().daos

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
    override fun playlistService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        securityService: org.airsonic.player.service.SecurityService,
        settingsService: org.airsonic.player.service.SettingsService,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        coverArtService: org.airsonic.player.service.CoverArtService,
    ): org.airsonic.player.service.PlaylistService =
        runtime.createPlaylistService(securityService, settingsService, mediaFileService, coverArtService)

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
    override fun tagEditService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        securityService: org.airsonic.player.service.SecurityService,
    ): org.airsonic.player.service.TagEditService = runtime.createTagEditService(
        runtime.createMetaDataParserFactory(), mediaFileService, securityService)

    @Bean
    fun upnpService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        settingsService: org.airsonic.player.service.SettingsService,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
        playlistService: org.airsonic.player.service.PlaylistService,
        searchService: org.airsonic.player.service.SearchService,
        musicIndexService: org.airsonic.player.service.MusicIndexService,
        indexManager: org.airsonic.player.service.search.IndexManager,
        playerService: org.airsonic.player.service.PlayerService,
        transcodingService: org.airsonic.player.service.TranscodingService,
        jwtSecurityService: org.airsonic.player.service.JWTSecurityService,
    ): org.airsonic.player.service.UPnPService = runtime.createUpnpService(
        settingsService,
        runtime.createDispatchingContentDirectory(
            mediaFileService, mediaFolderService, playlistService, searchService, musicIndexService, indexManager,
            settingsService, playerService, transcodingService, jwtSecurityService))

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
    override fun settingsService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
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
        coverArtService: org.airsonic.player.service.CoverArtService,
        localeService: org.airsonic.player.i18n.LocaleService,
    ): org.airsonic.player.service.MediaFileService = runtime.createMediaFileService(
        securityService, settingsService, graph.mediaFileDao, graph.albumDao, runtime.createJaudiotaggerParser(),
        runtime.createMetaDataParserFactory(), coverArtService, localeService)

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
    ): org.airsonic.player.service.TranscodingService = runtime.createTranscodingService(
        graph.transcodingDao, settingsService, mediaFolderService)

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
