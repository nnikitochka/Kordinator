package org.airsonic.player.runtime

import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.dao.PlaylistDao
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.media.MediaFolderService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.PathWatcherService
import org.airsonic.player.service.playlist.DefaultPlaylistExportHandler
import org.airsonic.player.service.playlist.DefaultPlaylistImportHandler
import org.airsonic.player.service.playlist.PlaylistExportHandler
import org.airsonic.player.service.playlist.PlaylistImportHandler
import org.airsonic.player.service.playlist.XspfPlaylistExportHandler
import org.airsonic.player.service.playlist.XspfPlaylistImportHandler
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
