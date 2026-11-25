package su.nnedition.kord.dao

import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.Playlist
import org.apache.commons.lang3.tuple.Pair
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.Comparator

/** Provides database services for playlists and their reader permissions. */
class PlaylistDao(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val rowMapper = PlaylistMapper()

    @Throws(Exception::class)
    fun register() {
        registerInserts("playlist", "id", INSERT_COLUMNS.split(", "), Playlist::class.java)
    }

    fun getReadablePlaylistsForUser(username: String): List<Playlist> {
        val writable = getWritablePlaylistsForUser(username)
        val public = query("select $QUERY_COLUMNS from playlist where is_public", rowMapper)
        val shared = query(
            "select ${prefix(QUERY_COLUMNS, "playlist")} from playlist, playlist_user " +
                "where playlist.id = playlist_user.playlist_id and playlist.username != ? " +
                "and playlist_user.username = ?",
            rowMapper, username, username
        )
        return (writable + public + shared).distinctBy { it.id }.sortedWith(sorter)
    }

    fun getWritablePlaylistsForUser(username: String): List<Playlist> = query(
        "select $QUERY_COLUMNS from playlist where username=?", rowMapper, username
    ).sortedWith(sorter)

    fun getPlaylist(id: Int): Playlist? = queryOne(
        "select $QUERY_COLUMNS from playlist where id=?", rowMapper, id
    )

    fun getAllPlaylists(): List<Playlist> = query(
        "select $QUERY_COLUMNS from playlist", rowMapper
    ).sortedWith(sorter)

    fun createPlaylist(playlist: Playlist) {
        playlist.id = insert("playlist", playlist)!!
    }

    fun setFilesInPlaylist(id: Int, files: List<MediaFile>) {
        inTransaction {
            update("delete from playlist_file where playlist_id=?", id)
            val batch = files.map { file -> arrayOf<Any?>(id, file.id) }
            batchedUpdate("insert into playlist_file (playlist_id, media_file_id) values (?, ?)", batch)
        }
    }

    fun getPlaylistFileStats(id: Int): Pair<Int, Double>? = queryOne(
        "select count(*), sum(duration) from media_file m, playlist_file p " +
            "where p.media_file_id = m.id and p.playlist_id=?",
        RowMapper { rs: ResultSet, _ -> Pair.of(rs.getInt(1), rs.getDouble(2)) }, id
    )

    fun getPlaylistUsers(playlistId: Int): List<String> = queryForStrings(
        "select username from playlist_user where playlist_id=?", playlistId
    )

    fun addPlaylistUser(playlistId: Int, username: String) {
        if (username !in getPlaylistUsers(playlistId)) {
            update("insert into playlist_user(playlist_id,username) values (?,?)", playlistId, username)
        }
    }

    fun deletePlaylistUser(playlistId: Int, username: String) {
        update("delete from playlist_user where playlist_id=? and username=?", playlistId, username)
    }

    fun deletePlaylist(id: Int) {
        update("delete from playlist where id=?", id)
    }

    fun updatePlaylist(playlist: Playlist) {
        update(
            "update playlist set username=?, is_public=?, name=?, comment=?, changed=?, imported_from=?, " +
                "file_count=?, duration=? where id=?",
            playlist.username, playlist.shared, playlist.name, playlist.comment, Instant.now(),
            playlist.importedFrom, playlist.fileCount, playlist.duration, playlist.id
        )
    }

    private class PlaylistMapper : RowMapper<Playlist> {
        override fun mapRow(rs: ResultSet, rowNum: Int): Playlist = Playlist(
            rs.getInt(1),
            rs.getString(2),
            rs.getBoolean(3),
            rs.getString(4),
            rs.getString(5),
            rs.getInt(6),
            rs.getDouble(7),
            rs.getTimestamp(8).toInstantOrNull(),
            rs.getTimestamp(9).toInstantOrNull(),
            rs.getString(10)
        )
    }

    private companion object {
        private const val INSERT_COLUMNS = "username, is_public, name, comment, file_count, duration, " +
            "created, changed, imported_from"
        private const val QUERY_COLUMNS = "id, $INSERT_COLUMNS"
        private val sorter = Comparator<Playlist> { first, second -> first.name!!.compareTo(second.name!!) }
    }
}

private fun Timestamp?.toInstantOrNull(): Instant? = this?.toInstant()
