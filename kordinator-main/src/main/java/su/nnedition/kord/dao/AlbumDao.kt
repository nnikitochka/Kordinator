package su.nnedition.kord.dao

import su.nnedition.kord.domain.Album
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import java.time.Instant

/** Public album DAO facade split across query and mutation responsibilities. */
class AlbumDao(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val reads = AlbumDaoRead(jdbcHelper)
    private val mutations = AlbumDaoMutation(jdbcHelper)

    fun getAlbum(id: Int): Album? = reads.getAlbum(id)
    fun getAlbum(artistName: String, albumName: String): Album? = reads.getAlbum(artistName, albumName)
    fun getAlbumForFile(file: MediaFile): Album? = reads.getAlbumForFile(file)
    fun getAlbumsForArtist(artist: String, musicFolders: List<MusicFolder>) = reads.getAlbumsForArtist(artist, musicFolders)
    fun createOrUpdateAlbum(album: Album) = mutations.createOrUpdateAlbum(album)
    fun getAlphabeticalAlbums(offset: Int, count: Int, byArtist: Boolean, ignoreCase: Boolean, musicFolders: List<MusicFolder>) =
        reads.getAlphabeticalAlbums(offset, count, byArtist, ignoreCase, musicFolders)
    fun getAlbumCount(musicFolders: List<MusicFolder>) = reads.getAlbumCount(musicFolders)
    fun getMostFrequentlyPlayedAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>) =
        reads.getMostFrequentlyPlayedAlbums(offset, count, musicFolders)
    fun getMostRecentlyPlayedAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>) =
        reads.getMostRecentlyPlayedAlbums(offset, count, musicFolders)
    fun getNewestAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>) = reads.getNewestAlbums(offset, count, musicFolders)
    fun getStarredAlbums(offset: Int, count: Int, username: String, musicFolders: List<MusicFolder>) =
        reads.getStarredAlbums(offset, count, username, musicFolders)
    fun getAlbumsByGenre(offset: Int, count: Int, genre: String, musicFolders: List<MusicFolder>) =
        reads.getAlbumsByGenre(offset, count, genre, musicFolders)
    fun getAlbumsByYear(offset: Int, count: Int, fromYear: Int, toYear: Int, musicFolders: List<MusicFolder>) =
        reads.getAlbumsByYear(offset, count, fromYear, toYear, musicFolders)
    fun markNonPresent(lastScanned: Instant) = mutations.markNonPresent(lastScanned)
    fun getExpungeCandidates() = mutations.getExpungeCandidates()
    fun expunge() = mutations.expunge()
    fun starAlbum(albumId: Int, username: String) = mutations.starAlbum(albumId, username)
    fun unstarAlbum(albumId: Int, username: String) = mutations.unstarAlbum(albumId, username)
    fun getAlbumStarredDate(albumId: Int, username: String): Instant? = mutations.getAlbumStarredDate(albumId, username)
}
