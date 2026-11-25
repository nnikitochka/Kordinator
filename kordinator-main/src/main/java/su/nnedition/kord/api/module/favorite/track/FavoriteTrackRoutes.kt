package su.nnedition.kord.api.module.favorite.track

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import su.nnedition.kord.security.LocalUserPrincipal
import su.nnedition.kord.service.StandaloneFavoriteService
import su.nnedition.kord.service.library.scan.StandaloneTrack
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.module.favorite.libraryScope
import su.nnedition.kord.api.module.favorite.parsePage
import su.nnedition.kord.api.module.track.TrackResponse
import su.nnedition.kord.api.support.respondJson

fun Route.favoriteTrackRoutes(
    favoriteService: StandaloneFavoriteService?,
    dispatcher: CoroutineDispatcher,
) {
    get("/favorites/tracks") {
        val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        favoriteService ?: return@get call.respondJson(
            ApiError("unavailable", "Favorite storage is not configured"), HttpStatusCode.ServiceUnavailable,
        )
        val page = parsePage(
            call.request.queryParameters["limit"],
            call.request.queryParameters["offset"]
        ) ?: return@get call.respondJson(
            ApiError("invalid_request", "Limit must be between 1 and 100 and offset non-negative"),
            HttpStatusCode.BadRequest,
        )

        val scope = user.libraryScope()
        val tracks = withContext(dispatcher) { favoriteService.list(user.id, page.first, page.second, scope) }
        val total = withContext(dispatcher) { favoriteService.count(user.id, scope) }
        call.respondJson(FavoriteTrackPage(tracks.map(StandaloneTrack::toTrackResponse), tracks.size, total, page.first, page.second))
    }

    put("/tracks/{id}/favorite") {
        call.updateTrackFavorite(favoriteService, dispatcher, true)
    }
    delete("/tracks/{id}/favorite") {
        call.updateTrackFavorite(favoriteService, dispatcher, false)
    }
}

private suspend fun ApplicationCall.updateTrackFavorite(
    favoriteService: StandaloneFavoriteService?,
    dispatcher: CoroutineDispatcher,
    favorite: Boolean
) {
    val user = principal<LocalUserPrincipal>() ?: return respond(HttpStatusCode.Unauthorized)
    val favoriteService = favoriteService ?: return respondJson(
        ApiError("unavailable", "Favorite storage is not configured"), HttpStatusCode.ServiceUnavailable,
    )
    val id = parameters["id"]?.toLongOrNull()
    if (id == null || id < 0) {
        return respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
    }
    val updated = withContext(dispatcher) { favoriteService.set(user.id, id, favorite, user.libraryScope()) }
    if (updated) respond(HttpStatusCode.NoContent) else respond(HttpStatusCode.NotFound)
}

private fun StandaloneTrack.toTrackResponse() = TrackResponse(
    id, libraryId, relativePath, title, artist, album, albumArtist, durationSeconds, sizeBytes, modifiedAt?.toString(),
    discNumber, trackNumber, year, genre,
)
