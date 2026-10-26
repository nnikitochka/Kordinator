package org.airsonic.player.controller.rest.search

import org.airsonic.player.controller.CoverArtController
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.domain.Album
import org.airsonic.player.domain.CoverArt
import org.airsonic.player.domain.CoverArt.EntityType
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Player
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.RatingService
import org.airsonic.player.service.TranscodingService
import org.airsonic.player.util.StringUtil
import org.subsonic.restapi.AlbumID3
import org.subsonic.restapi.ArtistID3
import org.subsonic.restapi.Child
import kotlin.math.roundToInt

/** Converts application domain objects into the Subsonic REST schema. */
class SubsonicResponseMapper(
    private val mediaFileService: MediaFileService,
    private val mediaFileDao: MediaFileDao,
    private val ratingService: RatingService,
    private val coverArtService: CoverArtService,
    private val transcodingService: TranscodingService,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val jaxbWriter: JAXBWriter,
) {
    fun <T : ArtistID3> artist(target: T, artist: org.airsonic.player.domain.Artist, username: String): T {
        target.id = artist.id.toString()
        target.name = artist.name
        target.starred = jaxbWriter.convertDate(artistDao.getArtistStarredDate(artist.id, username))
        target.albumCount = artist.albumCount
        if (CoverArt.NULL_ART != coverArtService.get(EntityType.ARTIST, artist.id)) {
            target.coverArt = CoverArtController.ARTIST_COVERART_PREFIX + artist.id
        }
        return target
    }

    fun artist(artist: MediaFile, username: String): org.subsonic.restapi.Artist =
        org.subsonic.restapi.Artist().apply {
            id = artist.id.toString()
            name = artist.artist
            starred = jaxbWriter.convertDate(mediaFileDao.getMediaFileStarredDate(artist.id, username))
        }

    fun <T : AlbumID3> album(target: T, album: Album, username: String): T {
        target.id = album.id.toString()
        target.name = album.name
        album.artist?.let { albumArtist ->
            target.artist = albumArtist
            artistDao.getArtist(albumArtist)?.let { target.artistId = it.id.toString() }
        }
        if (CoverArt.NULL_ART != coverArtService.get(EntityType.ALBUM, album.id)) {
            target.coverArt = CoverArtController.ALBUM_COVERART_PREFIX + album.id
        }
        target.accentColor = coverArtService.getAccentColor(EntityType.ALBUM, album.id)
        target.songCount = album.songCount
        target.duration = album.duration.roundToInt()
        target.created = jaxbWriter.convertDate(album.created)
        target.starred = jaxbWriter.convertDate(albumDao.getAlbumStarredDate(album.id, username))
        target.year = album.year
        target.genre = album.genre
        return target
    }

    fun child(player: Player, mediaFile: MediaFile, username: String): Child =
        child(Child(), player, mediaFile, username)

    fun <T : Child> child(target: T, player: Player, mediaFile: MediaFile, username: String): T {
        val parent = mediaFileService.getParentOf(mediaFile)
        target.id = mediaFile.id.toString()
        try {
            if (!mediaFileService.isRoot(parent)) {
                target.parent = parent.id.toString()
            }
        } catch (_: SecurityException) {
            // The root media file has no parent in the REST representation.
        }
        target.title = mediaFile.name
        target.album = mediaFile.albumName
        target.artist = mediaFile.artist
        target.directory = mediaFile.isDirectory
        target.coverArt = findCoverArt(mediaFile, parent)
        target.accentColor = accentColor(mediaFile, parent)
        target.year = mediaFile.year
        target.genre = mediaFile.genre
        target.created = jaxbWriter.convertDate(mediaFile.created)
        target.starred = jaxbWriter.convertDate(mediaFileDao.getMediaFileStarredDate(mediaFile.id, username))
        target.userRating = ratingService.getRatingForUser(username, mediaFile)
        target.averageRating = ratingService.getAverageRating(mediaFile)
        target.playCount = mediaFile.playCount.toLong()

        if (mediaFile.isFile) {
            target.duration = mediaFile.duration.roundToInt()
            target.bitRate = mediaFile.bitRate
            target.track = mediaFile.trackNumber
            target.discNumber = mediaFile.discNumber
            target.size = mediaFile.fileSize
            val suffix = mediaFile.format
            target.suffix = suffix
            target.contentType = StringUtil.getMimeType(suffix)
            target.video = mediaFile.isVideo
            target.path = mediaFile.path

            if (mediaFile.albumArtist != null && mediaFile.albumName != null) {
                albumDao.getAlbum(mediaFile.albumArtist, mediaFile.albumName)?.let {
                    target.albumId = it.id.toString()
                }
            }
            mediaFile.artist?.let { name ->
                artistDao.getArtist(name)?.let { target.artistId = it.id.toString() }
            }
            when (mediaFile.mediaType) {
                MediaFile.MediaType.MUSIC -> target.type = org.subsonic.restapi.MediaType.MUSIC
                MediaFile.MediaType.AUDIOBOOK -> target.type = org.subsonic.restapi.MediaType.AUDIOBOOK
                MediaFile.MediaType.VIDEO -> {
                    target.type = org.subsonic.restapi.MediaType.VIDEO
                    target.originalWidth = mediaFile.width
                    target.originalHeight = mediaFile.height
                }
                else -> Unit
            }
            if (transcodingService.isTranscodingRequired(mediaFile, player)) {
                val transcodedSuffix = transcodingService.getSuffix(player, mediaFile, null)
                target.transcodedSuffix = transcodedSuffix
                target.transcodedContentType = StringUtil.getMimeType(transcodedSuffix)
            }
        }
        return target
    }

    fun accentColor(mediaFile: MediaFile, parent: MediaFile?): String? {
        val directory = if (mediaFile.isDirectory) mediaFile else parent ?: return null
        return coverArtService.getAccentColor(EntityType.MEDIA_FILE, directory.id)
    }

    private fun findCoverArt(mediaFile: MediaFile, parent: MediaFile?): String? {
        val directory = if (mediaFile.isDirectory) mediaFile else parent
        return if (directory != null && CoverArt.NULL_ART != coverArtService.get(EntityType.MEDIA_FILE, directory.id)) {
            directory.id.toString()
        } else {
            null
        }
    }
}
