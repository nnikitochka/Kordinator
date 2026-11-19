package org.airsonic.player.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SecurityService

data class KordinatorAlbumSummary(
    val id: Int,
    val name: String?,
    val artist: String?,
    val year: Int?,
    val genre: String?,
    val durationSeconds: Int,
)

class KordinatorAlbumRoutes(
    private val mediaFileService: MediaFileService,
    private val mediaFolderService: MediaFolderService,
    private val securityService: SecurityService,
) {
    fun routes(
        root: Route,
        currentUsername: (ApplicationCall) -> String?,
        objectMapper: ObjectMapper,
    ) {
        root.get("/api/v1/albums") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val offsetValue = call.request.queryParameters["offset"]
            val limitValue = call.request.queryParameters["limit"]
            val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
            val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 20 else null
            if (offset == null || limit == null) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Offset and limit must be integers")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@get
            }
            val byArtist = when (call.request.queryParameters["sort"] ?: "artist") {
                "name" -> false
                "artist" -> true
                else -> {
                    call.respondText(
                        objectMapper.writeValueAsString(ApiError("invalid_request", "Sort must be 'name' or 'artist'")),
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@get
                }
            }
            if (offset < 0 || limit !in 1..100) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Offset must be non-negative and limit must be between 1 and 100")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@get
            }

            val musicFolders = mediaFolderService.getMusicFoldersForUser(username)
            val albums = mediaFileService.getAlphabeticalAlbums(offset, limit, byArtist, musicFolders)
                .asSequence()
                .filter { it.mediaType == MediaFile.MediaType.ALBUM }
                .filter { securityService.isFolderAccessAllowed(it, username) }
                .map { it.toKordinatorAlbumSummary() }
                .toList()
            call.respondText(objectMapper.writeValueAsString(albums), ContentType.Application.Json)
        }
    }
}

private fun MediaFile.toKordinatorAlbumSummary() = KordinatorAlbumSummary(
    id = requireNotNull(id),
    name = albumName,
    artist = artist,
    year = year,
    genre = genre,
    durationSeconds = duration?.toInt() ?: 0,
)
