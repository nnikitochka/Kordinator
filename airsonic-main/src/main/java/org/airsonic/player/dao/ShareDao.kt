package org.airsonic.player.dao

import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.Share
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/** Provides database services for shared media. */
class ShareDao(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val shareRowMapper = ShareRowMapper()
    private val shareFileRowMapper = ShareFileRowMapper()

    @Throws(Exception::class)
    fun register() {
        registerInserts("share", "id", INSERT_COLUMNS.split(", "), Share::class.java)
    }

    /** Creates a share and assigns its generated ID. */
    fun createShare(share: Share) {
        share.id = insert("share", share)!!
    }

    fun getAllShares(): List<Share> = query("select $QUERY_COLUMNS from share", shareRowMapper)

    fun getShareByName(shareName: String): Share? = queryOne(
        "select $QUERY_COLUMNS from share where name=?", shareRowMapper, shareName
    )

    fun getShareById(id: Int): Share? = queryOne(
        "select $QUERY_COLUMNS from share where id=?", shareRowMapper, id
    )

    fun updateShare(share: Share) {
        update(
            "update share set name=?, description=?, username=?, created=?, expires=?, last_visited=?, visit_count=? where id=?",
            share.name, share.description, share.username, share.created, share.expires,
            share.lastVisited, share.visitCount, share.id
        )
    }

    fun createSharedFiles(shareId: Int, mediaFileIds: List<Int?>?) {
        if (mediaFileIds.isNullOrEmpty()) return
        inTransaction {
            val batch = mediaFileIds.map { mediaFileId -> arrayOf<Any?>(shareId, mediaFileId) }
            batchedUpdate("insert into share_file (share_id, media_file_id) values (?, ?)", batch)
        }
    }

    fun getSharedFiles(shareId: Int, musicFolders: List<MusicFolder>): List<Int> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = mapOf(
            "shareId" to shareId,
            "folders" to MusicFolder.toIdList(musicFolders)
        )
        return namedQuery(
            "select share_file.media_file_id from share_file, media_file where share_id = :shareId and " +
                "share_file.media_file_id = media_file.id and media_file.present and media_file.folder_id in (:folders)",
            shareFileRowMapper,
            args
        )
    }

    fun deleteShare(id: Int?) {
        update("delete from share where id=?", id)
    }

    private class ShareRowMapper : RowMapper<Share> {
        override fun mapRow(rs: ResultSet, rowNum: Int): Share = Share(
            rs.getInt(1),
            rs.getString(2),
            rs.getString(3),
            rs.getString(4),
            rs.getTimestamp(5).toInstantOrNull(),
            rs.getTimestamp(6).toInstantOrNull(),
            rs.getTimestamp(7).toInstantOrNull(),
            rs.getInt(8)
        )
    }

    private class ShareFileRowMapper : RowMapper<Int> {
        override fun mapRow(rs: ResultSet, rowNum: Int): Int = rs.getInt(1)
    }

    private companion object {
        const val INSERT_COLUMNS = "name, description, username, created, expires, last_visited, visit_count"
        const val QUERY_COLUMNS = "id, $INSERT_COLUMNS"
    }
}

private fun Timestamp?.toInstantOrNull(): Instant? = this?.toInstant()
