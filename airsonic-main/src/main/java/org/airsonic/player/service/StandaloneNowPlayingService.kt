package org.airsonic.player.service

import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.sql.DataSource

data class StandaloneNowPlayingEntry(
    val clientId: String,
    val track: StandaloneTrack,
    val positionMillis: Long,
    val isPlaying: Boolean,
    val updatedAt: Instant,
)

/** Stores short-lived, user-private playback reports from REST clients. */
class StandaloneNowPlayingService(
    private val dataSource: DataSource,
    private val mediaCatalog: KtorCoreMediaCatalogService,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun report(
        userId: Long,
        clientId: String,
        trackId: Long,
        positionMillis: Long,
        isPlaying: Boolean,
        accessibleLibraryIds: Set<Long>? = null,
    ): Boolean {
        require(clientId.matches(CLIENT_ID_PATTERN)) { "Client ID must contain 1 to 128 letters, digits, dots, underscores, colons or hyphens" }
        require(trackId >= 0) { "Track ID must be non-negative" }
        require(positionMillis >= 0) { "Playback position must be non-negative" }
        if (mediaCatalog.track(trackId, accessibleLibraryIds) == null) return false

        val now = Timestamp.from(clock.instant())
        dataSource.connection.use { connection ->
            val updated = connection.prepareStatement(
                "UPDATE user_now_playing SET track_id = ?, position_ms = ?, is_playing = ?, updated_at = ? " +
                    "WHERE user_id = ? AND client_id = ?",
            ).use { statement ->
                statement.setLong(1, trackId)
                statement.setLong(2, positionMillis)
                statement.setBoolean(3, isPlaying)
                statement.setTimestamp(4, now)
                statement.setLong(5, userId)
                statement.setString(6, clientId)
                statement.executeUpdate()
            }
            if (updated > 0) return true
            try {
                connection.prepareStatement(
                    "INSERT INTO user_now_playing (user_id, client_id, track_id, position_ms, is_playing, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.setString(2, clientId)
                    statement.setLong(3, trackId)
                    statement.setLong(4, positionMillis)
                    statement.setBoolean(5, isPlaying)
                    statement.setTimestamp(6, now)
                    statement.executeUpdate()
                }
            } catch (failure: java.sql.SQLException) {
                if (failure.sqlState?.startsWith("23") != true) throw failure
                connection.prepareStatement(
                    "UPDATE user_now_playing SET track_id = ?, position_ms = ?, is_playing = ?, updated_at = ? " +
                        "WHERE user_id = ? AND client_id = ?",
                ).use { statement ->
                    statement.setLong(1, trackId)
                    statement.setLong(2, positionMillis)
                    statement.setBoolean(3, isPlaying)
                    statement.setTimestamp(4, now)
                    statement.setLong(5, userId)
                    statement.setString(6, clientId)
                    check(statement.executeUpdate() == 1) { "Playback report disappeared during concurrent update" }
                }
            }
        }
        return true
    }

    fun list(userId: Long, accessibleLibraryIds: Set<Long>? = null): List<StandaloneNowPlayingEntry> {
        val cutoff = Timestamp.from(clock.instant().minus(STATE_TTL))
        val reports = dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM user_now_playing WHERE updated_at < ?").use { statement ->
                statement.setTimestamp(1, cutoff)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                "SELECT client_id, track_id, position_ms, is_playing, updated_at FROM user_now_playing " +
                    "WHERE user_id = ? AND updated_at >= ? ORDER BY updated_at DESC, client_id",
            ).use { statement ->
                statement.setLong(1, userId)
                statement.setTimestamp(2, cutoff)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(
                                StoredReport(
                                    rows.getString("client_id"),
                                    rows.getLong("track_id"),
                                    rows.getLong("position_ms"),
                                    rows.getBoolean("is_playing"),
                                    rows.getTimestamp("updated_at").toInstant(),
                                ),
                            )
                        }
                    }
                }
            }
        }
        val tracks = mediaCatalog.tracksByCoreIds(reports.map { it.trackId }.distinct(), accessibleLibraryIds).associateBy { it.id }
        return reports.mapNotNull { report ->
            tracks[report.trackId]?.let { track ->
                StandaloneNowPlayingEntry(report.clientId, track, report.positionMillis, report.isPlaying, report.updatedAt)
            }
        }
    }

    fun clear(userId: Long, clientId: String) {
        require(clientId.matches(CLIENT_ID_PATTERN)) { "Client ID must contain 1 to 128 letters, digits, dots, underscores, colons or hyphens" }
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM user_now_playing WHERE user_id = ? AND client_id = ?").use { statement ->
                statement.setLong(1, userId)
                statement.setString(2, clientId)
                statement.executeUpdate()
            }
        }
    }

    private data class StoredReport(
        val clientId: String,
        val trackId: Long,
        val positionMillis: Long,
        val isPlaying: Boolean,
        val updatedAt: Instant,
    )

    private companion object {
        val CLIENT_ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,128}")
        val STATE_TTL: Duration = Duration.ofMinutes(5)
    }
}
