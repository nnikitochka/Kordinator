package org.airsonic.player.service

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/** Stores each user's favorite tracks for the standalone API. */
class StandaloneFavoriteService(private val dataSource: DataSource) {
    fun list(userId: Long, limit: Int, offset: Int): List<StandaloneTrack> {
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)
        val sql = "SELECT m.id, m.library_id, m.relative_path, m.title, m.artist, m.album, m.album_artist, " +
            "m.disc_number, m.track_number, m.year, m.genre, m.duration_seconds, m.size_bytes, m.modified_at " +
            "FROM favorite_track f JOIN media_track m ON m.id = f.track_id WHERE f.user_id = ? " +
            "ORDER BY f.created_at DESC, m.id LIMIT ? OFFSET ?"
        return dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, userId)
                statement.setInt(2, safeLimit)
                statement.setInt(3, safeOffset)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(
                            StandaloneTrack(
                                rows.getLong("id"), rows.getLong("library_id"), rows.getString("relative_path"),
                                rows.getString("title"), rows.getString("artist"), rows.getString("album"),
                                rows.getString("album_artist"), rows.getObject("disc_number")?.let { rows.getInt("disc_number") },
                                rows.getObject("track_number")?.let { rows.getInt("track_number") },
                                rows.getObject("year")?.let { rows.getInt("year") }, rows.getString("genre"),
                                rows.getInt("duration_seconds"), rows.getLong("size_bytes"),
                                rows.getTimestamp("modified_at")?.toInstant(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun count(userId: Long): Long = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT COUNT(*) FROM favorite_track WHERE user_id = ?").use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
        }
    }

    fun set(userId: Long, trackId: Long, favorite: Boolean): Boolean = dataSource.connection.use { connection ->
        val exists = connection.prepareStatement("SELECT 1 FROM media_track WHERE id = ?").use { statement ->
            statement.setLong(1, trackId)
            statement.executeQuery().use { it.next() }
        }
        if (!exists) return@use false

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
