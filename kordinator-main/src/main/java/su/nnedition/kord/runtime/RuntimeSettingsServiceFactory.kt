package su.nnedition.kord.runtime

import su.nnedition.kord.dao.AlbumDao
import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.dao.AvatarDao
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.dao.UserDao
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
