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
import org.airsonic.player.service.playlist.PlaylistExportHandler
import org.airsonic.player.service.playlist.PlaylistImportHandler
import org.airsonic.player.service.playlist.XspfPlaylistExportHandler
import org.airsonic.player.service.playlist.XspfPlaylistImportHandler

/** Lazily assembles playlist components under the shared runtime lock. */
internal class RuntimePlaylistComposition(private val context: RuntimeCompositionContext) : RuntimePlaylistComponents {
    private var playlistService: PlaylistService? = null
    private var defaultPlaylistExportHandler: DefaultPlaylistExportHandler? = null
    private var defaultPlaylistImportHandler: DefaultPlaylistImportHandler? = null
    private var xspfPlaylistExportHandler: XspfPlaylistExportHandler? = null
    private var xspfPlaylistImportHandler: XspfPlaylistImportHandler? = null

    override fun createDefaultPlaylistExportHandler(
        mediaFileDao: MediaFileDao,
    ): DefaultPlaylistExportHandler = synchronized(context.lock) {
        if (defaultPlaylistExportHandler == null) {
            defaultPlaylistExportHandler = RuntimePlaylistServiceFactory.createDefaultExportHandler(
                    mediaFileDao, context.root.createMediaFolderService())
        }
        return@synchronized defaultPlaylistExportHandler!!
    }

    override fun createDefaultPlaylistImportHandler(
        mediaFileService: MediaFileService,
        settingsService: SettingsService,
    ): DefaultPlaylistImportHandler = synchronized(context.lock) {
        if (defaultPlaylistImportHandler == null) {
            defaultPlaylistImportHandler = RuntimePlaylistServiceFactory.createDefaultImportHandler(
                    mediaFileService, settingsService)
        }
        return@synchronized defaultPlaylistImportHandler!!
    }

    override fun createXspfPlaylistExportHandler(
        mediaFileDao: MediaFileDao,
        playlistDao: PlaylistDao,
        coverArtService: CoverArtService,
    ): XspfPlaylistExportHandler = synchronized(context.lock) {
        if (xspfPlaylistExportHandler == null) {
            xspfPlaylistExportHandler = RuntimePlaylistServiceFactory.createXspfExportHandler(
                    mediaFileDao, playlistDao, context.root.createMediaFolderService(), coverArtService)
        }
        return@synchronized xspfPlaylistExportHandler!!
    }

    override fun createXspfPlaylistImportHandler(
        mediaFileService: MediaFileService,
        settingsService: SettingsService,
    ): XspfPlaylistImportHandler = synchronized(context.lock) {
        if (xspfPlaylistImportHandler == null) {
            xspfPlaylistImportHandler = RuntimePlaylistServiceFactory.createXspfImportHandler(
                    mediaFileService, settingsService)
        }
        return@synchronized xspfPlaylistImportHandler!!
    }

    @Throws(Exception::class)
    override fun createPlaylistService(
        securityService: SecurityService,
        settingsService: SettingsService,
        mediaFileService: MediaFileService,
        coverArtService: CoverArtService,
    ): PlaylistService = synchronized(context.lock) {
        if (playlistService == null) {
            if (context.persistenceGraph == null) {
                throw IllegalStateException("Persistence graph must be created before PlaylistService")
            }
            val daos = context.persistenceGraph!!.daos
            val exportHandlers = listOf<PlaylistExportHandler>(
                    createDefaultPlaylistExportHandler(daos.mediaFileDao),
                    createXspfPlaylistExportHandler(daos.mediaFileDao, daos.playlistDao, coverArtService))
            val importHandlers = listOf<PlaylistImportHandler>(
                    createDefaultPlaylistImportHandler(mediaFileService, settingsService),
                    createXspfPlaylistImportHandler(mediaFileService, settingsService))
            playlistService = RuntimePlaylistServiceFactory.createPlaylistService(
                    daos.mediaFileDao, daos.playlistDao,
                    securityService, settingsService, exportHandlers, importHandlers,
                    context.root.createPathWatcherService(), context.root.createCacheManager())
            playlistService!!.init()
        }
        return@synchronized playlistService!!
    }
}
