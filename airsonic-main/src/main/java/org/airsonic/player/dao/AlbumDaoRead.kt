package org.airsonic.player.dao

import org.airsonic.player.domain.Album
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

internal object AlbumDaoSql {
    const val INSERT_COLUMNS = "path, name, artist, song_count, duration, year, genre, play_count, last_played, comment, " +
        "created, last_scanned, present, folder_id, mb_release_id"
    const val QUERY_COLUMNS = "id, $INSERT_COLUMNS"
}

/** Maps album table rows without relying on Spring JDBC. */
class AlbumRowMapper : RowMapper<Album> {
    override fun mapRow(rs: ResultSet, rowNum: Int): Album = Album(
        rs.getInt("id"),
        rs.getString("path"),
        rs.getString("name"),
        rs.getString("artist"),
        rs.getInt("song_count"),
        rs.getDouble("duration"),
        rs.getInt("year").takeIf { it != 0 },
        rs.getString("genre"),
        rs.getInt("play_count"),
        rs.getTimestamp("last_played").toInstantOrNull(),
        rs.getString("comment"),
        rs.getTimestamp("created").toInstantOrNull(),
        rs.getTimestamp("last_scanned").toInstantOrNull(),
        rs.getBoolean("present"),
        rs.getInt("folder_id"),
        rs.getString("mb_release_id")
    )
}

/** Album lookups, lists, and count queries. */
class AlbumDaoRead(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val rowMapper = AlbumRowMapper()

    fun getAlbum(id: Int): Album? = queryOne(
        "select ${AlbumDaoSql.QUERY_COLUMNS} from album where id=?", rowMapper, id
    )

    fun getAlbum(artistName: String, albumName: String): Album? = queryOne(
        "select ${AlbumDaoSql.QUERY_COLUMNS} from album where artist=? and name=?", rowMapper, artistName, albumName
    )

    fun getAlbumForFile(file: MediaFile): Album? {
        val candidates = query(
            "select ${AlbumDaoSql.QUERY_COLUMNS} from album where name=?", rowMapper, file.albumName
        )
        if (candidates.isEmpty()) return null
        candidates.firstOrNull { it.artist == file.artist }?.let { return it }
        return candidates.firstOrNull { it.path == file.parentPath && it.folderId == file.folderId }
    }

    fun getAlbumsForArtist(artist: String, musicFolders: List<MusicFolder>): List<Album> {
        if (musicFolders.isEmpty()) return emptyList()
        return namedQuery(
            "select ${AlbumDaoSql.QUERY_COLUMNS} from album where artist = :artist and present and folder_id in (:folders) order by name",
            rowMapper, mapOf("artist" to artist, "folders" to MusicFolder.toIdList(musicFolders))
        )
    }

    fun getAlphabeticalAlbums(
        offset: Int,
        count: Int,
        byArtist: Boolean,
        ignoreCase: Boolean,
        musicFolders: List<MusicFolder>
    ): List<Album> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = pagingArgs(offset, count, musicFolders)
        val orderBy = when {
            ignoreCase && byArtist -> "LOWER(artist),  LOWER(name)"
            ignoreCase -> "LOWER(name)"
            byArtist -> "artist, name"
            else -> "name"
        }
        return namedQuery(
            "select ${AlbumDaoSql.QUERY_COLUMNS} from album where present and folder_id in (:folders) " +
                "order by $orderBy, id limit :count offset :offset",
            rowMapper, args
        )
    }

    fun getAlbumCount(musicFolders: List<MusicFolder>): Int {
        if (musicFolders.isEmpty()) return 0
        return namedQueryForInt(
            "select count(*) from album where present and folder_id in (:folders)", 0,
            mapOf("folders" to MusicFolder.toIdList(musicFolders))
        ) ?: 0
    }

    fun getMostFrequentlyPlayedAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>): List<Album> =
        queryPagedAlbums(offset, count, musicFolders,
            "play_count > 0 and present", "play_count desc, id")

    fun getMostRecentlyPlayedAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>): List<Album> =
        queryPagedAlbums(offset, count, musicFolders,
            "last_played is not null and present", "last_played desc, id")

    fun getNewestAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>): List<Album> =
        queryPagedAlbums(offset, count, musicFolders, "present", "created desc, id desc")

    fun getStarredAlbums(offset: Int, count: Int, username: String, musicFolders: List<MusicFolder>): List<Album> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = pagingArgs(offset, count, musicFolders) + ("username" to username)
        return namedQuery(
            "select ${prefix(AlbumDaoSql.QUERY_COLUMNS, "album")} from starred_album, album " +
                "where album.id = starred_album.album_id and album.present and album.folder_id in (:folders) " +
                "and starred_album.username = :username order by starred_album.created desc, id limit :count offset :offset",
            rowMapper, args
        )
    }

    fun getAlbumsByGenre(offset: Int, count: Int, genre: String, musicFolders: List<MusicFolder>): List<Album> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = pagingArgs(offset, count, musicFolders) + ("genre" to genre)
        return namedQuery(
            "select ${AlbumDaoSql.QUERY_COLUMNS} from album where present and folder_id in (:folders) " +
                "and genre = :genre order by id limit :count offset :offset",
            rowMapper, args
        )
    }

    fun getAlbumsByYear(
        offset: Int,
        count: Int,
        fromYear: Int,
        toYear: Int,
        musicFolders: List<MusicFolder>
    ): List<Album> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = pagingArgs(offset, count, musicFolders) + mapOf("fromYear" to fromYear, "toYear" to toYear)
        val rangeAndOrder = if (fromYear <= toYear) {
            "year between :fromYear and :toYear order by year, id"
        } else {
            "year between :toYear and :fromYear order by year desc, id"
        }
        return namedQuery(
            "select ${AlbumDaoSql.QUERY_COLUMNS} from album where present and folder_id in (:folders) " +
                "and $rangeAndOrder limit :count offset :offset",
            rowMapper, args
        )
    }

    private fun queryPagedAlbums(
        offset: Int,
        count: Int,
        musicFolders: List<MusicFolder>,
        condition: String,
        orderBy: String
    ): List<Album> {
        if (musicFolders.isEmpty()) return emptyList()
        return namedQuery(
            "select ${AlbumDaoSql.QUERY_COLUMNS} from album where $condition and folder_id in (:folders) " +
                "order by $orderBy limit :count offset :offset",
            rowMapper, pagingArgs(offset, count, musicFolders)
        )
    }

    private fun pagingArgs(offset: Int, count: Int, musicFolders: List<MusicFolder>): Map<String, Any?> = mapOf(
        "folders" to MusicFolder.toIdList(musicFolders),
        "count" to count,
        "offset" to offset
    )
}

private fun Timestamp?.toInstantOrNull(): Instant? = this?.toInstant()
