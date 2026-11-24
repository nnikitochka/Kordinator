package org.airsonic.player.runtime

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.ConfigurationPropertiesService
import org.airsonic.player.service.CoverArtAccentColorBackfill
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.GeneralSettingsAdministrationService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.MediaScannerService
import org.airsonic.player.service.MusicFolderAdministrationService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.search.IndexManager

/** Lazily assembles settings components under the shared runtime lock. */
internal class RuntimeSettingsComposition(private val context: RuntimeCompositionContext) : RuntimeSettingsComponents {
    private var configurationPropertiesService: ConfigurationPropertiesService? = null
    private var settingsService: SettingsService? = null
    private var generalSettingsAdministrationService: GeneralSettingsAdministrationService? = null
    private var musicFolderAdministrationService: MusicFolderAdministrationService? = null
    private var coverArtAccentColorBackfill: CoverArtAccentColorBackfill? = null

    override fun createConfigurationPropertiesService(): ConfigurationPropertiesService = synchronized(context.lock) {
        if (configurationPropertiesService == null) {
            configurationPropertiesService = ConfigurationPropertiesService.getInstance()
        }
        return@synchronized configurationPropertiesService!!
    }

    override fun createSettingsService(): SettingsService = synchronized(context.lock) {
        if (settingsService == null) {
            if (context.persistenceGraph == null) {
                throw IllegalStateException("Persistence graph must be created before SettingsService")
            }
            val daos = context.persistenceGraph!!.daos
            settingsService = RuntimeSettingsServiceFactory.createSettingsService(
                    daos.userDao, daos.avatarDao, context.configuration, context.root.createCacheManager(),
                    createConfigurationPropertiesService())
            settingsService!!.init()
        }
        return@synchronized settingsService!!
    }

    override fun createGeneralSettingsAdministrationService(
        settingsService: SettingsService,
        playlistService: PlaylistService,
    ): GeneralSettingsAdministrationService = synchronized(context.lock) {
        if (generalSettingsAdministrationService == null) {
            generalSettingsAdministrationService = RuntimeSettingsServiceFactory.createGeneralSettingsAdministrationService(
                    settingsService, playlistService)
        }
        return@synchronized generalSettingsAdministrationService!!
    }

    override fun createMusicFolderAdministrationService(
        settingsService: SettingsService,
        mediaScannerService: MediaScannerService,
        mediaFolderService: MediaFolderService,
        artistDao: ArtistDao,
        albumDao: AlbumDao,
        mediaFileDao: MediaFileDao,
        indexManager: IndexManager,
        playlistService: PlaylistService,
        coverArtService: CoverArtService,
    ): MusicFolderAdministrationService = synchronized(context.lock) {
        if (musicFolderAdministrationService == null) {
            musicFolderAdministrationService = RuntimeSettingsServiceFactory.createMusicFolderAdministrationService(
                    settingsService, mediaScannerService, mediaFolderService, artistDao, albumDao, mediaFileDao,
                    indexManager, playlistService, coverArtService)
        }
        return@synchronized musicFolderAdministrationService!!
    }

    override fun createCoverArtAccentColorBackfill(
        coverArtService: CoverArtService,
    ): CoverArtAccentColorBackfill = synchronized(context.lock) {
        if (coverArtAccentColorBackfill == null) {
            coverArtAccentColorBackfill = RuntimeSettingsServiceFactory.createCoverArtAccentColorBackfill(coverArtService)
            context.lifecycleRegistry.registerStartup("coverArtAccentColorBackfill",
                    { coverArtAccentColorBackfill!!.backfillAccentColors() })
        }
        return@synchronized coverArtAccentColorBackfill!!
    }
}
