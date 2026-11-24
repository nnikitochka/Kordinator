package org.airsonic.player.ktor


data class AdminUserResponse(val id: Long, val username: String, val role: String, val enabled: Boolean, val email: String? = null)
data class AdminUserDetailsResponse(val user: AdminUserResponse, val libraryIds: List<Long>)
data class AdminHealthResponse(val status: String, val components: List<HealthComponentResponse>)
data class HealthComponentResponse(
    val name: String,
    val status: String,
    val details: Map<String, Any?> = emptyMap(),
    val components: List<HealthComponentResponse> = emptyList(),
)
data class PathWatcherResponse(val name: String, val path: String)
data class ScheduledTaskResponse(
    val name: String,
    val createdAt: String,
    val scheduledBy: String,
    val type: String?,
    val firstRun: String?,
    val lastRun: String?,
    val nextRun: String?,
)
data class CacheStatisticsResponse(val name: String, val hits: Double?, val misses: Double?, val puts: Double?, val removals: Double?)
data class TransferSampleResponse(val timestamp: Long, val bytesTransferred: Long)
data class TransferStatisticsResponse(
    val type: String,
    val username: String?,
    val playerType: String?,
    val playerName: String?,
    val path: String?,
    val bytesTransferred: Long,
    val history: List<TransferSampleResponse>,
)
data class UserTransferStatisticsResponse(val username: String, val streamed: Long, val downloaded: Long, val uploaded: Long)
data class NowPlayingResponse(
    val transferId: String?,
    val playerId: Int?,
    val trackId: Int?,
    val username: String?,
    val artist: String?,
    val title: String?,
    val minutesAgo: Long,
)

data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
data class SavePlayQueueRequest(
    val trackIds: List<Long> = emptyList(),
    val currentTrackId: Long? = null,
    val positionMillis: Long = 0,
)
data class PlayQueueResponse(
    val trackIds: List<Long>,
    val currentTrackId: Long?,
    val positionMillis: Long,
    val updatedAt: String?,
    val tracks: List<PlayQueueTrackResponse>,
)
data class PlayQueueTrackResponse(val id: Long, val title: String, val artist: String?, val album: String?, val durationSeconds: Int)
data class SetTrackNoteRequest(val note: String)
data class TrackNoteResponse(val trackId: Long, val note: String?, val createdAt: String?, val updatedAt: String?)
data class GenreListResponse(val items: List<GenreResponse>, val count: Int)
data class GenreResponse(val name: String, val trackCount: Int, val albumCount: Int)
data class VideoPage(val items: List<VideoResponse>, val limit: Int, val offset: Int, val hasMore: Boolean)
data class VideoResponse(
    val id: Int,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationSeconds: Int?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val format: String?,
)
