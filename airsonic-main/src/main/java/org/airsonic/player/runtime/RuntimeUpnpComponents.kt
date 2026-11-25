package org.airsonic.player.runtime

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.JWTSecurityService
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.media.MediaFolderService
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
