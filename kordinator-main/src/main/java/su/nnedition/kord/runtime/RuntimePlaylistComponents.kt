package su.nnedition.kord.runtime

import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.dao.PlaylistDao
import su.nnedition.kord.service.CoverArtService
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.PlaylistService
import su.nnedition.kord.service.SecurityService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.playlist.DefaultPlaylistExportHandler
import su.nnedition.kord.service.playlist.DefaultPlaylistImportHandler
import su.nnedition.kord.service.playlist.XspfPlaylistExportHandler
import su.nnedition.kord.service.playlist.XspfPlaylistImportHandler

/** Creation boundary for the playlist components owned by one runtime. */
interface RuntimePlaylistComponents {
    fun createDefaultPlaylistExportHandler(
        mediaFileDao: MediaFileDao,
    ): DefaultPlaylistExportHandler

    fun createDefaultPlaylistImportHandler(
        mediaFileService: MediaFileService,
        settingsService: SettingsService,
    ): DefaultPlaylistImportHandler

    fun createXspfPlaylistExportHandler(
        mediaFileDao: MediaFileDao,
        playlistDao: PlaylistDao,
        coverArtService: CoverArtService,
    ): XspfPlaylistExportHandler

    fun createXspfPlaylistImportHandler(
        mediaFileService: MediaFileService,
        settingsService: SettingsService,
    ): XspfPlaylistImportHandler

    @Throws(Exception::class)
    fun createPlaylistService(
        securityService: SecurityService,
        settingsService: SettingsService,
        mediaFileService: MediaFileService,
        coverArtService: CoverArtService,
    ): PlaylistService
}
