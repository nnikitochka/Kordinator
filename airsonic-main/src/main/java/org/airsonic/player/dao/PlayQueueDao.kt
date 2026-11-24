package org.airsonic.player.dao

import org.airsonic.player.domain.SavedPlayQueue
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/** Persists a user's saved queue and its ordered track IDs. */
class PlayQueueDao(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val rowMapper = PlayQueueMapper()

    @Throws(Exception::class)
    fun register() {
        registerInserts("play_queue", "id", INSERT_COLUMNS.split(", "), SavedPlayQueue::class.java)
    }

    fun getPlayQueue(username: String): SavedPlayQueue? = inTransaction {
        val playQueue = queryOne("select $QUERY_COLUMNS from play_queue where username=?", rowMapper, username)
            ?: return@inTransaction null
        playQueue.mediaFileIds = queryForInts(
            "select media_file_id from play_queue_file where play_queue_id = ?", playQueue.id
        )
        playQueue
    }

    fun savePlayQueue(playQueue: SavedPlayQueue) {
        inTransaction {
            update("delete from play_queue where username=?", playQueue.username)
            val id = insert("play_queue", playQueue)
            playQueue.id = id
            val batch = playQueue.mediaFileIds!!.map { mediaFileId ->
                arrayOf<Any?>(id, mediaFileId)
            }
            batchedUpdate("insert into play_queue_file(play_queue_id, media_file_id) values (?, ?)", batch)
        }
    }

    private class PlayQueueMapper : RowMapper<SavedPlayQueue> {
        override fun mapRow(rs: ResultSet, rowNum: Int): SavedPlayQueue = SavedPlayQueue(
            rs.getInt(1),
            rs.getString(2),
            null,
            rs.getInt(3),
            rs.getLong(4),
            rs.getTimestamp(5).toInstantOrNull(),
            rs.getString(6)
        )
    }

    private companion object {
        private const val INSERT_COLUMNS = "username, current_media_file_id, position_millis, changed, changed_by"
        private const val QUERY_COLUMNS = "id, $INSERT_COLUMNS"
    }
}

private fun Timestamp?.toInstantOrNull(): Instant? = this?.toInstant()
