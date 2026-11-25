package org.airsonic.player.service

import de.umass.lastfm.Artist as LastFmArtist
import de.umass.lastfm.Track
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.slf4j.LoggerFactory
import java.util.Collections

/** Resolves Last.fm recommendations to items available in the local library. */
internal class LastFmRecommendations(
    private val mediaFileDao: MediaFileDao,
    private val mediaFileService: MediaFileService,
    private val artistDao: ArtistDao
) {
    fun getSimilarArtists(
        mediaFile: MediaFile?,
        count: Int,
        includeNotPresent: Boolean,
        musicFolders: List<MusicFolder>
    ): List<MediaFile> {
        val result = ArrayList<MediaFile>()
        if (mediaFile == null) return result
        val artistName = artistName(mediaFile)
        try {
            val similarArtists = LastFmArtist.getSimilar(canonicalArtistName(artistName), API_KEY)
            for (lastFmArtist in similarArtists) {
                val similarArtist = mediaFileDao.getArtistByName(lastFmArtist.name, musicFolders)
                if (similarArtist != null) {
                    result.add(similarArtist)
                    if (result.size == count) return result
                }
            }
            if (includeNotPresent) {
                for (lastFmArtist in similarArtists) {
                    if (mediaFileDao.getArtistByName(lastFmArtist.name, musicFolders) == null) {
                        result.add(MediaFile().apply {
                            id = -1
                            artist = lastFmArtist.name
                        })
                        if (result.size == count) return result
                    }
                }
            }
        } catch (failure: Throwable) {
            log.warn("Failed to find similar artists for {}", artistName, failure)
        }
        return result
    }

    fun getSimilarArtists(
        artist: org.airsonic.player.domain.Artist,
        count: Int,
        includeNotPresent: Boolean,
        musicFolders: List<MusicFolder>
    ): List<org.airsonic.player.domain.Artist> {
        val result = ArrayList<org.airsonic.player.domain.Artist>()
        try {
            val similarArtists = LastFmArtist.getSimilar(canonicalArtistName(artist.name), API_KEY)
            for (lastFmArtist in similarArtists) {
                val similarArtist = artistDao.getArtist(lastFmArtist.name, musicFolders)
                if (similarArtist != null) {
                    result.add(similarArtist)
                    if (result.size == count) return result
                }
            }
            if (includeNotPresent) {
                for (lastFmArtist in similarArtists) {
                    if (artistDao.getArtist(lastFmArtist.name!!) == null) {
                        result.add(org.airsonic.player.domain.Artist(id = -1, name = lastFmArtist.name))
                        if (result.size == count) return result
                    }
                }
            }
        } catch (failure: Throwable) {
            log.warn("Failed to find similar artists for {}", artist.name, failure)
        }
        return result
    }

    fun getSimilarSongs(
        artist: org.airsonic.player.domain.Artist,
        count: Int,
        musicFolders: List<MusicFolder>
    ): List<MediaFile> {
        val similarSongs = ArrayList(mediaFileDao.getSongsByArtist(artist.name!!, 0, 1000))
        for (similarArtist in getSimilarArtists(artist, 100, false, musicFolders)) {
            similarSongs.addAll(mediaFileDao.getSongsByArtist(similarArtist.name!!, 0, 1000))
        }
        Collections.shuffle(similarSongs)
        return similarSongs.subList(0, minOf(count, similarSongs.size))
    }

    fun getSimilarSongs(mediaFile: MediaFile, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> {
        val similarSongs = ArrayList<MediaFile>()
        val artistName = artistName(mediaFile)
        val artist = mediaFileDao.getArtistByName(artistName!!, musicFolders)
        if (artist != null) similarSongs.addAll(mediaFileService.getRandomSongsForParent(artist, count))
        for (similarArtist in getSimilarArtists(mediaFile, 100, false, musicFolders)) {
            similarSongs.addAll(mediaFileService.getRandomSongsForParent(similarArtist, count))
        }
        Collections.shuffle(similarSongs)
        return similarSongs.subList(0, minOf(count, similarSongs.size))
    }

    fun getTopSongs(artistName: String?, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> {
        try {
            if (org.apache.commons.lang.StringUtils.isBlank(artistName) || count <= 0) return emptyList()
            val result = ArrayList<MediaFile>()
            for (topTrack: Track in LastFmArtist.getTopTracks(artistName, API_KEY)) {
                val song = mediaFileDao.getSongByArtistAndTitle(artistName, topTrack.name, musicFolders)
                if (song != null) {
                    result.add(song)
                    if (result.size == count) return result
                }
            }
            return result
        } catch (failure: Throwable) {
            log.warn("Failed to find top songs for {}", artistName, failure)
            return emptyList()
        }
    }

    private fun canonicalArtistName(artistName: String?): String? = LastFmMetadataClient().canonicalArtistName(artistName)

    private fun artistName(mediaFile: MediaFile): String? = when {
        mediaFile.isAlbum || mediaFile.isFile -> mediaFile.albumArtist ?: mediaFile.artist
        else -> mediaFile.name
    }

    companion object {
        private const val API_KEY = "ece4499898a9440896dfdce5dab26bbf"
        private val log = LoggerFactory.getLogger(LastFmRecommendations::class.java)
    }
}
