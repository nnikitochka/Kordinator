package org.airsonic.player.service.media

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.domain.Album
import org.airsonic.player.domain.Artist
import org.airsonic.player.domain.CoverArt
import org.airsonic.player.domain.CoverArt.EntityType
import org.airsonic.player.domain.Genres
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.SettingsService
import org.apache.commons.lang.ObjectUtils
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Builds the artist, album, and genre aggregates collected during scanning. */
internal class MediaLibraryScanEntities(
    private val settingsService: SettingsService,
    private val mediaFileService: MediaFileService,
    private val coverArtService: CoverArtService,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao
) {
    fun updateGenres(file: MediaFile, genres: Genres) {
        val genre = file.genre ?: return
        when {
            file.isAlbum -> genres.incrementAlbumCount(genre, settingsService.getGenreSeparators())
            file.isAudio -> genres.incrementSongCount(genre, settingsService.getGenreSeparators())
        }
    }

    fun updateAlbumAndArtist(file: MediaFile, musicFolder: MusicFolder, state: MediaLibraryScanState) {
        val lastScanned = state.statistics.getScanDate()!!
        updateAlbum(file, musicFolder, lastScanned, state)
        updateArtist(file, musicFolder, lastScanned, state)
    }

    private fun updateAlbum(file: MediaFile, musicFolder: MusicFolder, lastScanned: Instant, state: MediaLibraryScanState) {
        val artistName = file.albumArtist ?: file.artist
        val albumName = file.albumName
        if (albumName == null || artistName == null || file.parentPath == null || !file.isAudio) return

        val firstEncounter = AtomicBoolean(false)
        val album = state.albums.compute("$albumName|$artistName") { _, existing ->
            var result = existing
            if (result == null) {
                val dbAlbum = albumDao.getAlbum(artistName, albumName)
                if (dbAlbum != null) {
                    result = state.albumsInDatabase.computeIfAbsent(dbAlbum.id) {
                        dbAlbum.duration = 0.0
                        dbAlbum.songCount = 0
                        dbAlbum
                    }
                }
            }
            if (result == null) {
                result = Album().apply {
                    path = file.parentPath
                    name = albumName
                    artist = artistName
                    created = file.changed
                }
            }

            firstEncounter.set(lastScanned != result.lastScanned)
            file.duration?.let(result::incrementDuration)
            if (file.isAudio) result.incrementSongCount()
            result.lastScanned = lastScanned
            result.isPresent = true
            result
        }!!

        file.musicBrainzReleaseId?.let { album.musicBrainzReleaseId = it }
        file.year?.let { album.year = it }
        file.genre?.let { album.genre = it }
        if (album.art == null) {
            mediaFileService.getParentOf(file, true)?.let { parent ->
                val art = coverArtService.get(EntityType.MEDIA_FILE, parent.id!!)
                if (CoverArt.NULL_ART != art) {
                    album.art = CoverArt(-1, EntityType.ALBUM, art.path, art.folderId, false)
                }
            }
        }
        if (firstEncounter.get()) {
            album.folderId = musicFolder.id
            state.albumCounts.computeIfAbsent(artistName) { AtomicInteger(0) }.incrementAndGet()
        }
        if (!ObjectUtils.equals(album.artist, file.albumArtist)) {
            file.albumArtist = album.artist
            mediaFileService.updateMediaFile(file)
        }
    }

    private fun updateArtist(file: MediaFile, musicFolder: MusicFolder, lastScanned: Instant, state: MediaLibraryScanState) {
        val albumArtist = file.albumArtist
        if (albumArtist == null || !file.isAudio) return

        val firstEncounter = AtomicBoolean(false)
        val artist = state.artists.compute(albumArtist) { key, existing ->
            var result = existing ?: artistDao.getArtist(key)
            if (result == null) result = Artist().apply { name = key }
            val currentCount = result.name?.let { state.albumCounts[it]?.get() } ?: 0
            result.albumCount = maxOf(currentCount, result.albumCount ?: 0)
            firstEncounter.set(lastScanned != result.lastScanned)
            result.lastScanned = lastScanned
            result.isPresent = true
            result
        }!!

        if (artist.art == null) {
            mediaFileService.getParentOf(file, true)?.let { parent ->
                val art = coverArtService.get(EntityType.MEDIA_FILE, parent.id!!)
                if (CoverArt.NULL_ART != art) {
                    artist.art = CoverArt(-1, EntityType.ARTIST, art.path, art.folderId, false)
                }
            }
        }
        if (firstEncounter.get()) artist.folderId = musicFolder.id
    }
}
