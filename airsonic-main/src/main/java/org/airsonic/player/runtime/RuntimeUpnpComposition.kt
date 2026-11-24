package org.airsonic.player.runtime

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.JWTSecurityService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.MusicIndexService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SearchService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.TranscodingService
import org.airsonic.player.service.UPnPService
import org.airsonic.player.service.search.IndexManager
import org.airsonic.player.service.upnp.AlbumUpnpProcessor
import org.airsonic.player.service.upnp.ArtistUpnpProcessor
import org.airsonic.player.service.upnp.CustomContentDirectory
import org.airsonic.player.service.upnp.DispatchingContentDirectory
import org.airsonic.player.service.upnp.FolderBasedContentDirectory
import org.airsonic.player.service.upnp.GenreUpnpProcessor
import org.airsonic.player.service.upnp.MediaFileUpnpProcessor
import org.airsonic.player.service.upnp.PlaylistUpnpProcessor
import org.airsonic.player.service.upnp.RecentAlbumUpnpProcessor
import org.airsonic.player.service.upnp.RootUpnpProcessor

/** Lazily assembles upnp components under the shared runtime lock. */
internal class RuntimeUpnpComposition(private val context: RuntimeCompositionContext) : RuntimeUpnpComponents {
    private var upnpService: UPnPService? = null
    private var albumUpnpProcessor: AlbumUpnpProcessor? = null
    private var recentAlbumUpnpProcessor: RecentAlbumUpnpProcessor? = null
    private var artistUpnpProcessor: ArtistUpnpProcessor? = null
    private var genreUpnpProcessor: GenreUpnpProcessor? = null
    private var mediaFileUpnpProcessor: MediaFileUpnpProcessor? = null
    private var playlistUpnpProcessor: PlaylistUpnpProcessor? = null
    private var rootUpnpProcessor: RootUpnpProcessor? = null
    private var dispatchingContentDirectory: DispatchingContentDirectory? = null
    private var folderBasedContentDirectory: FolderBasedContentDirectory? = null

    override fun createUpnpService(
        settingsService: SettingsService,
        contentDirectory: CustomContentDirectory,
    ): UPnPService = synchronized(context.lock) {
        if (upnpService == null) {
            upnpService = RuntimeUpnpFactory.createUpnpService(settingsService, contentDirectory)
            upnpService!!.init()
            context.lifecycleRegistry.registerShutdown("upnpService", upnpService)
        }
        return@synchronized upnpService!!
    }

    override fun createAlbumUpnpProcessor(
        albumDao: AlbumDao,
    ): AlbumUpnpProcessor = synchronized(context.lock) {
        if (albumUpnpProcessor == null) {
            albumUpnpProcessor = RuntimeUpnpFactory.createAlbumProcessor(albumDao)
        }
        return@synchronized albumUpnpProcessor!!
    }

    override fun createRecentAlbumUpnpProcessor(
        albumDao: AlbumDao,
    ): RecentAlbumUpnpProcessor = synchronized(context.lock) {
        if (recentAlbumUpnpProcessor == null) {
            recentAlbumUpnpProcessor = RuntimeUpnpFactory.createRecentAlbumProcessor(albumDao)
        }
        return@synchronized recentAlbumUpnpProcessor!!
    }

    override fun createArtistUpnpProcessor(
        artistDao: ArtistDao,
    ): ArtistUpnpProcessor = synchronized(context.lock) {
        if (artistUpnpProcessor == null) {
            artistUpnpProcessor = RuntimeUpnpFactory.createArtistProcessor(artistDao)
        }
        return@synchronized artistUpnpProcessor!!
    }

    override fun createGenreUpnpProcessor(): GenreUpnpProcessor = synchronized(context.lock) {
        if (genreUpnpProcessor == null) {
            genreUpnpProcessor = RuntimeUpnpFactory.createGenreProcessor()
        }
        return@synchronized genreUpnpProcessor!!
    }

    override fun createMediaFileUpnpProcessor(
        mediaFileDao: MediaFileDao,
    ): MediaFileUpnpProcessor = synchronized(context.lock) {
        if (mediaFileUpnpProcessor == null) {
            mediaFileUpnpProcessor = RuntimeUpnpFactory.createMediaFileProcessor(mediaFileDao)
        }
        return@synchronized mediaFileUpnpProcessor!!
    }

