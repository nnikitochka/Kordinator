package su.nnedition.kord.api.module.album

import io.ktor.server.routing.Route

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import kotlinx.coroutines.withContext
import org.airsonic.player.ktor.catalogLibraryScope
import org.airsonic.player.ktor.decodeKordinatorCatalogId
import org.airsonic.player.ktor.kordinatorCatalogId
import org.airsonic.player.ktor.respondJson
import org.airsonic.player.ktor.toResponse
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.scanDispatcher

fun Route.albumRoutes(
    context: ApiContext
) {
    get("/albums") {
        if (context.mediaCatalogService == null) {
            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val requestedLibraryId = call.request.queryParameters["libraryId"]
        val libraryId = requestedLibraryId?.toLongOrNull()
        val limitValue = call.request.queryParameters["limit"]
        val offsetValue = call.request.queryParameters["offset"]
        val sort = call.request.queryParameters["sort"] ?: "name"
        val genre = call.request.queryParameters["genre"]?.takeIf(String::isNotBlank)
        val rawFromYear = call.request.queryParameters["fromYear"]
        val fromYear = rawFromYear?.toIntOrNull()
        val rawToYear = call.request.queryParameters["toYear"]
        val toYear = rawToYear?.toIntOrNull()
        val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 50 else null
        val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
        if ((requestedLibraryId != null && (libraryId == null || libraryId !in 0..Int.MAX_VALUE.toLong())) ||
            limit == null || limit !in 1..100 || offset == null || offset < 0 ||
            sort !in setOf(
                "name", "artist", "year", "newest", "most-played", "recently-played", "random", "highest-rated",
            ) ||
            genre?.length?.let { it > 128 } == true ||
            rawFromYear != null && fromYear == null || rawToYear != null && toYear == null ||
            (rawFromYear == null) != (rawToYear == null) ||
            fromYear != null && toYear != null && fromYear > toYear
        ) {
            call.respondJson(
                ApiError("invalid_request", "Invalid libraryId, sort, genre, year range, limit, or offset"),
                HttpStatusCode.BadRequest,
            )
            return@get
        }
        val accessibleLibraries = call.catalogLibraryScope()
        if (libraryId != null && accessibleLibraries?.contains(libraryId) == false) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val albums = withContext(scanDispatcher) {
            context.mediaCatalogService.albums(libraryId, limit, offset, accessibleLibraries, sort, genre, fromYear, toYear)
        }
        val total = withContext(scanDispatcher) {
            context.mediaCatalogService.countAlbums(libraryId, accessibleLibraries, genre, fromYear, toYear)
        }
        call.respondJson(AlbumPage(albums.map { it.toResponse() }, albums.size, total, limit, offset))
    }
    get("/albums/{id}") {
        val selector = call.parameters["id"]?.decodeKordinatorCatalogId()
        if (selector == null) {
            call.respondJson(ApiError("invalid_request", "Album ID is invalid"), HttpStatusCode.BadRequest)
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
            service.tracksForAlbum(selector.first, selector.second, accessibleLibraries)
        }
        if (tracks.isEmpty()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.respondJson(
            AlbumDetailsResponse(
                kordinatorCatalogId(selector.first, selector.second), selector.second,
                tracks.first().albumArtist ?: tracks.first().artist,
                tracks.mapNotNull { it.year }.maxOrNull(),
                tracks.firstNotNullOfOrNull { it.genre },
                tracks.size,
                tracks.sumOf { it.durationSeconds.toLong() },
                tracks.map { it.toResponse() },
            )
        )
    }

}
