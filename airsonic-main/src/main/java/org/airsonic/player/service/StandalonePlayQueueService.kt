package org.airsonic.player.service

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

data class StandalonePlayQueue(
    val trackIds: List<Long>,
    val currentTrackId: Long?,
    val positionMillis: Long,
    val updatedAt: Instant?,
)

/** Persists a user's restorable playback queue independently of any live player connection. */
class StandalonePlayQueueService(
    private val dataSource: DataSource,
    private val mediaCatalog: KtorCoreMediaCatalogService,
) {
    fun get(userId: Long): StandalonePlayQueue = dataSource.connection.use { connection ->
        val queue = connection.prepareStatement(
            "SELECT current_track_id, position_ms, updated_at FROM user_play_queue WHERE user_id = ?",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else {
                    val currentTrackId = rows.getLong("current_track_id").let { if (rows.wasNull()) null else it }
                    Triple(currentTrackId, rows.getLong("position_ms"), rows.getTimestamp("updated_at").toInstant())
                }
            }
        }
        val trackIds = if (queue == null) emptyList() else connection.prepareStatement(
            "SELECT track_id FROM user_play_queue_track WHERE user_id = ? ORDER BY position",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } }
        }
        StandalonePlayQueue(trackIds, queue?.first, queue?.second ?: 0L, queue?.third)
    }

    fun save(
        userId: Long,
        trackIds: List<Long>,
        currentTrackId: Long?,
        positionMillis: Long,
        accessibleLibraryIds: Set<Long>? = null,
    ): StandalonePlayQueue {
        require(trackIds.size <= MAX_TRACKS) { "Queue contains too many tracks" }
        require(trackIds.all { it >= 0 }) { "Track IDs must be non-negative" }
        require(positionMillis >= 0) { "Playback position must be non-negative" }
        require(currentTrackId == null || currentTrackId >= 0) { "Current track ID must be non-negative" }
        require(currentTrackId == null || currentTrackId in trackIds) { "Current track must be present in the queue" }
        val uniqueTrackIds = trackIds.distinct()
        val availableTrackIds = mediaCatalog.tracksByCoreIds(uniqueTrackIds, accessibleLibraryIds).map { it.id }.toSet()
        require(availableTrackIds.size == uniqueTrackIds.size) { "Track does not exist or is not accessible" }

        val now = Instant.now()
        dataSource.connection.use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val exists = connection.prepareStatement("SELECT 1 FROM user_play_queue WHERE user_id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.executeQuery().use { it.next() }
                }
                if (exists) {
                    connection.prepareStatement(
                        "UPDATE user_play_queue SET current_track_id = ?, position_ms = ?, updated_at = ? WHERE user_id = ?",
                    ).use { statement ->
                        statement.setObject(1, currentTrackId)
                        statement.setLong(2, positionMillis)
                        statement.setTimestamp(3, Timestamp.from(now))
                        statement.setLong(4, userId)
                        check(statement.executeUpdate() == 1) { "Queue disappeared during update" }
                    }
                } else {
                    connection.prepareStatement(
                        "INSERT INTO user_play_queue (user_id, current_track_id, position_ms, updated_at) VALUES (?, ?, ?, ?)",
                    ).use { statement ->
                        statement.setLong(1, userId)
                        statement.setObject(2, currentTrackId)
                        statement.setLong(3, positionMillis)
                        statement.setTimestamp(4, Timestamp.from(now))
                        statement.executeUpdate()
                    }
                }
                connection.prepareStatement("DELETE FROM user_play_queue_track WHERE user_id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO user_play_queue_track (user_id, position, track_id) VALUES (?, ?, ?)",
                ).use { statement ->
                    trackIds.forEachIndexed { position, trackId ->
                        statement.setLong(1, userId)
                        statement.setInt(2, position)
                        statement.setLong(3, trackId)
                        statement.addBatch()
                    }
                    if (trackIds.isNotEmpty()) statement.executeBatch()
                }
                connection.commit()
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
        return StandalonePlayQueue(trackIds, currentTrackId, positionMillis, now)
    }

    fun clear(userId: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM user_play_queue WHERE user_id = ?").use { statement ->
                statement.setLong(1, userId)
                statement.executeUpdate()
            }
        }
    }

    private companion object {
        const val MAX_TRACKS = 5_000
    }
}
