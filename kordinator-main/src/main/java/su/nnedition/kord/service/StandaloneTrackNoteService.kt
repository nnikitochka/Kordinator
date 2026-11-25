package su.nnedition.kord.service

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

data class StandaloneTrackNote(
    val trackId: Long,
    val note: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Stores private per-account notes for tracks without changing media files or shared catalog metadata. */
class StandaloneTrackNoteService(
    private val dataSource: DataSource,
    private val mediaCatalog: KtorCoreMediaCatalogService,
) {
    fun get(userId: Long, trackId: Long): StandaloneTrackNote? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT note, created_at, updated_at FROM user_track_note WHERE user_id = ? AND track_id = ?",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.setLong(2, trackId)
            statement.executeQuery().use { rows ->
                if (rows.next()) {
                    StandaloneTrackNote(
                        trackId,
                        rows.getString("note"),
                        rows.getTimestamp("created_at").toInstant(),
                        rows.getTimestamp("updated_at").toInstant(),
                    )
                } else {
                    null
                }
            }
        }
    }

    fun set(
        userId: Long,
        trackId: Long,
        note: String,
        accessibleLibraryIds: Set<Long>? = null,
    ): Boolean {
        val normalized = note.trim()
        require(normalized.isNotEmpty()) { "Note must not be blank" }
        require(normalized.length <= MAX_NOTE_LENGTH) { "Note must be at most $MAX_NOTE_LENGTH characters" }
        if (mediaCatalog.track(trackId, accessibleLibraryIds) == null) return false

        val now = Timestamp.from(Instant.now())
        return dataSource.connection.use { connection ->
            val updated = connection.prepareStatement(
                "UPDATE user_track_note SET note = ?, updated_at = ? WHERE user_id = ? AND track_id = ?",
            ).use { statement ->
                statement.setString(1, normalized)
                statement.setTimestamp(2, now)
                statement.setLong(3, userId)
                statement.setLong(4, trackId)
                statement.executeUpdate()
            }
            if (updated == 0) {
                connection.prepareStatement(
                    "INSERT INTO user_track_note (user_id, track_id, note, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.setLong(2, trackId)
                    statement.setString(3, normalized)
                    statement.setTimestamp(4, now)
                    statement.setTimestamp(5, now)
                    statement.executeUpdate()
                }
            }
            true
        }
    }

    fun delete(userId: Long, trackId: Long, accessibleLibraryIds: Set<Long>? = null): Boolean {
        if (mediaCatalog.track(trackId, accessibleLibraryIds) == null) return false
        return dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM user_track_note WHERE user_id = ? AND track_id = ?").use { statement ->
                statement.setLong(1, userId)
                statement.setLong(2, trackId)
                statement.executeUpdate() > 0
            }
        }
    }

    private companion object {
        const val MAX_NOTE_LENGTH = 4096
    }
}
