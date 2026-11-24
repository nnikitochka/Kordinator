package su.nnedition.kord.api.module.favorite.album

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
import org.airsonic.player.service.StandaloneFavoriteService
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.module.album.AlbumSummaryResponse
import su.nnedition.kord.api.module.favorite.catalogId
import su.nnedition.kord.api.module.favorite.libraryScope
import su.nnedition.kord.api.module.favorite.parsePage
import su.nnedition.kord.api.module.favorite.updateCatalogFavorite
import su.nnedition.kord.api.support.respondJson

fun Route.favoriteAlbumRoutes(
    favoriteService: StandaloneFavoriteService?,
    dispatcher: CoroutineDispatcher,
) {
    get("/favorites/albums") {
        val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        favoriteService ?: return@get call.respondJson(
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

    put("/albums/{id}/favorite") {
        call.updateCatalogFavorite(favoriteService, dispatcher, favorite = true, artist = false)
    }
    delete("/albums/{id}/favorite") {
        call.updateCatalogFavorite(favoriteService, dispatcher, favorite = false, artist = false)
    }
}

internal fun StandaloneAlbum.toSummaryResponse() = AlbumSummaryResponse(
    catalogId(libraryId, name), libraryId, name, artist, year, genre, trackCount, durationSeconds,
)
