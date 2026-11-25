package su.nnedition.kord.dao

import su.nnedition.kord.domain.Artist
import su.nnedition.kord.domain.MusicFolder
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/** Provides database services for artists. */
class ArtistDao(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val rowMapper = ArtistRowMapper()

    fun getArtist(artistName: String): Artist? = queryOne(
        "select $QUERY_COLUMNS from artist where name=?", rowMapper, artistName
    )

    fun getArtist(artistName: String, musicFolders: List<MusicFolder>): Artist? {
        if (musicFolders.isEmpty()) return null
        return namedQueryOne(
            "select $QUERY_COLUMNS from artist where name = :name and folder_id in (:folders)",
            rowMapper,
            mapOf("name" to artistName, "folders" to MusicFolder.toIdList(musicFolders))
        )
    }

    fun getArtist(id: Int): Artist? = queryOne(
        "select $QUERY_COLUMNS from artist where id=?", rowMapper, id
    )

    fun createOrUpdateArtist(artist: Artist) {
        inTransaction(TransactionManager.ISOLATION_READ_COMMITTED) {
            val updated = update(
                "update artist set album_count=?, last_scanned=?, present=?, folder_id=? where name=?",
                artist.albumCount, artist.lastScanned, artist.isPresent, artist.folderId, artist.name
            )
            if (updated == 0) {
                update(
                    "insert into artist ($INSERT_COLUMNS) values (${questionMarks(INSERT_COLUMNS)})",
                    artist.name, artist.albumCount, artist.lastScanned, artist.isPresent, artist.folderId
                )
            }
            artist.id = queryForInt("select id from artist where name=?", null, artist.name)!!
        }
    }

    fun getAlphabetialArtists(offset: Int, count: Int, musicFolders: List<MusicFolder>): List<Artist> {
        if (musicFolders.isEmpty()) return emptyList()
        return namedQuery(
            "select $QUERY_COLUMNS from artist where present and folder_id in (:folders) " +
                "order by name, id limit :count offset :offset",
            rowMapper,
            pagingArgs(offset, count, musicFolders)
        )
    }

    fun getStarredArtists(
        offset: Int,
        count: Int,
        username: String,
        musicFolders: List<MusicFolder>
    ): List<Artist> {
        if (musicFolders.isEmpty()) return emptyList()
        return namedQuery(
            "select ${prefix(QUERY_COLUMNS, "artist")} from starred_artist, artist " +
                "where artist.id = starred_artist.artist_id and artist.present and " +
                "starred_artist.username = :username and artist.folder_id in (:folders) " +
                "order by starred_artist.created desc, starred_artist.id limit :count offset :offset",
            rowMapper,
            pagingArgs(offset, count, musicFolders) + ("username" to username)
        )
    }

    fun markPresent(artistName: String, lastScanned: Instant) {
        update("update artist set present=?, last_scanned = ? where name=?", true, lastScanned, artistName)
    }

    fun markNonPresent(lastScanned: Instant) {
        update("update artist set present=false where last_scanned < ? and present", lastScanned)
    }

    fun getExpungeCandidates(): List<Int> = queryForInts("select id from artist where not present")

    fun expunge() {
        update("delete from artist where not present")
    }

    fun starArtist(artistId: Int, username: String) {
        unstarArtist(artistId, username)
        update("insert into starred_artist(artist_id, username, created) values (?,?,?)", artistId, username, Instant.now())
    }

    fun unstarArtist(artistId: Int, username: String) {
        update("delete from starred_artist where artist_id=? and username=?", artistId, username)
    }

    fun getArtistStarredDate(artistId: Int, username: String): Instant? = queryForInstant(
        "select created from starred_artist where artist_id=? and username=?", null, artistId, username
    )

    private fun pagingArgs(offset: Int, count: Int, musicFolders: List<MusicFolder>): Map<String, Any?> = mapOf(
        "folders" to MusicFolder.toIdList(musicFolders),
        "count" to count,
        "offset" to offset
    )

    private class ArtistRowMapper : RowMapper<Artist> {
        override fun mapRow(rs: ResultSet, rowNum: Int): Artist = Artist(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getInt("album_count"),
            rs.getTimestamp("last_scanned").toInstantOrNull(),
            rs.getBoolean("present"),
            rs.getInt("folder_id")
        )
    }

    private companion object {
        private const val INSERT_COLUMNS = "name, album_count, last_scanned, present, folder_id"
        private const val QUERY_COLUMNS = "id, $INSERT_COLUMNS"
    }
}

private fun Timestamp?.toInstantOrNull(): Instant? = this?.toInstant()
