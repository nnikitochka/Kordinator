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

/** Creation boundary for the settings components owned by one runtime. */
interface RuntimeSettingsComponents {
    fun createConfigurationPropertiesService(): ConfigurationPropertiesService

    fun createSettingsService(): SettingsService

    fun createGeneralSettingsAdministrationService(
        settingsService: SettingsService,
        playlistService: PlaylistService,
    ): GeneralSettingsAdministrationService

    fun createMusicFolderAdministrationService(
        settingsService: SettingsService,
        mediaScannerService: MediaScannerService,
        mediaFolderService: MediaFolderService,
        artistDao: ArtistDao,
        albumDao: AlbumDao,
        mediaFileDao: MediaFileDao,
        indexManager: IndexManager,
        playlistService: PlaylistService,
        coverArtService: CoverArtService,
    ): MusicFolderAdministrationService

    fun createCoverArtAccentColorBackfill(
        coverArtService: CoverArtService,
    ): CoverArtAccentColorBackfill
}
