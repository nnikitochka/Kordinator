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
    fun upnpService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        settingsService: org.airsonic.player.service.SettingsService,
        graph: org.airsonic.player.runtime.DaoGraph,
        mediaFileService: org.airsonic.player.service.MediaFileService,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
        securityService: org.airsonic.player.service.SecurityService,
        transcodingService: org.airsonic.player.service.TranscodingService,
        jwtSecurityService: org.airsonic.player.service.JWTSecurityService,
    ): org.airsonic.player.service.UPnPService = runtime.createUpnpService(
        settingsService,
        runtime.createDispatchingContentDirectory(
            mediaFileService, mediaFolderService,
            runtime.createPlaylistService(
                securityService, settingsService, mediaFileService, runtime.createCoverArtService(graph.coverArtDao)),
            runtime.createSearchService(mediaFileService),
            runtime.createMusicIndexService(settingsService, mediaFileService), runtime.createIndexManager(),
            settingsService,
            runtime.createPlayerService(
                runtime.createPlayerDao(),
                runtime.createStatusService(mediaFileService, mediaFolderService, settingsService,
                    runtime.createTaskSchedulingService()),
                securityService, transcodingService),
            transcodingService, jwtSecurityService))

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
    fun mediaFileService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        securityService: org.airsonic.player.service.SecurityService,
        settingsService: org.airsonic.player.service.SettingsService,
        graph: org.airsonic.player.runtime.DaoGraph,
    ): org.airsonic.player.service.MediaFileService = runtime.createMediaFileService(
        securityService, settingsService, graph.mediaFileDao, graph.albumDao, runtime.createJaudiotaggerParser(),
        runtime.createMetaDataParserFactory(), runtime.createCoverArtService(graph.coverArtDao),
        runtime.createLocaleService(settingsService))

    @Bean
    override fun transcodingService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        graph: org.airsonic.player.runtime.DaoGraph,
        settingsService: org.airsonic.player.service.SettingsService,
        mediaFolderService: org.airsonic.player.service.MediaFolderService,
    ): org.airsonic.player.service.TranscodingService = runtime.createTranscodingService(
        graph.transcodingDao, settingsService, mediaFolderService)

}
