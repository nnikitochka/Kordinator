package org.airsonic.player.runtime

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.ConfigurationPropertiesService
import org.airsonic.player.service.CoverArtAccentColorBackfill
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.GeneralSettingsAdministrationService
import org.airsonic.player.service.media.MediaFolderService
import org.airsonic.player.service.media.MediaScannerService
import org.airsonic.player.service.MusicFolderAdministrationService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.search.IndexManager

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
