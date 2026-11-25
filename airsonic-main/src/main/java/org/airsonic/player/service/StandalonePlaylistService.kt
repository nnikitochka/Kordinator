package org.airsonic.player.service

import org.airsonic.player.service.library.scan.StandaloneTrack
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
    val visibility: String = "PRIVATE",
    val description: String? = null,
)

data class StandalonePlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val trackCount: Int,
    val visibility: String = "PRIVATE",
    val description: String? = null,
)

/** Stores ordered playlists and account-scoped read access for the standalone API. */
class StandalonePlaylistService(
    private val dataSource: DataSource,
    private val mediaCatalog: KtorCoreMediaCatalogService,
) {
    fun list(ownerId: Long, accessibleLibraryIds: Set<Long>? = null): List<StandalonePlaylistSummary> = dataSource.connection.use { connection ->
        connection.prepareStatement(
                "SELECT p.id, p.name, p.created_at, p.updated_at, p.visibility, p.description " +
                "FROM user_playlist p WHERE p.owner_id = ? OR p.visibility = 'PUBLIC' OR " +
                "EXISTS (SELECT 1 FROM user_playlist_access a WHERE a.playlist_id = p.id AND a.user_id = ?) " +
                "ORDER BY p.name, p.id",
        ).use { statement ->
            statement.setLong(1, ownerId)
            statement.setLong(2, ownerId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val playlistId = rows.getLong("id")
                        val playlist = get(connection, ownerId, playlistId, accessibleLibraryIds)
                        add(
                            StandalonePlaylistSummary(
                                playlistId, rows.getString("name"), rows.getTimestamp("created_at").toInstant(),
                                rows.getTimestamp("updated_at").toInstant(), playlist?.tracks?.size ?: 0,
                                rows.getString("visibility"), rows.getString("description"),
                            )
                        )
                    }
                }
            }
        }
    }

    fun get(ownerId: Long, playlistId: Long, accessibleLibraryIds: Set<Long>? = null): StandalonePlaylist? = dataSource.connection.use { connection ->
        get(connection, ownerId, playlistId, accessibleLibraryIds)
    }

    private fun get(connection: Connection, ownerId: Long, playlistId: Long, accessibleLibraryIds: Set<Long>?): StandalonePlaylist? {
        val header = connection.prepareStatement(
            "SELECT p.name, p.created_at, p.updated_at, p.visibility, p.description FROM user_playlist p " +
                "WHERE p.id = ? AND (p.owner_id = ? OR p.visibility = 'PUBLIC' OR " +
                "EXISTS (SELECT 1 FROM user_playlist_access a WHERE a.playlist_id = p.id AND a.user_id = ?))",
        ).use { statement ->
            statement.setLong(1, playlistId)
            statement.setLong(2, ownerId)
            statement.setLong(3, ownerId)
            statement.executeQuery().use { rows ->
                if (rows.next()) PlaylistHeader(
                    rows.getString(1), rows.getTimestamp(2).toInstant(), rows.getTimestamp(3).toInstant(),
                    rows.getString(4), rows.getString(5),
                ) else null
            }
        } ?: return null

        val coreTrackIds = connection.prepareStatement(
            "SELECT track_id FROM user_playlist_track WHERE playlist_id = ? ORDER BY position",
        ).use { statement ->
            statement.setLong(1, playlistId)
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } }
        }
        val tracks = mediaCatalog.tracksByCoreIds(coreTrackIds, accessibleLibraryIds)
        return StandalonePlaylist(playlistId, header.name, header.createdAt, header.updatedAt, tracks, header.visibility, header.description)
    }

    fun create(
        ownerId: Long,
        name: String,
        trackIds: List<Long>,
        accessibleLibraryIds: Set<Long>? = null,
        visibility: String = "PRIVATE",
        description: String? = null,
    ): StandalonePlaylist {
        val normalizedName = validate(name, trackIds)
        val normalizedVisibility = validateVisibility(visibility)
        val coreTrackIds = trackIds
        return transaction { connection ->
            requireTracksExist(coreTrackIds, accessibleLibraryIds)
            val now = Instant.now()
            val playlistId = connection.prepareStatement(
                "INSERT INTO user_playlist (owner_id, name, created_at, updated_at, visibility, description) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf("id"),
            ).use { statement ->
                statement.setLong(1, ownerId)
                statement.setString(2, normalizedName)
                statement.setTimestamp(3, Timestamp.from(now))
                statement.setTimestamp(4, Timestamp.from(now))
                statement.setString(5, normalizedVisibility)
                statement.setString(6, validateDescription(description))
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    check(keys.next()) { "Database did not return a playlist ID" }
                    keys.getLong(1)
                }
            }
            replaceTracks(connection, playlistId, coreTrackIds)
            get(connection, ownerId, playlistId, accessibleLibraryIds) ?: error("Created playlist is not visible to its owner")
        }
    }

    fun update(
        ownerId: Long,
        playlistId: Long,
        name: String,
        trackIds: List<Long>,
        accessibleLibraryIds: Set<Long>? = null,
        visibility: String = "PRIVATE",
        description: String? = null,
    ): StandalonePlaylist? {
        val normalizedName = validate(name, trackIds)
        val normalizedVisibility = validateVisibility(visibility)
        val coreTrackIds = trackIds
        return transaction { connection ->
            val exists = connection.prepareStatement("SELECT 1 FROM user_playlist WHERE id = ? AND owner_id = ?").use { statement ->
                statement.setLong(1, playlistId)
                statement.setLong(2, ownerId)
                statement.executeQuery().use { it.next() }
            }
            if (!exists) return@transaction null
            requireTracksExist(coreTrackIds, accessibleLibraryIds)
            connection.prepareStatement(
                "UPDATE user_playlist SET name = ?, updated_at = ?, visibility = ?, description = ? WHERE id = ? AND owner_id = ?",
            ).use { statement ->
                statement.setString(1, normalizedName)
                statement.setTimestamp(2, Timestamp.from(Instant.now()))
                statement.setString(3, normalizedVisibility)
                statement.setString(4, validateDescription(description))
                statement.setLong(5, playlistId)
                statement.setLong(6, ownerId)
                check(statement.executeUpdate() == 1)
            }
            replaceTracks(connection, playlistId, coreTrackIds)
            get(connection, ownerId, playlistId, accessibleLibraryIds)
        }
    }

    fun delete(ownerId: Long, playlistId: Long): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM user_playlist WHERE id = ? AND owner_id = ?").use { statement ->
            statement.setLong(1, playlistId)
            statement.setLong(2, ownerId)
            statement.executeUpdate() == 1
        }
    }

    fun readers(ownerId: Long, playlistId: Long): List<Long>? = dataSource.connection.use { connection ->
        if (!owns(connection, ownerId, playlistId)) return@use null
        connection.prepareStatement("SELECT user_id FROM user_playlist_access WHERE playlist_id = ? ORDER BY user_id").use { statement ->
            statement.setLong(1, playlistId)
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } }
        }
    }

    fun setReaders(ownerId: Long, playlistId: Long, userIds: List<Long>): List<Long>? {
        require(userIds.size <= 1000) { "Too many playlist readers" }
        require(userIds.all { it > 0 } && userIds.distinct().size == userIds.size) { "Reader IDs must be unique positive user IDs" }
        return transaction { connection ->
            if (!owns(connection, ownerId, playlistId)) return@transaction null
            val readers = userIds.filterNot { it == ownerId }
            val existingCount = connection.prepareStatement(
                "SELECT COUNT(*) FROM local_user WHERE id IN (${readers.ifEmpty { listOf(-1L) }.joinToString(",") { "?" }})",
            ).use { statement ->
                readers.ifEmpty { listOf(-1L) }.forEachIndexed { index, id -> statement.setLong(index + 1, id) }
                statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
            }
            require(existingCount == readers.size) { "Reader account does not exist" }
            connection.prepareStatement("DELETE FROM user_playlist_access WHERE playlist_id = ?").use { statement ->
                statement.setLong(1, playlistId)
                statement.executeUpdate()
            }
            connection.prepareStatement("INSERT INTO user_playlist_access (playlist_id, user_id) VALUES (?, ?)").use { statement ->
                readers.forEach { userId ->
                    statement.setLong(1, playlistId)
                    statement.setLong(2, userId)
                    statement.addBatch()
                }
                if (readers.isNotEmpty()) statement.executeBatch()
            }
            readers
        }
    }

    private fun owns(connection: Connection, ownerId: Long, playlistId: Long): Boolean =
        connection.prepareStatement("SELECT 1 FROM user_playlist WHERE id = ? AND owner_id = ?").use { statement ->
            statement.setLong(1, playlistId)
            statement.setLong(2, ownerId)
            statement.executeQuery().use { it.next() }
        }

    private fun validate(name: String, trackIds: List<Long>): String {
        val normalized = name.trim()
        require(normalized.length in 1..200) { "Playlist name must contain between 1 and 200 characters" }
        require(trackIds.size <= MAX_TRACKS) { "Playlist contains too many tracks" }
        require(trackIds.all { it >= 0 }) { "Track IDs must be non-negative" }
        return normalized
    }

    private fun validateVisibility(visibility: String): String = visibility.uppercase().also {
        require(it == "PRIVATE" || it == "PUBLIC") { "Playlist visibility must be PRIVATE or PUBLIC" }
    }

    private fun validateDescription(description: String?): String? {
        require(description == null || description.length <= 4096) { "Playlist description is too long" }
        return description?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun requireTracksExist(coreTrackIds: List<Long>, accessibleLibraryIds: Set<Long>?) {
        require(mediaCatalog.tracksByCoreIds(coreTrackIds, accessibleLibraryIds).size == coreTrackIds.size) {
            "Track does not exist or is not accessible"
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

    private data class PlaylistHeader(
        val name: String,
        val createdAt: Instant,
        val updatedAt: Instant,
        val visibility: String,
        val description: String?,
    )

    private companion object {
        const val MAX_TRACKS = 5_000
    }
}
