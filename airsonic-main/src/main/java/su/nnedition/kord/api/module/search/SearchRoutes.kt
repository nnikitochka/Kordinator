package su.nnedition.kord.api.module.search

import io.ktor.server.routing.Route

import io.ktor.server.auth.principal
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.domain.RandomSearchCriteria
import org.airsonic.player.ktor.catalogLibraryScope
import org.airsonic.player.ktor.decodeKordinatorCatalogId
import org.airsonic.player.ktor.respondJson
import org.airsonic.player.ktor.toResponse
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.authDispatcher
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.module.track.TrackResponse
import su.nnedition.kord.api.scanDispatcher

fun Route.searchRoutes(
    context: ApiContext,
) {
    get("/search") {
        if (context.mediaCatalogService == null) {
            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val query = call.request.queryParameters["query"]?.trim()
        val offsetValue = call.request.queryParameters["offset"]
        val limitValue = call.request.queryParameters["limit"]
        val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
        val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 25 else null
        if (query.isNullOrEmpty() || offset == null || offset < 0 || limit == null || limit !in 1..100) {
            call.respondJson(
                ApiError(
                    "invalid_request",
                    "Query is required, offset must be non-negative and limit must be between 1 and 100"
                ), HttpStatusCode.BadRequest)
            return@get
        }
        val accessibleLibraries = call.catalogLibraryScope()
        val (matches, total) = withContext(scanDispatcher) {
            context.mediaCatalogService.tracks(null, query, limit, offset, accessibleLibraries) to
                    context.mediaCatalogService.countTracks(null, query, accessibleLibraries)
        }
        call.respondJson(SearchResponse(query, offset, total, matches.map {
            TrackResponse(
                it.id, it.libraryId, it.relativePath, it.title, it.artist, it.album,
                it.albumArtist, it.durationSeconds, it.sizeBytes, it.modifiedAt?.toString()
            )
        }))
    }
    get("/tracks/random") {
        val search = context.randomSearchService
        val folders = context.mediaFolderService
        val catalog = context.mediaCatalogService
        if (search == null || folders == null || catalog == null) {
            call.respondJson(ApiError("unavailable", "Random track search is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val parameters = call.request.queryParameters
        val rawSize = parameters["size"]
        val size = rawSize?.toIntOrNull() ?: if (rawSize == null) 10 else null
        val rawFromYear = parameters["fromYear"]
        val fromYear = rawFromYear?.toIntOrNull()
        val rawToYear = parameters["toYear"]
        val toYear = rawToYear?.toIntOrNull()
        val rawLibraryId = parameters["libraryId"]
        val libraryId = rawLibraryId?.toLongOrNull()
        if (size == null || size !in 0..500 ||
            rawFromYear != null && fromYear == null || rawToYear != null && toYear == null ||
            rawLibraryId != null && (libraryId == null || libraryId !in 0..Int.MAX_VALUE.toLong())) {
            call.respondJson(ApiError("invalid_request", "Invalid size, year, or libraryId"), HttpStatusCode.BadRequest)
            return@get
        }
        val libraryScope = user.catalogLibraryScope()
        if (libraryId != null && libraryScope != null && libraryId !in libraryScope) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val allowedFolders = withContext(authDispatcher) {
            folders.getAllMusicFolders(false, false).filter { folder ->
                val id = folder.id?.toLong()
                id != null && (libraryScope == null || id in libraryScope) && (libraryId == null || id == libraryId)
            }
        }
        val result = withContext(scanDispatcher) {
            search.getRandomSongs(RandomSearchCriteria(size, parameters["genre"], fromYear, toYear, allowedFolders))
        }
        val tracks = withContext(scanDispatcher) {
            catalog.tracksByCoreIds(result.map { it.id!!.toLong() }, libraryScope)
        }
        call.respondJson(RandomTrackResponse(tracks.map { it.toResponse() }, tracks.size))
    }
    get("/artists/{id}/top-tracks") {
        val service = context.topSongsService
        val folders = context.mediaFolderService
        val catalog = context.mediaCatalogService
        if (service == null || folders == null || catalog == null) {
            call.respondJson(ApiError("unavailable", "Top track lookup is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val artist = call.parameters["id"]?.decodeKordinatorCatalogId()
        if (artist == null || user.catalogLibraryScope()?.contains(artist.first) == false) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val requestedCount = call.request.queryParameters["count"]
        val count = requestedCount?.toIntOrNull() ?: if (requestedCount == null) 50 else null
        if (count == null || count !in 0..500) {
            call.respondJson(ApiError("invalid_request", "count must be between 0 and 500"), HttpStatusCode.BadRequest)
            return@get
        }
        val scope = user.catalogLibraryScope()
        val allowedFolders = withContext(authDispatcher) {
            folders.getAllMusicFolders(false, false).filter { folder ->
                val id = folder.id?.toLong()
                id != null && (scope == null || id in scope)
            }
        }
        val recommendations = withContext(scanDispatcher) {
            service.getTopSongs(artist.second, count, allowedFolders)
        }
        val tracks = withContext(scanDispatcher) {
            catalog.tracksByCoreIds(recommendations.map { it.id!!.toLong() }, scope)
        }
        call.respondJson(TopTrackPage(artist.second, tracks.map { it.toResponse() }, tracks.size))
    }
    get("/artists/{id}/similar-tracks") {
        val recommendations = context.topSongsService
        val folders = context.mediaFolderService
        val catalog = context.mediaCatalogService
        val dao = context.artistDao
        if (recommendations == null || folders == null || catalog == null || dao == null) {
            call.respondJson(ApiError("unavailable", "Similar track lookup is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val artist = call.parameters["id"]?.decodeKordinatorCatalogId()
        val scope = user.catalogLibraryScope()
        if (artist == null || scope?.contains(artist.first) == false) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val requestedCount = call.request.queryParameters["count"]
        val count = requestedCount?.toIntOrNull() ?: if (requestedCount == null) 50 else null
        if (count == null || count !in 0..500) {
            call.respondJson(ApiError("invalid_request", "count must be between 0 and 500"), HttpStatusCode.BadRequest)
            return@get
        }
        if (withContext(scanDispatcher) { catalog.tracksForArtist(artist.first, artist.second, scope) }.isEmpty()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val allowedFolders = withContext(authDispatcher) {
            folders.getAllMusicFolders(false, false).filter { folder ->
                val id = folder.id?.toLong()
                id != null && (scope == null || id in scope)
            }
        }
        val coreArtist = withContext(scanDispatcher) { dao.getArtist(artist.second, allowedFolders) }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        val similar = withContext(scanDispatcher) {
            recommendations.getSimilarSongs(coreArtist, count, allowedFolders)
        }
        val tracks = withContext(scanDispatcher) {
            catalog.tracksByCoreIds(similar.map { it.id!!.toLong() }, scope)
        }
        call.respondJson(SimilarTrackPage(artist.second, tracks.map { it.toResponse() }, tracks.size))
    }
    get("/tracks/{id}/similar-tracks") {
        val recommendations = context.topSongsService
        val folders = context.mediaFolderService
        val catalog = context.mediaCatalogService
        val files = context.mediaFileService
        if (recommendations == null || folders == null || catalog == null || files == null) {
            call.respondJson(ApiError("unavailable", "Similar track lookup is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val trackId = call.parameters["id"]?.toLongOrNull()
        if (trackId == null || trackId !in 0..Int.MAX_VALUE.toLong()) {
            call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative integer"), HttpStatusCode.BadRequest)
            return@get
        }
        val scope = user.catalogLibraryScope()
        if (catalog.track(trackId, scope) == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val requestedCount = call.request.queryParameters["count"]
        val count = requestedCount?.toIntOrNull() ?: if (requestedCount == null) 50 else null
        if (count == null || count !in 0..500) {
            call.respondJson(ApiError("invalid_request", "count must be between 0 and 500"), HttpStatusCode.BadRequest)
            return@get
        }
        val allowedFolders = withContext(authDispatcher) {
            folders.getAllMusicFolders(false, false).filter { folder ->
                val id = folder.id?.toLong()
                id != null && (scope == null || id in scope)
            }
        }
        val mediaFile = withContext(scanDispatcher) { files.getMediaFile(trackId.toInt()) }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        val similar = withContext(scanDispatcher) {
            recommendations.getSimilarSongs(mediaFile, count, allowedFolders)
        }
        val tracks = withContext(scanDispatcher) {
            catalog.tracksByCoreIds(similar.map { it.id!!.toLong() }, scope)
        }
        call.respondJson(SimilarTrackPage(null, tracks.map { it.toResponse() }, tracks.size))
    }
}