    override fun createPlaylistUpnpProcessor(
        playlistService: PlaylistService,
    ): PlaylistUpnpProcessor = synchronized(context.lock) {
        if (playlistUpnpProcessor == null) {
            playlistUpnpProcessor = RuntimeUpnpFactory.createPlaylistProcessor(playlistService)
        }
        return@synchronized playlistUpnpProcessor!!
    }

    override fun createRootUpnpProcessor(
        indexManager: IndexManager,
    ): RootUpnpProcessor = synchronized(context.lock) {
        if (rootUpnpProcessor == null) {
            rootUpnpProcessor = RuntimeUpnpFactory.createRootProcessor(indexManager)
        }
        return@synchronized rootUpnpProcessor!!
    }

    override fun createDispatchingContentDirectory(
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        playlistService: PlaylistService,
        searchService: SearchService,
        musicIndexService: MusicIndexService,
        indexManager: IndexManager,
        settingsService: SettingsService,
        playerService: PlayerService,
        transcodingService: TranscodingService,
        jwtSecurityService: JWTSecurityService,
    ): DispatchingContentDirectory = synchronized(context.lock) {
        if (dispatchingContentDirectory == null) {
            if (context.persistenceGraph == null) {
                throw IllegalStateException("Persistence graph must be created before UPnP processors")
            }
            dispatchingContentDirectory = RuntimeUpnpFactory.createDispatchingContentDirectory(
                    createMediaFileUpnpProcessor(context.persistenceGraph!!.daos.mediaFileDao),
                    createPlaylistUpnpProcessor(playlistService), createAlbumUpnpProcessor(context.persistenceGraph!!.daos.albumDao),
                    createRecentAlbumUpnpProcessor(context.persistenceGraph!!.daos.albumDao),
                    createArtistUpnpProcessor(context.persistenceGraph!!.daos.artistDao), createGenreUpnpProcessor(),
                    createRootUpnpProcessor(indexManager), mediaFileService, mediaFolderService,
                    playlistService, searchService, musicIndexService)
            dispatchingContentDirectory!!.setSettingsService(settingsService)
            dispatchingContentDirectory!!.setPlayerService(playerService)
            dispatchingContentDirectory!!.setTranscodingService(transcodingService)
            dispatchingContentDirectory!!.setJwtSecurityService(jwtSecurityService)
            createMediaFileUpnpProcessor(context.persistenceGraph!!.daos.mediaFileDao)
                    .setDispatchingContentDirectory(dispatchingContentDirectory)
            createPlaylistUpnpProcessor(playlistService).setDispatchingContentDirectory(dispatchingContentDirectory)
            createAlbumUpnpProcessor(context.persistenceGraph!!.daos.albumDao)
                    .setDispatchingContentDirectory(dispatchingContentDirectory)
            createRecentAlbumUpnpProcessor(context.persistenceGraph!!.daos.albumDao)
                    .setDispatchingContentDirectory(dispatchingContentDirectory)
            createArtistUpnpProcessor(context.persistenceGraph!!.daos.artistDao)
                    .setDispatchingContentDirectory(dispatchingContentDirectory)
            createGenreUpnpProcessor().setDispatchingContentDirectory(dispatchingContentDirectory)
            createRootUpnpProcessor(indexManager).setDispatchingContentDirectory(dispatchingContentDirectory)
        }
        return@synchronized dispatchingContentDirectory!!
    }

    override fun createFolderBasedContentDirectory(
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        playlistService: PlaylistService,
        indexManager: IndexManager,
        settingsService: SettingsService,
        playerService: PlayerService,
        transcodingService: TranscodingService,
        jwtSecurityService: JWTSecurityService,
    ): FolderBasedContentDirectory = synchronized(context.lock) {
        if (folderBasedContentDirectory == null) {
            folderBasedContentDirectory = RuntimeUpnpFactory.createFolderBasedContentDirectory(
                    mediaFileService, mediaFolderService, playlistService, indexManager)
            folderBasedContentDirectory!!.setSettingsService(settingsService)
            folderBasedContentDirectory!!.setPlayerService(playerService)
            folderBasedContentDirectory!!.setTranscodingService(transcodingService)
            folderBasedContentDirectory!!.setJwtSecurityService(jwtSecurityService)
        }
        return@synchronized folderBasedContentDirectory!!
    }
}
