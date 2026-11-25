package su.nnedition.kord.runtime

import su.nnedition.kord.dao.AlbumDao
import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.MusicIndexService
import su.nnedition.kord.service.PlaylistService
import su.nnedition.kord.service.SearchService
import su.nnedition.kord.service.SettingsService
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

/** Creates UPnP content-directory and processor objects; lifecycle wiring stays in the runtime root. */
object RuntimeUpnpFactory {
    @JvmStatic
    fun createUpnpService(settingsService: SettingsService, contentDirectory: CustomContentDirectory) =
        UPnPService(settingsService, contentDirectory)

    @JvmStatic
    fun createAlbumProcessor(albumDao: AlbumDao) = AlbumUpnpProcessor().apply { setAlbumDao(albumDao) }

    @JvmStatic
    fun createRecentAlbumProcessor(albumDao: AlbumDao) =
        RecentAlbumUpnpProcessor().apply { setAlbumDao(albumDao) }

    @JvmStatic
    fun createArtistProcessor(artistDao: ArtistDao) = ArtistUpnpProcessor().apply { setArtistDao(artistDao) }

    @JvmStatic
    fun createGenreProcessor() = GenreUpnpProcessor()

    @JvmStatic
    fun createMediaFileProcessor(mediaFileDao: MediaFileDao) =
        MediaFileUpnpProcessor().apply { setMediaFileDao(mediaFileDao) }

    @JvmStatic
    fun createPlaylistProcessor(playlistService: PlaylistService) =
        PlaylistUpnpProcessor().apply { setPlaylistService(playlistService) }

    @JvmStatic
    fun createRootProcessor(indexManager: IndexManager) = RootUpnpProcessor(indexManager)

    @JvmStatic
    fun createDispatchingContentDirectory(
        mediaFileProcessor: MediaFileUpnpProcessor,
        playlistProcessor: PlaylistUpnpProcessor,
        albumProcessor: AlbumUpnpProcessor,
        recentAlbumProcessor: RecentAlbumUpnpProcessor,
        artistProcessor: ArtistUpnpProcessor,
        genreProcessor: GenreUpnpProcessor,
        rootProcessor: RootUpnpProcessor,
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        playlistService: PlaylistService,
        searchService: SearchService,
        musicIndexService: MusicIndexService,
    ) = DispatchingContentDirectory(
        mediaFileProcessor, playlistProcessor, albumProcessor, recentAlbumProcessor,
        artistProcessor, genreProcessor, rootProcessor, mediaFileService, mediaFolderService,
        playlistService, searchService, musicIndexService,
    )

    @JvmStatic
    fun createFolderBasedContentDirectory(
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        playlistService: PlaylistService,
        indexManager: IndexManager,
    ) = FolderBasedContentDirectory(mediaFileService, mediaFolderService, playlistService, indexManager)
}
