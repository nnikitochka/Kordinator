package org.airsonic.player.service

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/** Stores each user's favorite tracks for the standalone API. */
class StandaloneFavoriteService(
    private val dataSource: DataSource,
    private val mediaCatalog: KtorCoreMediaCatalogService,
) {
    fun list(userId: Long, limit: Int, offset: Int, accessibleLibraryIds: Set<Long>? = null): List<StandaloneTrack> {
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)
        val coreTrackIds = dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT track_id FROM favorite_track WHERE user_id = ? ORDER BY created_at DESC, track_id",
            ).use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } }
            }
        }
        return mediaCatalog.tracksByCoreIds(coreTrackIds, accessibleLibraryIds).drop(safeOffset).take(safeLimit)
    }

    fun count(userId: Long, accessibleLibraryIds: Set<Long>? = null): Long {
        val trackIds = dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT track_id FROM favorite_track WHERE user_id = ?").use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } }
            }
        }
        return mediaCatalog.tracksByCoreIds(trackIds, accessibleLibraryIds).size.toLong()
    }

    fun set(userId: Long, trackId: Long, favorite: Boolean, accessibleLibraryIds: Set<Long>? = null): Boolean {
        if (mediaCatalog.track(trackId, accessibleLibraryIds) == null) return false
        return dataSource.connection.use { connection ->
            if (favorite) {
                val alreadyFavorite = connection.prepareStatement(
                    "SELECT 1 FROM favorite_track WHERE user_id = ? AND track_id = ?",
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.setLong(2, trackId)
                    statement.executeQuery().use { it.next() }
                }
                if (!alreadyFavorite) {
                    try {
                        connection.prepareStatement(
                            "INSERT INTO favorite_track (user_id, track_id, created_at) VALUES (?, ?, ?)",
                        ).use { statement ->
                            statement.setLong(1, userId)
                            statement.setLong(2, trackId)
                            statement.setTimestamp(3, Timestamp.from(Instant.now()))
                            statement.executeUpdate()
                        }
                    } catch (failure: java.sql.SQLException) {
                        if (failure.sqlState?.startsWith("23") != true) throw failure
                    }
                }
            } else {
                connection.prepareStatement("DELETE FROM favorite_track WHERE user_id = ? AND track_id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.setLong(2, trackId)
                    statement.executeUpdate()
                }
            }
            true
        }
    }
}
