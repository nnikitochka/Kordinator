package su.nnedition.kord.runtime

import su.nnedition.kord.dao.AlbumDao
import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.service.ConfigurationPropertiesService
import su.nnedition.kord.service.CoverArtAccentColorBackfill
import su.nnedition.kord.service.CoverArtService
import su.nnedition.kord.service.GeneralSettingsAdministrationService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.media.MediaScannerService
import su.nnedition.kord.service.MusicFolderAdministrationService
import su.nnedition.kord.service.PlaylistService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.search.IndexManager

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
