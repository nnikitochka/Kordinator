package su.nnedition.kord.runtime

import su.nnedition.kord.dao.AlbumDao
import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.service.JWTSecurityService
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.MusicIndexService
import su.nnedition.kord.service.PlayerService
import su.nnedition.kord.service.PlaylistService
import su.nnedition.kord.service.SearchService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.TranscodingService
import su.nnedition.kord.service.UPnPService
import su.nnedition.kord.service.search.IndexManager
import su.nnedition.kord.service.upnp.AlbumUpnpProcessor
import su.nnedition.kord.service.upnp.ArtistUpnpProcessor
import su.nnedition.kord.service.upnp.CustomContentDirectory
import su.nnedition.kord.service.upnp.DispatchingContentDirectory
import su.nnedition.kord.service.upnp.FolderBasedContentDirectory
import su.nnedition.kord.service.upnp.GenreUpnpProcessor
import su.nnedition.kord.service.upnp.MediaFileUpnpProcessor
import su.nnedition.kord.service.upnp.PlaylistUpnpProcessor
import su.nnedition.kord.service.upnp.RecentAlbumUpnpProcessor
import su.nnedition.kord.service.upnp.RootUpnpProcessor

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
