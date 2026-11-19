package org.airsonic.player.service

import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

data class StandalonePlaylist(
    val id: Long,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val tracks: List<StandaloneTrack>,
)

data class StandalonePlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val trackCount: Int,
)

/** Stores private, ordered playlists for the standalone API. */
class StandalonePlaylistService(
    private val dataSource: DataSource,
) {
    fun list(ownerId: Long): List<StandalonePlaylistSummary> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT p.id, p.name, p.created_at, p.updated_at, COUNT(t.position) AS track_count " +
                "FROM user_playlist p LEFT JOIN user_playlist_track t ON t.playlist_id = p.id " +
                "WHERE p.owner_id = ? GROUP BY p.id, p.name, p.created_at, p.updated_at " +
                "ORDER BY p.name, p.id",
        ).use { statement ->
            statement.setLong(1, ownerId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            StandalonePlaylistSummary(
                                rows.getLong("id"), rows.getString("name"), rows.getTimestamp("created_at").toInstant(),
                                rows.getTimestamp("updated_at").toInstant(), rows.getInt("track_count"),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun get(ownerId: Long, playlistId: Long): StandalonePlaylist? = dataSource.connection.use { connection ->
        get(connection, ownerId, playlistId)
    }

    private fun get(connection: Connection, ownerId: Long, playlistId: Long): StandalonePlaylist? {
        val header = connection.prepareStatement(
            "SELECT name, created_at, updated_at FROM user_playlist WHERE id = ? AND owner_id = ?",
        ).use { statement ->
            statement.setLong(1, playlistId)
            statement.setLong(2, ownerId)
            statement.executeQuery().use { rows ->
                if (rows.next()) Triple(rows.getString(1), rows.getTimestamp(2).toInstant(), rows.getTimestamp(3).toInstant()) else null
            }
        } ?: return null

        val tracks = connection.prepareStatement(
            "SELECT m.id, m.library_id, m.relative_path, m.title, m.artist, m.album, m.album_artist, " +
                "m.duration_seconds, m.size_bytes, m.modified_at FROM user_playlist_track p " +
                "JOIN media_track m ON m.id = p.track_id WHERE p.playlist_id = ? ORDER BY p.position",
        ).use { statement ->
            statement.setLong(1, playlistId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            StandaloneTrack(
                                rows.getLong("id"), rows.getLong("library_id"), rows.getString("relative_path"),
                                rows.getString("title"), rows.getString("artist"), rows.getString("album"),
                                rows.getString("album_artist"), rows.getInt("duration_seconds"), rows.getLong("size_bytes"),
                                rows.getTimestamp("modified_at")?.toInstant(),
                            ),
                        )
                    }
                }
            }
        }
        return StandalonePlaylist(playlistId, header.first, header.second, header.third, tracks)
    }

    fun create(ownerId: Long, name: String, trackIds: List<Long>): StandalonePlaylist {
        val normalizedName = validate(name, trackIds)
        return transaction { connection ->
            requireTracksExist(connection, trackIds)
            val now = Instant.now()
            val playlistId = connection.prepareStatement(
                "INSERT INTO user_playlist (owner_id, name, created_at, updated_at) VALUES (?, ?, ?, ?)",
                arrayOf("id"),
            ).use { statement ->
                statement.setLong(1, ownerId)
                statement.setString(2, normalizedName)
                statement.setTimestamp(3, Timestamp.from(now))
                statement.setTimestamp(4, Timestamp.from(now))
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    check(keys.next()) { "Database did not return a playlist ID" }
                    keys.getLong(1)
                }
            }
            replaceTracks(connection, playlistId, trackIds)
            get(connection, ownerId, playlistId) ?: error("Created playlist is not visible to its owner")
        }
    }

    fun update(ownerId: Long, playlistId: Long, name: String, trackIds: List<Long>): StandalonePlaylist? {
        val normalizedName = validate(name, trackIds)
        return transaction { connection ->
            val exists = connection.prepareStatement("SELECT 1 FROM user_playlist WHERE id = ? AND owner_id = ?").use { statement ->
                statement.setLong(1, playlistId)
                statement.setLong(2, ownerId)
                statement.executeQuery().use { it.next() }
            }
            if (!exists) return@transaction null
            requireTracksExist(connection, trackIds)
            connection.prepareStatement("UPDATE user_playlist SET name = ?, updated_at = ? WHERE id = ? AND owner_id = ?").use { statement ->
                statement.setString(1, normalizedName)
                statement.setTimestamp(2, Timestamp.from(Instant.now()))
                statement.setLong(3, playlistId)
                statement.setLong(4, ownerId)
                check(statement.executeUpdate() == 1)
            }
            replaceTracks(connection, playlistId, trackIds)
            get(connection, ownerId, playlistId)
        }
    }

    fun delete(ownerId: Long, playlistId: Long): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM user_playlist WHERE id = ? AND owner_id = ?").use { statement ->
            statement.setLong(1, playlistId)
            statement.setLong(2, ownerId)
            statement.executeUpdate() == 1
        }
    }

    private fun validate(name: String, trackIds: List<Long>): String {
        val normalized = name.trim()
        require(normalized.length in 1..200) { "Playlist name must contain between 1 and 200 characters" }
        require(trackIds.size <= MAX_TRACKS) { "Playlist contains too many tracks" }
        require(trackIds.all { it >= 0 }) { "Track IDs must be non-negative" }
        return normalized
    }

    private fun requireTracksExist(connection: Connection, trackIds: List<Long>) {
        trackIds.forEach { trackId ->
            connection.prepareStatement("SELECT 1 FROM media_track WHERE id = ?").use { statement ->
                statement.setLong(1, trackId)
                statement.executeQuery().use { require(it.next()) { "Track does not exist" } }
            }
        }
    }

    private fun replaceTracks(connection: Connection, playlistId: Long, trackIds: List<Long>) {
        connection.prepareStatement("DELETE FROM user_playlist_track WHERE playlist_id = ?").use { statement ->
            statement.setLong(1, playlistId)
            statement.executeUpdate()
        }
        connection.prepareStatement("INSERT INTO user_playlist_track (playlist_id, position, track_id) VALUES (?, ?, ?)").use { statement ->
            trackIds.forEachIndexed { position, trackId ->
                statement.setLong(1, playlistId)
                statement.setInt(2, position)
                statement.setLong(3, trackId)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun <T> transaction(action: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            action(connection).also { connection.commit() }
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        }
    }

    private companion object {
        const val MAX_TRACKS = 5_000
    }
}
