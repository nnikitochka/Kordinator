package org.airsonic.player.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.airsonic.player.domain.SearchCriteria
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SearchService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.search.IndexType

data class KordinatorSearchResponse(
    val query: String,
    val offset: Int,
    val totalHits: Int,
    val tracks: List<KordinatorTrackResponse>,
)

class KordinatorSearchRoutes(
    private val searchService: SearchService,
    private val mediaFolderService: MediaFolderService,
    private val securityService: SecurityService,
) {
    fun routes(
        root: Route,
        currentUsername: (ApplicationCall) -> String?,
        objectMapper: ObjectMapper,
    ) {
        root.get("/api/v1/search") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val query = call.request.queryParameters["query"]?.trim()
            if (query.isNullOrEmpty()) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Search query is required")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@get
            }

            val offsetValue = call.request.queryParameters["offset"]
            val limitValue = call.request.queryParameters["limit"]
            val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
            val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 25 else null
            if (offset == null || limit == null || offset < 0 || limit !in 1..100) {
                call.respondText(
                    objectMapper.writeValueAsString(ApiError("invalid_request", "Offset must be non-negative and limit must be between 1 and 100")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return@get
            }

            val criteria = SearchCriteria().apply {
                this.query = query
                this.offset = offset
                count = limit
            }
            val folders = mediaFolderService.getMusicFoldersForUser(username)
            val result = searchService.search(criteria, folders, IndexType.SONG)
            val tracks = result.mediaFiles.asSequence()
                .filter { it.isAudio }
                .filter { securityService.isFolderAccessAllowed(it, username) }
                .map { it.toKordinatorTrackResponse() }
                .toList()
            call.respondText(
                objectMapper.writeValueAsString(KordinatorSearchResponse(query, result.offset, result.totalHits, tracks)),
                ContentType.Application.Json,
            )
        }
    }
}

private fun org.airsonic.player.domain.MediaFile.toKordinatorTrackResponse() = KordinatorTrackResponse(
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
