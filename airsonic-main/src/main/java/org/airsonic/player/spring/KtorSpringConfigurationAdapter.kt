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
        statusService: org.airsonic.player.service.StatusService,
        securityService: org.airsonic.player.service.SecurityService,
        transcodingService: org.airsonic.player.service.TranscodingService,
        jwtSecurityService: org.airsonic.player.service.JWTSecurityService,
    ): org.airsonic.player.service.UPnPService = runtime.createUpnpService(
        settingsService,
        runtime.createDispatchingContentDirectory(
            mediaFileService, mediaFolderService, playlistService, searchService,
            runtime.createMusicIndexService(settingsService, mediaFileService), runtime.createIndexManager(),
            settingsService, runtime.createPlayerService(runtime.createPlayerDao(), statusService, securityService,
                transcodingService), transcodingService, jwtSecurityService))

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
    ): org.airsonic.player.service.MediaFileService = runtime.createMediaFileService(
        securityService, settingsService, graph.mediaFileDao, graph.albumDao, runtime.createJaudiotaggerParser(),
        runtime.createMetaDataParserFactory(), coverArtService, runtime.createLocaleService(settingsService))

    @Bean
    override fun transcodingService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
        settingsService: org.airsonic.player.service.SettingsService,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
    ): org.airsonic.player.service.TranscodingService = runtime.createTranscodingService(
        graph.transcodingDao, settingsService, mediaFolderService)

    @Bean
    override fun taskSchedulingService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.TaskSchedulingService = runtime.createTaskSchedulingService()

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
    override fun statusService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
        settingsService: org.airsonic.player.service.SettingsService,
        taskSchedulingService: org.airsonic.player.service.TaskSchedulingService,
    ): org.airsonic.player.service.StatusService = runtime.createStatusService(
        mediaFileService, mediaFolderService, settingsService, taskSchedulingService)

}
