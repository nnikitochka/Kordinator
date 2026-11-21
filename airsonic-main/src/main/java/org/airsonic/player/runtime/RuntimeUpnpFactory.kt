package org.airsonic.player.runtime

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.MusicIndexService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SearchService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.TranscodingService
import org.airsonic.player.service.JWTSecurityService
import org.airsonic.player.service.UPnPService
import org.airsonic.player.service.PlayerService
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
