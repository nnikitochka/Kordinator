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
import io.ktor.server.routing.post
import org.airsonic.player.service.PlaylistService
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

class KordinatorPlaylistRoutes(private val playlists: PlaylistService) {
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
                    name = it.name,
                    trackCount = it.fileCount,
                    durationSeconds = it.duration.toInt(),
                    createdAt = it.created?.toString(),
                    updatedAt = it.changed?.toString(),
                )
            }
            call.respondText(objectMapper.writeValueAsString(visiblePlaylists), ContentType.Application.Json)
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

private fun Playlist.toSummary() = KordinatorPlaylistSummary(
    id = id,
    name = name,
    trackCount = fileCount,
    durationSeconds = duration.toInt(),
    createdAt = created?.toString(),
    updatedAt = changed?.toString(),
)
