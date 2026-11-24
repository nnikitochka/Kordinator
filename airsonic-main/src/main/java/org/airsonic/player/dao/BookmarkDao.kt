package org.airsonic.player.dao

import org.airsonic.player.domain.Bookmark
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock

/** Provides database services for media file bookmarks. */
class BookmarkDao(jdbcHelper: JdbcHelper, private val transactionManager: TransactionManager) :
    AbstractDao(jdbcHelper) {
    private val locks = Array(LOCK_STRIPES) { ReentrantLock() }
    private val rowMapper = BookmarkRowMapper()

    @Throws(Exception::class)
    fun register() {
        registerInserts("bookmark", "id", INSERT_COLUMNS.split(", "), Bookmark::class.java)
    }

    fun getBookmarks(): List<Bookmark> = query(
        "select $QUERY_COLUMNS from bookmark", rowMapper
    )

    fun getBookmarks(username: String): List<Bookmark> = query(
        "select $QUERY_COLUMNS from bookmark where username=?", rowMapper, username
    )

    fun getBookmark(username: String, mediaFileId: Int): Bookmark? = queryOne(
        "select $QUERY_COLUMNS from bookmark where username=? and media_file_id=?", rowMapper, username, mediaFileId
    )

    /** Serialize create/update by user and media-file ID until the transaction commits. */
    fun createOrUpdateBookmark(bookmark: Bookmark) {
        val lock = getLock(bookmark.username, bookmark.mediaFileId)
        lock.lock()
        try {
            transactionManager.executeWithoutResult(TX_DEF) {
                createOrUpdateBookmarkInTransaction(bookmark)
            }
        } finally {
            lock.unlock()
        }
    }

    private fun createOrUpdateBookmarkInTransaction(bookmark: Bookmark) {
        val existing = getBookmark(bookmark.username, bookmark.mediaFileId)
        if (existing == null) {
            bookmark.id = insert("bookmark", bookmark)!!
            return
        }

        bookmark.id = existing.id
        update(
            "update bookmark set position_millis=?, comment=?, changed=? where id=?",
            bookmark.positionMillis, bookmark.comment, bookmark.changed, existing.id
        )
    }

    fun deleteBookmark(username: String, mediaFileId: Int) {
        val lock = getLock(username, mediaFileId)
        lock.lock()
        try {
            transactionManager.executeWithoutResult(TX_DEF) {
                update("delete from bookmark where username=? and media_file_id=?", username, mediaFileId)
            }
        } finally {
            lock.unlock()
        }
    }

    private fun getLock(username: String?, mediaFileId: Int): ReentrantLock =
        locks[Math.floorMod(31 * mediaFileId + (username?.hashCode() ?: 0), LOCK_STRIPES)]

    private class BookmarkRowMapper : RowMapper<Bookmark> {
        override fun mapRow(rs: ResultSet, rowNum: Int): Bookmark = Bookmark(
            rs.getInt(1),
            rs.getInt(2),
            rs.getLong(3),
            rs.getString(4),
            rs.getString(5),
            rs.getTimestamp(6).toInstantOrNull(),
            rs.getTimestamp(7).toInstantOrNull()
        )
    }

    private companion object {
        private const val INSERT_COLUMNS = "media_file_id, position_millis, username, comment, created, changed"
        private const val QUERY_COLUMNS = "id, $INSERT_COLUMNS"
        private const val LOCK_STRIPES = 256
        private val TX_DEF = TransactionManager.TransactionDefinition(
            TransactionManager.PROPAGATION_REQUIRES_NEW,
            TransactionManager.ISOLATION_READ_COMMITTED
        )
    }
}

private fun Timestamp?.toInstantOrNull(): Instant? = this?.toInstant()
