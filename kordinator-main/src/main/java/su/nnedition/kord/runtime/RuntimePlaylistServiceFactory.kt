package su.nnedition.kord.runtime

import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.dao.PlaylistDao
import su.nnedition.kord.service.CoverArtService
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.PlaylistService
import su.nnedition.kord.service.SecurityService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.PathWatcherService
import su.nnedition.kord.service.playlist.DefaultPlaylistExportHandler
import su.nnedition.kord.service.playlist.DefaultPlaylistImportHandler
import su.nnedition.kord.service.playlist.PlaylistExportHandler
import su.nnedition.kord.service.playlist.PlaylistImportHandler
import su.nnedition.kord.service.playlist.XspfPlaylistExportHandler
import su.nnedition.kord.service.playlist.XspfPlaylistImportHandler
import javax.cache.CacheManager

/** Creates playlist import/export components and the service that composes them. */
object RuntimePlaylistServiceFactory {
    @JvmStatic
    fun createDefaultExportHandler(
        mediaFileDao: MediaFileDao,
        mediaFolderService: MediaFolderService,
    ): DefaultPlaylistExportHandler = DefaultPlaylistExportHandler(mediaFileDao, mediaFolderService)

    @JvmStatic
    fun createDefaultImportHandler(
        mediaFileService: MediaFileService,
        settingsService: SettingsService,
    ): DefaultPlaylistImportHandler = DefaultPlaylistImportHandler(mediaFileService, settingsService)

    @JvmStatic
    fun createXspfExportHandler(
        mediaFileDao: MediaFileDao,
        playlistDao: PlaylistDao,
        mediaFolderService: MediaFolderService,
        coverArtService: CoverArtService,
    ): XspfPlaylistExportHandler =
        XspfPlaylistExportHandler(mediaFileDao, playlistDao, mediaFolderService, coverArtService)

    @JvmStatic
    fun createXspfImportHandler(
        mediaFileService: MediaFileService,
        settingsService: SettingsService,
    ): XspfPlaylistImportHandler = XspfPlaylistImportHandler(mediaFileService, settingsService)

    @JvmStatic
    fun createPlaylistService(
        mediaFileDao: MediaFileDao,
        playlistDao: PlaylistDao,
        securityService: SecurityService,
        settingsService: SettingsService,
        exportHandlers: List<PlaylistExportHandler>,
        importHandlers: List<PlaylistImportHandler>,
        pathWatcherService: PathWatcherService,
        cacheManager: CacheManager,
    ): PlaylistService = PlaylistService(
        mediaFileDao, playlistDao, securityService, settingsService,
        exportHandlers, importHandlers, pathWatcherService, cacheManager,
    )
}
