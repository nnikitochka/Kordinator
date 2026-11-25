package su.nnedition.kord.api.module.favorite

import io.ktor.server.routing.Route
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.module.favorite.album.favoriteAlbumRoutes
import su.nnedition.kord.api.module.favorite.artist.favoriteArtistRoutes
import su.nnedition.kord.api.module.favorite.track.favoriteTrackRoutes
import su.nnedition.kord.api.scanDispatcher

fun Route.favoriteRoutes(
    context: ApiContext
) {
    favoriteAlbumRoutes(context.favoriteService, scanDispatcher)
    favoriteArtistRoutes(context.favoriteService, scanDispatcher)
    favoriteTrackRoutes(context.favoriteService, scanDispatcher)
}
