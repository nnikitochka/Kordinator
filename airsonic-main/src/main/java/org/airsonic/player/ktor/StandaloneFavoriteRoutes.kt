package org.airsonic.player.ktor

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.service.StandaloneAlbum
import org.airsonic.player.service.StandaloneArtist
import org.airsonic.player.service.StandaloneFavoriteService
import org.airsonic.player.service.StandaloneTrack
import java.util.Base64

data class FavoriteTrackPage(val items: List<TrackResponse>, val count: Int, val total: Long, val limit: Int, val offset: Int)
data class FavoriteAlbumPage(val items: List<AlbumSummaryResponse>, val count: Int, val total: Long, val limit: Int, val offset: Int)
data class FavoriteArtistPage(val items: List<ArtistSummaryResponse>, val count: Int, val total: Long, val limit: Int, val offset: Int)

/** Routes for the standalone account's favorites, backed by its own database and bearer identity. */
class StandaloneFavoriteRoutes(
    private val service: StandaloneFavoriteService?,
    private val dispatcher: CoroutineDispatcher,
) {
    fun routes(root: Route) {
        root.get("/favorites/tracks") {
            val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val favoriteService = service ?: return@get call.respondJson(
                ApiError("unavailable", "Favorite storage is not configured"), HttpStatusCode.ServiceUnavailable,
            )
            val page = parsePage(call.request.queryParameters["limit"], call.request.queryParameters["offset"])
                ?: return@get call.respondJson(
                    ApiError("invalid_request", "Limit must be between 1 and 100 and offset non-negative"),
                    HttpStatusCode.BadRequest,
                )
            val scope = user.libraryScope()
            val tracks = withContext(dispatcher) { favoriteService.list(user.id, page.first, page.second, scope) }
            val total = withContext(dispatcher) { favoriteService.count(user.id, scope) }
            call.respondJson(FavoriteTrackPage(tracks.map(StandaloneTrack::toTrackResponse), tracks.size, total, page.first, page.second))
        }

        root.get("/favorites/albums") {
            val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val favoriteService = service ?: return@get call.respondJson(
                ApiError("unavailable", "Favorite storage is not configured"), HttpStatusCode.ServiceUnavailable,
            )
            val page = parsePage(call.request.queryParameters["limit"], call.request.queryParameters["offset"])
                ?: return@get call.respondJson(
                    ApiError("invalid_request", "Limit must be between 1 and 100 and offset non-negative"),
                    HttpStatusCode.BadRequest,
                )
            val favorites = withContext(dispatcher) { favoriteService.albums(user.id, user.libraryScope()) }
            val pageItems = favorites.drop(page.second).take(page.first)
            call.respondJson(
                FavoriteAlbumPage(pageItems.map(StandaloneAlbum::toSummaryResponse), favorites.size, favorites.size.toLong(), page.first, page.second),
            )
        }

        root.get("/favorites/artists") {
            val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val favoriteService = service ?: return@get call.respondJson(
                ApiError("unavailable", "Favorite storage is not configured"), HttpStatusCode.ServiceUnavailable,
            )
            val page = parsePage(call.request.queryParameters["limit"], call.request.queryParameters["offset"])
                ?: return@get call.respondJson(
                    ApiError("invalid_request", "Limit must be between 1 and 100 and offset non-negative"),
                    HttpStatusCode.BadRequest,
                )
            val favorites = withContext(dispatcher) { favoriteService.artists(user.id, user.libraryScope()) }
            val pageItems = favorites.drop(page.second).take(page.first)
            call.respondJson(
                FavoriteArtistPage(pageItems.map(StandaloneArtist::toSummaryResponse), favorites.size, favorites.size.toLong(), page.first, page.second),
            )
        }

        root.put("/tracks/{id}/favorite") { call.updateTrackFavorite(favorite = true) }
        root.delete("/tracks/{id}/favorite") { call.updateTrackFavorite(favorite = false) }
        root.put("/albums/{id}/favorite") { call.updateCatalogFavorite(favorite = true, artist = false) }
        root.delete("/albums/{id}/favorite") { call.updateCatalogFavorite(favorite = false, artist = false) }
        root.put("/artists/{id}/favorite") { call.updateCatalogFavorite(favorite = true, artist = true) }
        root.delete("/artists/{id}/favorite") { call.updateCatalogFavorite(favorite = false, artist = true) }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.updateTrackFavorite(favorite: Boolean) {
        val user = principal<LocalUserPrincipal>() ?: return respond(HttpStatusCode.Unauthorized)
        val favoriteService = service ?: return respondJson(
            ApiError("unavailable", "Favorite storage is not configured"), HttpStatusCode.ServiceUnavailable,
        )
        val id = parameters["id"]?.toLongOrNull()
        if (id == null || id < 0) {
            return respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
        }
        val updated = withContext(dispatcher) { favoriteService.set(user.id, id, favorite, user.libraryScope()) }
        if (updated) respond(HttpStatusCode.NoContent) else respond(HttpStatusCode.NotFound)
    }

    private suspend fun io.ktor.server.application.ApplicationCall.updateCatalogFavorite(favorite: Boolean, artist: Boolean) {
        val user = principal<LocalUserPrincipal>() ?: return respond(HttpStatusCode.Unauthorized)
        val favoriteService = service ?: return respondJson(
            ApiError("unavailable", "Favorite storage is not configured"), HttpStatusCode.ServiceUnavailable,
        )
        val selector = parameters["id"]?.decodeCatalogId()
        if (selector == null) {
            return respondJson(ApiError("invalid_request", "Catalog ID is invalid"), HttpStatusCode.BadRequest)
        }
        val saved = try {
            withContext(dispatcher) {
                if (artist) favoriteService.setArtist(user.id, selector.first, selector.second, favorite, user.libraryScope())
                else favoriteService.setAlbum(user.id, selector.first, selector.second, favorite, user.libraryScope())
            }
        } catch (failure: IllegalArgumentException) {
            return respondJson(ApiError("invalid_request", failure.message ?: "Catalog favorite is invalid"), HttpStatusCode.BadRequest)
        }
        if (saved) respond(HttpStatusCode.NoContent) else respond(HttpStatusCode.NotFound)
    }
}

private fun parsePage(limitValue: String?, offsetValue: String?): Pair<Int, Int>? {
    val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 50 else null
    val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
    if (limit == null || limit !in 1..100 || offset == null || offset < 0) return null
    return limit to offset
}

private fun LocalUserPrincipal.libraryScope() = if (role == "ADMIN") null else allowedLibraryIds

private fun String.decodeCatalogId(): Pair<Long, String>? {
    return try {
        val separator = indexOf('.')
        if (separator < 1 || separator == lastIndex) return null
        val libraryId = substring(0, separator).toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val name = Base64.getUrlDecoder().decode(substring(separator + 1)).toString(Charsets.UTF_8)
        if (name.isBlank()) null else libraryId to name
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun StandaloneTrack.toTrackResponse() = TrackResponse(
    id, libraryId, relativePath, title, artist, album, albumArtist, durationSeconds, sizeBytes, modifiedAt?.toString(),
    discNumber, trackNumber, year, genre,
)

private fun StandaloneAlbum.toSummaryResponse() = AlbumSummaryResponse(
    catalogId(libraryId, name), libraryId, name, artist, year, genre, trackCount, durationSeconds,
)

private fun StandaloneArtist.toSummaryResponse() = ArtistSummaryResponse(
    catalogId(libraryId, name), libraryId, name, albumCount, trackCount,
)

private fun catalogId(libraryId: Long, name: String) =
    "$libraryId.${Base64.getUrlEncoder().withoutPadding().encodeToString(name.toByteArray(Charsets.UTF_8))}"
