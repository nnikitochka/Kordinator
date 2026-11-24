package org.airsonic.player.dao

import org.airsonic.player.domain.Genre
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.RandomSearchCriteria
import java.time.Instant
import java.util.function.Consumer

/** Public DAO facade for media-file reads, writes, scanner state and per-user queries. */
class MediaFileDao(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val reads = MediaFileDaoRead(jdbcHelper)
    private val albums = MediaFileDaoAlbumQueries(jdbcHelper)
    private val users = MediaFileDaoUserQueries(jdbcHelper)
    private val mutations = MediaFileDaoMutation(jdbcHelper)

    fun getMediaFile(path: String, folderId: Int): MediaFile? = reads.getMediaFile(path, folderId)
    fun getMediaFile(id: Int): MediaFile? = reads.getMediaFile(id)
    fun getMediaFilesByRelativePath(path: String): List<MediaFile> = reads.getMediaFilesByRelativePath(path)
    fun getChildrenOf(path: String, folderId: Int, onlyPresent: Boolean): List<MediaFile> = reads.getChildrenOf(path, folderId, onlyPresent)
    fun getFilesInPlaylist(playlistId: Int): List<MediaFile> = reads.getFilesInPlaylist(playlistId)
    fun getSongsForAlbum(artist: String, album: String): List<MediaFile> = reads.getSongsForAlbum(artist, album)
    fun getVideos(count: Int, offset: Int, musicFolders: List<MusicFolder>): List<MediaFile> = reads.getVideos(count, offset, musicFolders)
    fun getArtistByName(name: String, musicFolders: List<MusicFolder>): MediaFile? = reads.getArtistByName(name, musicFolders)
    fun getMusicFileInfo(path: String): MediaFile? = reads.getMusicFileInfo(path)
    fun getMediaFileCount(folderId: Int?): Int = reads.getMediaFileCount(folderId)

    fun createOrUpdateMediaFile(file: MediaFile, preInsertionCallback: Consumer<MediaFile>) =
        mutations.createOrUpdateMediaFile(file, preInsertionCallback)

    fun deleteMediaFile(path: String, folderId: Int?) = mutations.deleteMediaFiles(listOf(path), folderId)
    fun deleteMediaFiles(paths: Collection<String>, folderId: Int?) = mutations.deleteMediaFiles(paths, folderId)
    fun deleteMediaFiles(folderId: Int?) = mutations.deleteMediaFiles(folderId)
    fun getGenres(sortByAlbum: Boolean): List<Genre> = albums.getGenres(sortByAlbum)
    fun updateGenres(genres: List<Genre>): Boolean = albums.updateGenres(genres)

    fun getMostFrequentlyPlayedAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>) =
        albums.getMostFrequentlyPlayedAlbums(offset, count, musicFolders)
    fun getMostRecentlyPlayedAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>) =
        albums.getMostRecentlyPlayedAlbums(offset, count, musicFolders)
    fun getNewestAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>) = albums.getNewestAlbums(offset, count, musicFolders)
    fun getAlphabeticalAlbums(offset: Int, count: Int, byArtist: Boolean, musicFolders: List<MusicFolder>) =
        albums.getAlphabeticalAlbums(offset, count, byArtist, musicFolders)
    fun getAlbumsByYear(offset: Int, count: Int, fromYear: Int, toYear: Int, musicFolders: List<MusicFolder>) =
        albums.getAlbumsByYear(offset, count, fromYear, toYear, musicFolders)
    fun getAlbumsByGenre(offset: Int, count: Int, genre: String, musicFolders: List<MusicFolder>) =
        albums.getAlbumsByGenre(offset, count, genre, musicFolders)
    fun getSongsByGenre(genre: String, offset: Int, count: Int, musicFolders: List<MusicFolder>) =
        albums.getSongsByGenre(genre, offset, count, musicFolders)
    fun getSongsByArtist(artist: String, offset: Int, count: Int) = albums.getSongsByArtist(artist, offset, count)
    fun getSongByArtistAndTitle(artist: String?, title: String?, musicFolders: List<MusicFolder>) =
        albums.getSongByArtistAndTitle(artist, title, musicFolders)

    fun getStarredAlbums(offset: Int, count: Int, username: String, musicFolders: List<MusicFolder>) =
        users.getStarredAlbums(offset, count, username, musicFolders)
    fun getStarredDirectories(offset: Int, count: Int, username: String, musicFolders: List<MusicFolder>) =
        users.getStarredDirectories(offset, count, username, musicFolders)
    fun getStarredFiles(offset: Int, count: Int, username: String, musicFolders: List<MusicFolder>) =
        users.getStarredFiles(offset, count, username, musicFolders)
    fun getRandomSongs(criteria: RandomSearchCriteria, username: String) = users.getRandomSongs(criteria, username)
    fun getAlbumCount(musicFolders: List<MusicFolder>) = users.getAlbumCount(musicFolders)
    fun getPlayedAlbumCount(musicFolders: List<MusicFolder>) = users.getPlayedAlbumCount(musicFolders)
    fun getStarredAlbumCount(username: String, musicFolders: List<MusicFolder>) = users.getStarredAlbumCount(username, musicFolders)
    fun starMediaFiles(ids: List<Int>, username: String) = users.starMediaFiles(ids, username)
    fun unstarMediaFiles(ids: List<Int>, username: String) = users.unstarMediaFiles(ids, username)
    fun starMediaFile(id: Int, username: String) = users.starMediaFiles(listOf(id), username)
    fun unstarMediaFile(id: Int, username: String) = users.unstarMediaFiles(listOf(id), username)
    fun getMediaFileStarredDate(id: Int, username: String): Instant? = users.getMediaFileStarredDate(id, username)

    fun markPresent(paths: Map<Int, Set<String>>, lastScanned: Instant) = mutations.markPresent(paths, lastScanned)
    fun markNonPresent(lastScanned: Instant) = mutations.markNonPresent(lastScanned)
    fun getArtistExpungeCandidates() = mutations.expungeCandidates(MediaFile.MediaType.DIRECTORY.name)
    fun getAlbumExpungeCandidates() = mutations.expungeCandidates(MediaFile.MediaType.ALBUM.name)
    fun getSongExpungeCandidates() = mutations.expungeSongCandidates()
    fun expunge() = mutations.expunge()

    companion object {
        const val VERSION = 4
    }
}
