package org.airsonic.player.service

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/** Stores a user's 1–5 rating for a catalog track; rating zero removes it. */
class StandaloneRatingService(
    private val dataSource: DataSource,
    private val mediaCatalog: KtorCoreMediaCatalogService,
) {
    fun set(userId: Long, trackId: Long, rating: Int, accessibleLibraryIds: Set<Long>? = null): Boolean {
        require(rating in 0..5) { "Rating must be between 0 and 5" }
        if (mediaCatalog.track(trackId, accessibleLibraryIds) == null) return false
        return dataSource.connection.use { connection ->
            if (rating == 0) {
                connection.prepareStatement("DELETE FROM track_rating WHERE user_id = ? AND track_id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.setLong(2, trackId)
                    statement.executeUpdate()
                }
            } else {
                val updated = connection.prepareStatement(
                    "UPDATE track_rating SET rating = ?, updated_at = ? WHERE user_id = ? AND track_id = ?",
                ).use { statement ->
                    statement.setInt(1, rating)
                    statement.setTimestamp(2, Timestamp.from(Instant.now()))
                    statement.setLong(3, userId)
                    statement.setLong(4, trackId)
                    statement.executeUpdate()
                }
                if (updated == 0) {
                    connection.prepareStatement(
                        "INSERT INTO track_rating (user_id, track_id, rating, updated_at) VALUES (?, ?, ?, ?)",
                    ).use { statement ->
                        statement.setLong(1, userId)
                        statement.setLong(2, trackId)
                        statement.setInt(3, rating)
                        statement.setTimestamp(4, Timestamp.from(Instant.now()))
                        statement.executeUpdate()
                    }
                }
            }
            true
        }
    }
}
