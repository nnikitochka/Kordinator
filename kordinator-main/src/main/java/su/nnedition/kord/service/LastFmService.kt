package su.nnedition.kord.service

import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.domain.AlbumNotes
import su.nnedition.kord.domain.ArtistBio
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.service.media.MediaFileService
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
        artist: su.nnedition.kord.domain.Artist?,
        count: Int,
        includeNotPresent: Boolean,
        musicFolders: List<MusicFolder>
    ): List<su.nnedition.kord.domain.Artist> =
        recommendations().getSimilarArtists(artist!!, count, includeNotPresent, musicFolders)

    fun getSimilarSongs(
        artist: su.nnedition.kord.domain.Artist?,
        count: Int,
        musicFolders: List<MusicFolder>
    ): List<MediaFile> = recommendations().getSimilarSongs(artist!!, count, musicFolders)

    fun getSimilarSongs(mediaFile: MediaFile?, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> =
        recommendations().getSimilarSongs(mediaFile!!, count, musicFolders)

    fun getArtistBio(mediaFile: MediaFile?, locale: Locale?): ArtistBio? = metadata().getArtistBio(mediaFile!!, locale!!)

    fun getArtistBio(artist: su.nnedition.kord.domain.Artist?, locale: Locale?): ArtistBio? =
        metadata().getArtistBio(artist!!, locale!!)

    fun getTopSongs(artist: MediaFile?, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> =
        recommendations().getTopSongs(artist!!.name, count, musicFolders)

    fun getTopSongs(artistName: String, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> =
        recommendations().getTopSongs(artistName, count, musicFolders)

    fun getAlbumNotes(mediaFile: MediaFile?): AlbumNotes? = metadata().getAlbumNotes(mediaFile!!)

    fun getAlbumNotes(album: su.nnedition.kord.domain.Album?): AlbumNotes? = metadata().getAlbumNotes(album!!)

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
