package su.nnedition.kord.dao

import su.nnedition.kord.domain.CoverArt
import su.nnedition.kord.domain.CoverArt.EntityType
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/** Persists cover-art paths, ownership, timestamps, and computed accent colors. */
class CoverArtDao(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val rowMapper = CoverArtRowMapper()

    @Throws(Exception::class)
    fun register() {
        registerInserts("cover_art", null, COLUMNS.split(", "), CoverArt::class.java)
    }

    fun upsert(art: CoverArt) {
        inTransaction(TransactionManager.ISOLATION_READ_COMMITTED) {
            if (update(art) == 0) insert("cover_art", art)
        }
    }

    fun update(art: CoverArt): Int = update(
        "update cover_art set path=?, folder_id=?, overridden=?, updated=?, accent_color=? " +
            "where entity_id=? and entity_type=?",
        art.path, art.folderId, art.overridden, art.updated, art.accentColor, art.entityId, art.entityType!!.toString()
    )

    fun get(type: EntityType?, id: Int): CoverArt? = queryOne(
        "select * from cover_art where entity_id=? and entity_type=?", rowMapper, id, type
    )

    /** Returns saved rows whose accent color is absent or has not yet been computed. */
    fun getCoverArtWithoutAccentColor(): List<CoverArt> = query(
        "select * from cover_art where accent_color is null", rowMapper
    )

    fun delete(type: EntityType?, id: Int) {
        update("delete from cover_art where entity_id=? and entity_type=?", id, type)
    }

    fun expunge() {
        inTransaction(TransactionManager.ISOLATION_READ_COMMITTED) {
            update("delete from cover_art where entity_type='MEDIA_FILE' and entity_id in " +
                "(select ca.entity_id from cover_art ca left join media_file m on ca.entity_id=m.id " +
                "where m.id is null and ca.entity_type='MEDIA_FILE')")
            update("delete from cover_art where entity_type='ALBUM' and entity_id in " +
                "(select ca.entity_id from cover_art ca left join album a on ca.entity_id=a.id " +
                "where a.id is null and ca.entity_type='ALBUM')")
            update("delete from cover_art where entity_type='ARTTIST' and entity_id in " +
                "(select ca.entity_id from cover_art ca left join artist a on ca.entity_id=a.id " +
                "where a.id is null and ca.entity_type='ARTIST')")
        }
    }

    private class CoverArtRowMapper : RowMapper<CoverArt> {
        override fun mapRow(rs: ResultSet, rowNum: Int): CoverArt = CoverArt(
            rs.getInt("entity_id"),
            EntityType.valueOf(rs.getString("entity_type")),
            rs.getString("path"),
            rs.getObject("folder_id") as Int?,
            rs.getBoolean("overridden"),
            rs.getTimestamp("created").toInstantOrNull(),
            rs.getTimestamp("updated").toInstantOrNull()
        ).apply {
            accentColor = rs.getString("accent_color")
        }
    }

    private companion object {
        private const val COLUMNS = "entity_id, entity_type, path, folder_id, overridden, created, updated, accent_color"
    }
}

private fun Timestamp?.toInstantOrNull(): Instant? = this?.toInstant()
