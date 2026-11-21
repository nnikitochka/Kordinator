package org.airsonic.player.service

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

data class StandaloneBookmark(
    val track: StandaloneTrack,
    val positionMillis: Long,
    val comment: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Stores track resume positions for standalone Ktor accounts. */
class StandaloneBookmarkService(
    private val dataSource: DataSource,
    private val mediaCatalog: KtorCoreMediaCatalogService,
) {
    fun list(userId: Long, accessibleLibraryIds: Set<Long>? = null): List<StandaloneBookmark> {
        val rows = dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT track_id, position_ms, comment, created_at, updated_at FROM track_bookmark WHERE user_id = ? ORDER BY updated_at DESC, track_id",
            ).use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(
                                BookmarkRow(
                                    result.getLong("track_id"), result.getLong("position_ms"), result.getString("comment"),
                                    result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant(),
                                ),
                            )
                        }
                    }
                }
            }
        }
        return rows.mapNotNull { row ->
            mediaCatalog.track(row.trackId, accessibleLibraryIds)?.let {
                StandaloneBookmark(it, row.positionMillis, row.comment, row.createdAt, row.updatedAt)
            }
        }
    }

    fun set(
        userId: Long,
        trackId: Long,
        positionMillis: Long,
        comment: String?,
        accessibleLibraryIds: Set<Long>? = null,
    ): Boolean {
        require(positionMillis >= 0) { "Bookmark position must be non-negative" }
        require(comment == null || comment.length <= 1024) { "Bookmark comment must be at most 1024 characters" }
        val track = mediaCatalog.track(trackId, accessibleLibraryIds) ?: return false
        val durationMillis = track.durationSeconds.toLong() * 1000L
        if (durationMillis > 0 && durationMillis - positionMillis < 60_000L) {
            delete(userId, trackId)
            return true
        }
        val now = Timestamp.from(Instant.now())
        return dataSource.connection.use { connection ->
            val updated = connection.prepareStatement(
                "UPDATE track_bookmark SET position_ms = ?, comment = ?, updated_at = ? WHERE user_id = ? AND track_id = ?",
            ).use { statement ->
                statement.setLong(1, positionMillis)
                statement.setString(2, comment)
                statement.setTimestamp(3, now)
                statement.setLong(4, userId)
                statement.setLong(5, trackId)
                statement.executeUpdate()
            }
            if (updated == 0) {
                connection.prepareStatement(
                    "INSERT INTO track_bookmark (user_id, track_id, position_ms, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.setLong(2, trackId)
                    statement.setLong(3, positionMillis)
                    statement.setString(4, comment)
                    statement.setTimestamp(5, now)
                    statement.setTimestamp(6, now)
                    statement.executeUpdate()
                }
            }
            true
        }
    }

    fun delete(userId: Long, trackId: Long): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM track_bookmark WHERE user_id = ? AND track_id = ?").use { statement ->
            statement.setLong(1, userId)
            statement.setLong(2, trackId)
            statement.executeUpdate() > 0
        }
    }

    private data class BookmarkRow(
        val trackId: Long,
        val positionMillis: Long,
        val comment: String?,
        val createdAt: Instant,
        val updatedAt: Instant,
    )
}
