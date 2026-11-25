package su.nnedition.kord.dao

import su.nnedition.kord.domain.Album
import java.time.Instant

/** Persists album metadata, scanner state, and per-user album stars. */
class AlbumDaoMutation(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    fun createOrUpdateAlbum(album: Album) {
        inTransaction(TransactionManager.ISOLATION_READ_COMMITTED) {
            val updated = update(
                "update album set path=?, song_count=?, duration=?, year=?, genre=?, play_count=?, last_played=?, " +
                    "comment=?, created=?, last_scanned=?, present=?, folder_id=?, mb_release_id=? where artist=? and name=?",
                album.path, album.songCount, album.duration, album.year, album.genre, album.playCount, album.lastPlayed,
                album.comment, album.created, album.lastScanned, album.isPresent, album.folderId,
                album.musicBrainzReleaseId, album.artist, album.name
            )
            if (updated == 0) {
                update(
                    "insert into album (${AlbumDaoSql.INSERT_COLUMNS}) values (${questionMarks(AlbumDaoSql.INSERT_COLUMNS)})",
                    album.path, album.name, album.artist, album.songCount, album.duration, album.year, album.genre,
                    album.playCount, album.lastPlayed, album.comment, album.created, album.lastScanned, album.isPresent,
                    album.folderId, album.musicBrainzReleaseId
                )
            }
            album.id = queryForInt("select id from album where artist=? and name=?", null, album.artist, album.name)!!
        }
    }

    fun markNonPresent(lastScanned: Instant) {
        update("update album set present=false where last_scanned < ? and present", lastScanned)
    }

    fun getExpungeCandidates(): List<Int> = queryForInts("select id from album where not present")

    fun expunge() {
        update("delete from album where not present")
    }

    fun starAlbum(albumId: Int, username: String) {
        unstarAlbum(albumId, username)
        update("insert into starred_album(album_id, username, created) values (?,?,?)", albumId, username, Instant.now())
    }

    fun unstarAlbum(albumId: Int, username: String) {
        update("delete from starred_album where album_id=? and username=?", albumId, username)
    }

    fun getAlbumStarredDate(albumId: Int, username: String): Instant? = queryForInstant(
        "select created from starred_album where album_id=? and username=?", null, albumId, username
    )
}
