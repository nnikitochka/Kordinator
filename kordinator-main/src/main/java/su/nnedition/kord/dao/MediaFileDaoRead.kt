package su.nnedition.kord.dao

import su.nnedition.kord.domain.Genre
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.Optional

internal object MediaFileDaoSql {
    const val INSERT_COLUMNS = "path, folder_id, type, format, title, album, artist, album_artist, disc_number, " +
        "track_number, year, genre, bit_rate, variable_bit_rate, duration, file_size, width, height, " +
        "parent_path, play_count, last_played, comment, created, changed, last_scanned, children_last_updated, present, " +
        "version, mb_release_id, mb_recording_id"
    const val QUERY_COLUMNS = "id, $INSERT_COLUMNS"
    const val GENRE_COLUMNS = "name, song_count, album_count"
}

/** JDBC row mapper for the complete media-file projection. */
class MediaFileRowMapper : RowMapper<MediaFile> {
    override fun mapRow(rs: ResultSet, rowNum: Int): MediaFile = MediaFile(
        rs.getInt("id"),
        rs.getString("path"),
        rs.getInt("folder_id"),
        MediaFile.MediaType.valueOf(rs.getString("type")),
        rs.getString("format"),
        rs.getString("title"),
        rs.getString("album"),
        rs.getString("artist"),
        rs.getString("album_artist"),
        rs.getInt("disc_number").takeIf { it != 0 },
        rs.getInt("track_number").takeIf { it != 0 },
        rs.getInt("year").takeIf { it != 0 },
        rs.getString("genre"),
        rs.getInt("bit_rate").takeIf { it != 0 },
        rs.getBoolean("variable_bit_rate"),
        rs.getDouble("duration"),
        rs.getLong("file_size").takeIf { it != 0L },
        rs.getInt("width").takeIf { it != 0 },
        rs.getInt("height").takeIf { it != 0 },
        rs.getString("parent_path"),
        rs.getInt("play_count"),
        rs.getTimestamp("last_played").toInstantOrNull(),
        rs.getString("comment"),
        rs.getTimestamp("created").toInstantOrNull(),
        rs.getTimestamp("changed").toInstantOrNull(),
        rs.getTimestamp("last_scanned").toInstantOrNull(),
        rs.getTimestamp("children_last_updated").toInstantOrNull(),
        rs.getBoolean("present"),
        rs.getInt("version"),
        rs.getString("mb_release_id"),
        rs.getString("mb_recording_id")
    )
}

/** Mapper for the optional play-count information projection. */
class MusicFileInfoRowMapper : RowMapper<MediaFile> {
    override fun mapRow(rs: ResultSet, rowNum: Int): MediaFile = MediaFile().apply {
        playCount = rs.getInt(1)
        lastPlayed = rs.getTimestamp(2).toInstantOrNull()
        comment = rs.getString(3)
    }
}

/** Mapper for aggregated genre rows. */
class GenreRowMapper : RowMapper<Genre> {
    override fun mapRow(rs: ResultSet, rowNum: Int): Genre = Genre(rs.getString(1), rs.getInt(2), rs.getInt(3))
}

/** Read-only media-file lookups shared by the public DAO facade. */
class MediaFileDaoRead(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val rowMapper = MediaFileRowMapper()
    private val musicFileInfoRowMapper = MusicFileInfoRowMapper()

    fun getMediaFile(path: String, folderId: Int): MediaFile? =
        queryOne("select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where path=? and folder_id=?", rowMapper, path, folderId)

    fun getMediaFile(id: Int): MediaFile? =
        queryOne("select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where id=?", rowMapper, id)

    fun getMediaFilesByRelativePath(path: String): List<MediaFile> =
        query("select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where path=?", rowMapper, path)

    fun getChildrenOf(path: String, folderId: Int, onlyPresent: Boolean): List<MediaFile> = query(
        "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where parent_path=? and folder_id=?" +
            if (onlyPresent) " and present" else "",
        rowMapper, path, folderId
    )

    fun getFilesInPlaylist(playlistId: Int): List<MediaFile> = query(
        "select ${prefix(MediaFileDaoSql.QUERY_COLUMNS, "media_file")} from playlist_file, media_file where " +
            "media_file.id = playlist_file.media_file_id and playlist_file.playlist_id = ? order by playlist_file.id",
        rowMapper, playlistId
    )

    fun getSongsForAlbum(artist: String, album: String): List<MediaFile> = query(
        "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where album_artist=? and album=? and present " +
            "and type in (?,?) order by disc_number, track_number",
        rowMapper, artist, album, MediaFile.MediaType.MUSIC.name, MediaFile.MediaType.AUDIOBOOK.name
    )

    fun getVideos(count: Int, offset: Int, musicFolders: List<MusicFolder>): List<MediaFile> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = mapOf(
            "type" to MediaFile.MediaType.VIDEO.name,
            "folders" to MusicFolder.toIdList(musicFolders),
            "count" to count,
            "offset" to offset
        )
        return namedQuery(
            "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where type = :type and present and folder_id in (:folders)" +
                " order by title limit :count offset :offset",
            rowMapper, args
        )
    }

    fun getArtistByName(name: String, musicFolders: List<MusicFolder>): MediaFile? {
        if (musicFolders.isEmpty()) return null
        val args = mapOf(
            "type" to MediaFile.MediaType.DIRECTORY.name,
            "name" to name,
            "folders" to MusicFolder.toIdList(musicFolders)
        )
        return namedQueryOne(
            "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where type = :type and artist = :name " +
                "and present and folder_id in (:folders)", rowMapper, args
        )
    }

    fun getMusicFileInfo(path: String): MediaFile? = queryOne(
        "select play_count, last_played, comment from music_file_info where path=?", musicFileInfoRowMapper, path
    )

    fun getMediaFileCount(folderId: Int?): Int =
        queryForInt("select count(*) from media_file where folder_id=?", 0, folderId) ?: 0
}

private fun Timestamp?.toInstantOrNull(): Instant? = this?.toInstant()
