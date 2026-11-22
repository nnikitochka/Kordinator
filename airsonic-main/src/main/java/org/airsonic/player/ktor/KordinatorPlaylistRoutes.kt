package org.airsonic.player.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.domain.Playlist
import java.time.Instant

data class KordinatorPlaylistSummary(
    val id: Int,
    val name: String,
    val trackCount: Int,
    val durationSeconds: Int,
    val createdAt: String?,
    val updatedAt: String?,
)

class KordinatorPlaylistRoutes(
    private val playlists: PlaylistService,
    private val securityService: SecurityService,
    private val mediaFileService: MediaFileService,
) {
    fun routes(
        root: Route,
        currentUsername: (ApplicationCall) -> String?,
        objectMapper: ObjectMapper,
    ) {
        root.get("/api/v1/playlists") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val visiblePlaylists = playlists.getReadablePlaylistsForUser(username).map {
                KordinatorPlaylistSummary(
                    id = it.id,
                    name = it.name!!,
                    trackCount = it.fileCount,
                    durationSeconds = it.duration.toInt(),
                    createdAt = it.created?.toString(),
                    updatedAt = it.changed?.toString(),
                )
            }
            call.respondText(objectMapper.writeValueAsString(visiblePlaylists), ContentType.Application.Json)
        }

        root.get("/api/v1/playlists/{id}") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null || id <= 0) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Playlist ID must be a positive number")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@get
            }

            val playlist = playlists.getPlaylist(id)
            if (playlist == null || !playlists.isReadAllowed(playlist, username)) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val tracks = playlists.getFilesInPlaylist(id).mapNotNull { file ->
                if (!securityService.isFolderAccessAllowed(file, username)) return@mapNotNull null
                file.toTrack()
            }
            val details = KordinatorPlaylistDetails(
                id = playlist.id,
                name = playlist.name!!,
                comment = playlist.comment,
                trackCount = tracks.size,
                durationSeconds = tracks.sumOf { it.durationSeconds },
                createdAt = playlist.created?.toString(),
                updatedAt = playlist.changed?.toString(),
                tracks = tracks,
            )
            call.respondText(objectMapper.writeValueAsString(details), ContentType.Application.Json)
        }

        root.put("/api/v1/playlists/{id}") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@put
            }

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null || id <= 0) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Playlist ID must be a positive number")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@put
            }

            val original = playlists.getPlaylist(id)
            if (original == null || !playlists.isWriteAllowed(original, username)) {
                call.respond(HttpStatusCode.NotFound)
                return@put
            }

            val request = try {
                objectMapper.readValue(call.receiveText(), UpdateKordinatorPlaylistRequest::class.java)
            } catch (_: Exception) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Request body must be valid JSON")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@put
            }
            val name = request.name?.trim()
            if (name.isNullOrEmpty()) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Playlist name is required")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@put
            }

            val updated = Playlist(original).apply {
                this.name = name
                comment = request.comment?.trim()?.takeIf(String::isNotEmpty)
                changed = Instant.now()
            }
            playlists.updatePlaylist(updated)
            call.respondText(objectMapper.writeValueAsString(updated.toSummary()), ContentType.Application.Json)
        }

        root.put("/api/v1/playlists/{id}/tracks") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@put
            }

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null || id <= 0) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Playlist ID must be a positive number")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@put
            }

            val playlist = playlists.getPlaylist(id)
            if (playlist == null || !playlists.isWriteAllowed(playlist, username)) {
                call.respond(HttpStatusCode.NotFound)
                return@put
            }

            val request = try {
                objectMapper.readValue(call.receiveText(), ReplaceKordinatorPlaylistTracksRequest::class.java)
            } catch (_: Exception) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Request body must be valid JSON")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@put
            }
            val trackIds = request.trackIds
            if (trackIds == null || trackIds.any { it <= 0 }) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Track IDs must be positive numbers")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@put
            }

            val tracks = trackIds.map { trackId ->
                val track = mediaFileService.getMediaFile(trackId)
                if (track == null || !track.isAudio || !securityService.isFolderAccessAllowed(track, username)) {
                    call.respond(HttpStatusCode.NotFound)
                    return@put
                }
                track
            }
            playlists.setFilesInPlaylist(id, tracks)
            call.respond(HttpStatusCode.NoContent)
        }

        root.delete("/api/v1/playlists/{id}") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@delete
            }

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null || id <= 0) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Playlist ID must be a positive number")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@delete
            }

            val playlist = playlists.getPlaylist(id)
            if (playlist == null || !playlists.isWriteAllowed(playlist, username)) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            playlists.deletePlaylist(id)
            call.respond(HttpStatusCode.NoContent)
        }

        root.post("/api/v1/playlists") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val request = try {
                objectMapper.readValue(call.receiveText(), CreateKordinatorPlaylistRequest::class.java)
            } catch (_: Exception) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Request body must be valid JSON")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@post
            }
            val name = request.name?.trim()
            if (name.isNullOrEmpty()) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Playlist name is required")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@post
            }

            val now = Instant.now()
            val playlist = Playlist().apply {
                this.name = name
                this.username = username
                shared = false
                created = now
                changed = now
            }
            playlists.createPlaylist(playlist)
            call.respondText(
                objectMapper.writeValueAsString(playlist.toSummary()),
                ContentType.Application.Json,
                HttpStatusCode.Created,
            )
        }
    }
}

data class CreateKordinatorPlaylistRequest(val name: String?)

data class UpdateKordinatorPlaylistRequest(val name: String?, val comment: String?)

data class ReplaceKordinatorPlaylistTracksRequest(val trackIds: List<Int>?)

data class KordinatorPlaylistDetails(
    val id: Int,
    val name: String,
    val comment: String?,
    val trackCount: Int,
    val durationSeconds: Int,
    val createdAt: String?,
    val updatedAt: String?,
    val tracks: List<KordinatorTrackResponse>,
)

private fun Playlist.toSummary() = KordinatorPlaylistSummary(
    id = id,
    name = name!!,
    trackCount = fileCount,
    durationSeconds = duration.toInt(),
    createdAt = created?.toString(),
    updatedAt = changed?.toString(),
)

private fun MediaFile.toTrack() = KordinatorTrackResponse(
    id = requireNotNull(id),
    title = title,
    artist = artist,
    album = albumName,
    albumArtist = albumArtist,
    discNumber = discNumber,
    trackNumber = trackNumber,
    year = year,
    durationSeconds = duration?.toInt() ?: 0,
    fileSize = fileSize,
    format = format,
)
