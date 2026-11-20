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
    fun upnpService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        settingsService: org.airsonic.player.service.SettingsService,
        securityService: org.airsonic.player.service.SecurityService,
    ): org.airsonic.player.service.UPnPService {
        val graph = runtime.createPersistenceGraph().daos
        val jwtSecurityService = runtime.createJwtSecurityService(settingsService)
        val mediaFolderService = runtime.createMediaFolderService()
        val mediaFileService = runtime.createMediaFileService(
            securityService, settingsService, graph.mediaFileDao, graph.albumDao, runtime.createJaudiotaggerParser(),
            runtime.createMetaDataParserFactory(), runtime.createCoverArtService(graph.coverArtDao),
            runtime.createLocaleService(settingsService))
        val transcodingService = runtime.createTranscodingService(graph.transcodingDao, settingsService, mediaFolderService)
        val statusService = runtime.createStatusService(
            mediaFileService, mediaFolderService, settingsService, runtime.createTaskSchedulingService())
        return runtime.createUpnpService(
            settingsService,
            runtime.createDispatchingContentDirectory(
                mediaFileService, mediaFolderService,
                runtime.createPlaylistService(
                    securityService, settingsService, mediaFileService,
                    runtime.createCoverArtService(graph.coverArtDao)),
                runtime.createSearchService(mediaFileService),
                runtime.createMusicIndexService(settingsService, mediaFileService), runtime.createIndexManager(),
                settingsService,
                runtime.createPlayerService(
                    runtime.createPlayerDao(),
                    statusService, securityService, transcodingService),
                transcodingService, jwtSecurityService))
    }

    @Bean
    override fun credentialEncoderRegistry(
        settingsService: org.airsonic.player.service.SettingsService,
    ): org.airsonic.player.security.CredentialEncoderRegistry =
        org.airsonic.player.security.LegacyCredentialEncoderRegistry(settingsService)

    @Bean
    fun securityService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        settingsService: org.airsonic.player.service.SettingsService,
        credentialEncoders: org.airsonic.player.security.CredentialEncoderRegistry,
    ): org.airsonic.player.service.SecurityService =
        runtime.createSecurityService(
            runtime.createPersistenceGraph().daos.userDao, settingsService,
            runtime.createMediaFolderService(), credentialEncoders)

    @Bean
    fun settingsService(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.SettingsService {
        runtime.createPersistenceGraph()
        return runtime.createSettingsService()
    }

}
