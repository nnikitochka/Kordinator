package su.nnedition.kord.api.module.artist

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.withContext
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.module.album.AlbumSummaryResponse
import su.nnedition.kord.api.scanDispatcher
import su.nnedition.kord.api.support.catalogLibraryScope
import su.nnedition.kord.api.support.decodeKordinatorCatalogId
import su.nnedition.kord.api.support.kordinatorCatalogId
import su.nnedition.kord.api.support.respondJson
import su.nnedition.kord.api.support.toResponse

fun Route.artistRoutes(
    context: ApiContext
) {
    get("/artists") {
        if (context.mediaCatalogService == null) {
            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val requestedLibraryId = call.request.queryParameters["libraryId"]
        val libraryId = requestedLibraryId?.toLongOrNull()
        val requestedPrefix = call.request.queryParameters["prefix"]
        val prefix = requestedPrefix?.trim()?.takeIf(String::isNotEmpty)
        val limitValue = call.request.queryParameters["limit"]
        val offsetValue = call.request.queryParameters["offset"]
        val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 50 else null
        val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
        if ((requestedLibraryId != null && (libraryId == null || libraryId !in 0..Int.MAX_VALUE.toLong())) ||
            (requestedPrefix != null && requestedPrefix.length > 100) ||
            limit == null || limit !in 1..100 || offset == null || offset < 0
        ) {
            call.respondJson(
                ApiError(
                    "invalid_request",
                    "libraryId must be between 0 and ${Int.MAX_VALUE}, prefix at most 100 characters, limit between 1 and 100, and offset non-negative"
                ), HttpStatusCode.BadRequest)
            return@get
        }
        val accessibleLibraries = call.catalogLibraryScope()
        if (libraryId != null && accessibleLibraries?.contains(libraryId) == false) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val artists = withContext(scanDispatcher) {
            context.mediaCatalogService.artists(libraryId, limit, offset, accessibleLibraries, prefix)
        }
        val total = withContext(scanDispatcher) {
            context.mediaCatalogService.countArtists(libraryId, accessibleLibraries, prefix)
        }
        call.respondJson(ArtistPage(artists.map { it.toResponse() }, artists.size, total, limit, offset))
    }
    get("/artists/{id}") {
        val selector = call.parameters["id"]?.decodeKordinatorCatalogId()
        if (selector == null) {
            call.respondJson(ApiError("invalid_request", "Artist ID is invalid"), HttpStatusCode.BadRequest)
            return@get
        }
        val service = context.mediaCatalogService
        if (service == null) {
            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val accessibleLibraries = call.catalogLibraryScope()
        if (accessibleLibraries?.contains(selector.first) == false) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val tracks = withContext(scanDispatcher) {
            service.tracksForArtist(selector.first, selector.second, accessibleLibraries)
        }
        if (tracks.isEmpty()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val albums = tracks.asSequence()
            .mapNotNull { track -> track.album?.takeIf(String::isNotBlank)?.let { it to track } }
            .groupBy({ it.first }, { it.second })
            .map { (name, albumTracks) ->
                AlbumSummaryResponse(
                    kordinatorCatalogId(selector.first, name), selector.first, name,
                    albumTracks.first().albumArtist ?: albumTracks.first().artist,
                    albumTracks.mapNotNull { it.year }.maxOrNull(),
                    albumTracks.firstNotNullOfOrNull { it.genre },
                    albumTracks.size.toLong(),
                    albumTracks.sumOf { it.durationSeconds.toLong() },
                )
            }
            .sortedBy { it.name.lowercase() }

        call.respondJson(
            ArtistDetailsResponse(
                kordinatorCatalogId(selector.first, selector.second),
                selector.second,
                tracks.map { it.toResponse() },
                albums.size,
                albums,
            )
        )
    }
}
