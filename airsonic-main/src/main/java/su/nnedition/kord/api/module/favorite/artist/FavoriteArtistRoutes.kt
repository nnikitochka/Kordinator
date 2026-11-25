package su.nnedition.kord.api.module.favorite.artist

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
import org.airsonic.player.service.library.scan.StandaloneArtist
import org.airsonic.player.service.StandaloneFavoriteService
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.module.artist.ArtistSummaryResponse
import su.nnedition.kord.api.module.favorite.catalogId
import su.nnedition.kord.api.module.favorite.libraryScope
import su.nnedition.kord.api.module.favorite.parsePage
import su.nnedition.kord.api.module.favorite.updateCatalogFavorite
import su.nnedition.kord.api.support.respondJson

fun Route.favoriteArtistRoutes(
    favoriteService: StandaloneFavoriteService?,
    dispatcher: CoroutineDispatcher,
) {
    get("/favorites/artists") {
        val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        favoriteService ?: return@get call.respondJson(
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

    put("/artists/{id}/favorite") {
        call.updateCatalogFavorite(favoriteService, dispatcher, favorite = true, artist = true)
    }
    delete("/artists/{id}/favorite") {
        call.updateCatalogFavorite(favoriteService, dispatcher, favorite = false, artist = true)
    }
}

private fun StandaloneArtist.toSummaryResponse() = ArtistSummaryResponse(
    catalogId(libraryId, name), libraryId, name, albumCount, trackCount,
)
