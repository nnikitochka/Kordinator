package org.airsonic.player.runtime

import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.dao.PlaylistDao
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.playlist.DefaultPlaylistExportHandler
import org.airsonic.player.service.playlist.DefaultPlaylistImportHandler
import org.airsonic.player.service.playlist.XspfPlaylistExportHandler
import org.airsonic.player.service.playlist.XspfPlaylistImportHandler

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
