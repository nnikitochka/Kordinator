package org.airsonic.player.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.airsonic.player.service.PlaylistService

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
    }
}
