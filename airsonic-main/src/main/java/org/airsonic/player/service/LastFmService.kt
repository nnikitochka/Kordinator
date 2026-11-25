package org.airsonic.player.service

import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.domain.AlbumNotes
import org.airsonic.player.domain.ArtistBio
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import java.util.Locale

/** Provides recommendation and artist/album metadata through Last.fm. */
class LastFmService(
    mediaFileDao: MediaFileDao,
    mediaFileService: MediaFileService,
    artistDao: ArtistDao
) {
    private var mediaFiles = mediaFileDao
    private var mediaFileService = mediaFileService
    private var artists = artistDao

    fun init() {
        LastFmClientConfiguration.initialize()
    }

    fun getSimilarArtists(
        mediaFile: MediaFile?,
        count: Int,
        includeNotPresent: Boolean,
        musicFolders: List<MusicFolder>
    ): List<MediaFile> = recommendations().getSimilarArtists(mediaFile, count, includeNotPresent, musicFolders)

    fun getSimilarArtists(
        artist: org.airsonic.player.domain.Artist?,
        count: Int,
        includeNotPresent: Boolean,
        musicFolders: List<MusicFolder>
    ): List<org.airsonic.player.domain.Artist> =
        recommendations().getSimilarArtists(artist!!, count, includeNotPresent, musicFolders)

    fun getSimilarSongs(
        artist: org.airsonic.player.domain.Artist?,
        count: Int,
        musicFolders: List<MusicFolder>
    ): List<MediaFile> = recommendations().getSimilarSongs(artist!!, count, musicFolders)

    fun getSimilarSongs(mediaFile: MediaFile?, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> =
        recommendations().getSimilarSongs(mediaFile!!, count, musicFolders)

    fun getArtistBio(mediaFile: MediaFile?, locale: Locale?): ArtistBio? = metadata().getArtistBio(mediaFile!!, locale!!)

    fun getArtistBio(artist: org.airsonic.player.domain.Artist?, locale: Locale?): ArtistBio? =
        metadata().getArtistBio(artist!!, locale!!)

    fun getTopSongs(artist: MediaFile?, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> =
        recommendations().getTopSongs(artist!!.name, count, musicFolders)

    fun getTopSongs(artistName: String, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> =
        recommendations().getTopSongs(artistName, count, musicFolders)

    fun getAlbumNotes(mediaFile: MediaFile?): AlbumNotes? = metadata().getAlbumNotes(mediaFile!!)

    fun getAlbumNotes(album: org.airsonic.player.domain.Album?): AlbumNotes? = metadata().getAlbumNotes(album!!)

    fun setMediaFileDao(mediaFileDao: MediaFileDao) {
        mediaFiles = mediaFileDao
    }

    fun setMediaFileService(mediaFileService: MediaFileService) {
        this.mediaFileService = mediaFileService
    }

    fun setArtistDao(artistDao: ArtistDao) {
        artists = artistDao
    }

    private fun recommendations() = LastFmRecommendations(mediaFiles, mediaFileService, artists)

    private fun metadata() = LastFmMetadataClient()
}
