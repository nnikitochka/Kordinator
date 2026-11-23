package org.airsonic.player.service

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource
import org.airsonic.player.domain.MediaFile

data class StandaloneVideoBookmark(
    val videoId: Int,
    val positionMillis: Long,
    val comment: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Stores per-account resume positions for videos in the core media catalog. */
class StandaloneVideoBookmarkService(
    private val dataSource: DataSource,
    private val mediaFiles: MediaFileService,
) {
    fun list(userId: Long, accessibleLibraryIds: Set<Long>? = null): List<StandaloneVideoBookmark> {
        val rows = dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT video_id, position_ms, comment, created_at, updated_at FROM video_bookmark " +
                    "WHERE user_id = ? ORDER BY updated_at DESC, video_id",
            ).use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(
                                StandaloneVideoBookmark(
                                    result.getInt("video_id"), result.getLong("position_ms"), result.getString("comment"),
                                    result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant(),
                                ),
                            )
                        }
                    }
                }
            }
        }
        return rows.filter { accessibleVideo(it.videoId, accessibleLibraryIds) != null }
    }

    fun set(
        userId: Long,
        videoId: Int,
        positionMillis: Long,
        comment: String?,
        accessibleLibraryIds: Set<Long>? = null,
    ): Boolean {
        require(videoId >= 0) { "Video ID must be non-negative" }
        require(positionMillis >= 0) { "Bookmark position must be non-negative" }
        require(comment == null || comment.length <= 1024) { "Bookmark comment must be at most 1024 characters" }
        val video = accessibleVideo(videoId, accessibleLibraryIds) ?: return false
        val durationMillis = ((video.duration ?: 0.0) * 1000.0).toLong()
        if (durationMillis > 0 && durationMillis - positionMillis < END_BOOKMARK_WINDOW_MS) {
            delete(userId, videoId)
            return true
        }

        val now = Timestamp.from(Instant.now())
        return dataSource.connection.use { connection ->
            val updated = connection.prepareStatement(
                "UPDATE video_bookmark SET position_ms = ?, comment = ?, updated_at = ? " +
                    "WHERE user_id = ? AND video_id = ?",
            ).use { statement ->
                statement.setLong(1, positionMillis)
                statement.setString(2, comment)
                statement.setTimestamp(3, now)
                statement.setLong(4, userId)
                statement.setInt(5, videoId)
                statement.executeUpdate()
            }
            if (updated == 0) {
                connection.prepareStatement(
                    "INSERT INTO video_bookmark (user_id, video_id, position_ms, comment, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.setInt(2, videoId)
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

    fun delete(userId: Long, videoId: Int, accessibleLibraryIds: Set<Long>? = null): Boolean {
        if (accessibleVideo(videoId, accessibleLibraryIds) == null) return false
        return delete(userId, videoId)
    }

    private fun delete(userId: Long, videoId: Int): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM video_bookmark WHERE user_id = ? AND video_id = ?").use { statement ->
            statement.setLong(1, userId)
            statement.setInt(2, videoId)
            statement.executeUpdate() > 0
        }
    }

    private fun accessibleVideo(videoId: Int, accessibleLibraryIds: Set<Long>?): MediaFile? {
        if (videoId < 0) return null
        val video = mediaFiles.getMediaFile(videoId) ?: return null
        if (!video.isVideo || !video.isPresent) return null
        if (accessibleLibraryIds != null && video.folderId!!.toLong() !in accessibleLibraryIds) return null
        return video
    }

    private companion object {
        const val END_BOOKMARK_WINDOW_MS = 60_000L
    }
}
