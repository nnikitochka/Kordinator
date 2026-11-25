package su.nnedition.kord.api.support

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.coroutines.withContext
import su.nnedition.kord.service.KtorCoreMediaCatalogService
import su.nnedition.kord.service.library.scan.LibraryScan
import su.nnedition.kord.service.StandalonePlayQueue
import su.nnedition.kord.service.StandalonePlaylist
import su.nnedition.kord.api.module.admin.library.ScanResponse
import su.nnedition.kord.api.module.playlist.PlaylistResponse
import su.nnedition.kord.api.module.playlist.PlaylistTrackResponse
import su.nnedition.kord.api.module.user.playback.PlayQueueResponse
import su.nnedition.kord.api.module.user.playback.PlayQueueTrackResponse
import su.nnedition.kord.api.scanDispatcher

internal const val MAX_PASSWORD_LENGTH = 1_024

internal val jsonMapper = jacksonObjectMapper()

internal suspend fun ApplicationCall.respondJson(value: Any, status: HttpStatusCode = HttpStatusCode.OK) {
    respondText(jsonMapper.writeValueAsString(value), ContentType.Application.Json, status)
}

internal fun LibraryScan.toResponse() = ScanResponse(
    id, libraryId, state, tracksScanned, errorCount, startedAt.toString(), finishedAt?.toString(),
)

internal fun StandalonePlaylist.toResponse() = PlaylistResponse(
    id,
    name,
    createdAt.toString(),
    updatedAt.toString(),
    visibility,
    description,
    tracks.map { it.id },
    tracks.map { PlaylistTrackResponse(it.id, it.title, it.artist, it.album, it.durationSeconds) },
)

internal suspend fun StandalonePlayQueue.toResponse(
    mediaCatalogService: KtorCoreMediaCatalogService,
    accessibleLibraryIds: Set<Long>?,
): PlayQueueResponse {
    val tracksById = withContext(scanDispatcher) {
        mediaCatalogService.tracksByCoreIds(trackIds.distinct(), accessibleLibraryIds)
    }.associateBy { it.id }

    val visibleTracks = trackIds.mapNotNull { tracksById[it] }
    return PlayQueueResponse(
        trackIds = visibleTracks.map { it.id },
        currentTrackId = currentTrackId?.takeIf { it in tracksById },
        positionMillis = positionMillis,
        updatedAt = updatedAt?.toString(),
        tracks = visibleTracks.map { PlayQueueTrackResponse(it.id, it.title, it.artist, it.album, it.durationSeconds) },
    )
}
