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

/** Creation boundary for the upnp components owned by one runtime. */
interface RuntimeUpnpComponents {
    fun createUpnpService(
        settingsService: SettingsService,
        contentDirectory: CustomContentDirectory,
    ): UPnPService

    fun createAlbumUpnpProcessor(
        albumDao: AlbumDao,
    ): AlbumUpnpProcessor

    fun createRecentAlbumUpnpProcessor(
        albumDao: AlbumDao,
    ): RecentAlbumUpnpProcessor

    fun createArtistUpnpProcessor(
        artistDao: ArtistDao,
    ): ArtistUpnpProcessor

    fun createGenreUpnpProcessor(): GenreUpnpProcessor

    fun createMediaFileUpnpProcessor(
        mediaFileDao: MediaFileDao,
    ): MediaFileUpnpProcessor

    fun createPlaylistUpnpProcessor(
        playlistService: PlaylistService,
    ): PlaylistUpnpProcessor

    fun createRootUpnpProcessor(
        indexManager: IndexManager,
    ): RootUpnpProcessor

    fun createDispatchingContentDirectory(
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
    ): DispatchingContentDirectory

    fun createFolderBasedContentDirectory(
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        playlistService: PlaylistService,
        indexManager: IndexManager,
        settingsService: SettingsService,
        playerService: PlayerService,
        transcodingService: TranscodingService,
        jwtSecurityService: JWTSecurityService,
    ): FolderBasedContentDirectory
}
