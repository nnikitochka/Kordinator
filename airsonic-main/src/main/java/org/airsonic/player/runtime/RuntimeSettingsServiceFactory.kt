package org.airsonic.player.runtime

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.AvatarDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.dao.UserDao
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
import javax.cache.CacheManager

/** Constructors for user settings and settings administration services. */
object RuntimeSettingsServiceFactory {
    @JvmStatic
    fun createSettingsService(
        userDao: UserDao,
        avatarDao: AvatarDao,
        configuration: RuntimeConfiguration,
        cacheManager: CacheManager,
        propertiesService: ConfigurationPropertiesService,
    ) = SettingsService(userDao, avatarDao, configuration, cacheManager, propertiesService)

    @JvmStatic
    fun createGeneralSettingsAdministrationService(
        settingsService: SettingsService,
        playlistService: PlaylistService,
    ) = GeneralSettingsAdministrationService(settingsService, playlistService)

    @JvmStatic
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
    ) = MusicFolderAdministrationService(
        settingsService, mediaScannerService, mediaFolderService, artistDao, albumDao,
        mediaFileDao, indexManager, playlistService, coverArtService,
    )

    @JvmStatic
    fun createCoverArtAccentColorBackfill(coverArtService: CoverArtService) =
        CoverArtAccentColorBackfill(coverArtService)
}
