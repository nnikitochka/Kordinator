package org.airsonic.player.service.media

import org.airsonic.player.domain.Album
import org.airsonic.player.domain.Genre
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.RandomSearchCriteria
import java.time.Instant
import java.util.Collections

/** Album, genre, play-history and recommendation queries for media files. */
internal class MediaFileCatalog(
    private val service: MediaFileService
) {
    fun getGenres(sortByAlbum: Boolean): List<Genre> = service.mediaFileDao.getGenres(sortByAlbum)

    fun getMostFrequentlyPlayedAlbums(offset: Int, count: Int, folders: List<MusicFolder>): List<MediaFile> =
        service.mediaFileDao.getMostFrequentlyPlayedAlbums(offset, count, folders)

    fun getMostRecentlyPlayedAlbums(offset: Int, count: Int, folders: List<MusicFolder>): List<MediaFile> =
        service.mediaFileDao.getMostRecentlyPlayedAlbums(offset, count, folders)

    fun getNewestAlbums(offset: Int, count: Int, folders: List<MusicFolder>): List<MediaFile> =
        service.mediaFileDao.getNewestAlbums(offset, count, folders)

    fun getStarredAlbums(offset: Int, count: Int, username: String, folders: List<MusicFolder>): List<MediaFile> =
        service.mediaFileDao.getStarredAlbums(offset, count, username, folders)

    fun getAlphabeticalAlbums(offset: Int, count: Int, byArtist: Boolean, folders: List<MusicFolder>): List<MediaFile> =
        service.mediaFileDao.getAlphabeticalAlbums(offset, count, byArtist, folders)

    fun getAlbumsByYear(
        offset: Int,
        count: Int,
        fromYear: Int,
        toYear: Int,
        folders: List<MusicFolder>,
    ): List<MediaFile> = service.mediaFileDao.getAlbumsByYear(offset, count, fromYear, toYear, folders)

    fun getAlbumsByGenre(offset: Int, count: Int, genre: String, folders: List<MusicFolder>): List<MediaFile> =
        service.mediaFileDao.getAlbumsByGenre(offset, count, genre, folders)

    fun getRandomSongsForParent(parent: MediaFile, count: Int): List<MediaFile> {
        val children = service.children.getDescendantsOf(parent, false)
        removeVideoFiles(children)
        if (children.isEmpty()) return children
        Collections.shuffle(children as MutableList<MediaFile>)
        return children.subList(0, minOf(count, children.size))
    }

    fun getRandomSongs(criteria: RandomSearchCriteria, username: String): List<MediaFile> =
        service.mediaFileDao.getRandomSongs(criteria, username)

    fun removeVideoFiles(files: List<MediaFile>) {
        (files as MutableList<MediaFile>).removeIf(MediaFile::isVideo)
    }

    fun getMediaFileStarredDate(id: Int, username: String): Instant? =
        service.mediaFileDao.getMediaFileStarredDate(id, username)

    fun populateStarredDate(mediaFiles: List<MediaFile>, username: String) {
        mediaFiles.forEach { populateStarredDate(it, username) }
    }

    fun populateStarredDate(mediaFile: MediaFile, username: String) {
        mediaFile.starredDate = service.mediaFileDao.getMediaFileStarredDate(mediaFile.id!!, username)
    }

    fun incrementPlayCount(file: MediaFile) {
        val now = Instant.now()
        file.lastPlayed = now
        file.playCount += 1
        service.updateMediaFile(file)

        val parent = service.getParentOf(file)
        if (!service.isRoot(parent!!)) {
            parent.lastPlayed = now
            parent.playCount += 1
            service.updateMediaFile(parent)
        }

        val album: Album? = service.albumDao.getAlbum(file.albumArtist!!, file.albumName!!)
        if (album != null) {
            album.lastPlayed = now
            album.incrementPlayCount()
            service.albumDao.createOrUpdateAlbum(album)
        }
    }

    fun getAlbumCount(folders: List<MusicFolder>): Int = service.mediaFileDao.getAlbumCount(folders)

    fun getPlayedAlbumCount(folders: List<MusicFolder>): Int = service.mediaFileDao.getPlayedAlbumCount(folders)

    fun getStarredAlbumCount(username: String, folders: List<MusicFolder>): Int =
        service.mediaFileDao.getStarredAlbumCount(username, folders)
}
