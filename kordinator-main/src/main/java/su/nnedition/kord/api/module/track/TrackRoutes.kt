package su.nnedition.kord.api.module.track

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.withContext
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.scanDispatcher
import su.nnedition.kord.api.support.catalogLibraryScope
import su.nnedition.kord.api.support.respondJson
import su.nnedition.kord.api.support.toCatalogTrackResponse

fun Route.trackRoutes(
    context: ApiContext
) {
    get("/tracks") {
        if (context.mediaCatalogService == null) {
            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val requestedLibraryId = call.request.queryParameters["libraryId"]
        val libraryId = requestedLibraryId?.toLongOrNull()
        if (requestedLibraryId != null && (libraryId == null || libraryId !in 0..Int.MAX_VALUE.toLong())) {
            call.respondJson(ApiError("invalid_request", "libraryId must be between 0 and ${Int.MAX_VALUE}"), HttpStatusCode.BadRequest)
            return@get
        }
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
        val query = call.request.queryParameters["q"]
        val genre = call.request.queryParameters["genre"]
        val accessibleLibraries = call.catalogLibraryScope()
        if (libraryId != null && accessibleLibraries?.contains(libraryId) == false) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val (requestedTracks, total) = withContext(scanDispatcher) {
            context.mediaCatalogService.tracks(libraryId, query, limit, offset, accessibleLibraries, genre) to
                    context.mediaCatalogService.countTracks(libraryId, query, accessibleLibraries, genre)
        }
        val tracks = requestedTracks.map { it.toCatalogTrackResponse() }
        call.respondJson(TrackPage(tracks, tracks.size, total, limit.coerceIn(1, 200), offset.coerceAtLeast(0)))
    }
    get("/tracks/{id}") {
        if (context.mediaCatalogService == null) {
            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val trackId = call.parameters["id"]?.toLongOrNull()
        if (trackId == null || trackId < 0) {
            call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return@get
        }
        val track = withContext(scanDispatcher) {
            context.mediaCatalogService.track(trackId, call.catalogLibraryScope())
        }
        if (track == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.respondJson(track.toCatalogTrackResponse())
    }
}
