package su.nnedition.kord.api.module.media

import io.ktor.server.http.HttpRequestLifecycle
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.route
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.support.respondCatalogEntityArtwork
import su.nnedition.kord.api.support.respondPlaylistArtwork
import su.nnedition.kord.api.support.respondTrackArtwork
import su.nnedition.kord.api.support.respondTrackFile

fun Route.mediaRoutes(
    context: ApiContext,
) {
    route("/tracks/{id}/stream") {
        install(HttpRequestLifecycle) { cancelCallOnClose = true }
        get {
            respondTrackFile(call, context.libraryScanService, context.mediaCatalogService, false, false,
                audioTranscoding = context.audioTranscoding, mediaFiles = context.mediaFileService)
        }
        head {
            respondTrackFile(call, context.libraryScanService, context.mediaCatalogService, false, true,
                audioTranscoding = context.audioTranscoding, mediaFiles = context.mediaFileService)
        }
    }
    get("/tracks/{id}/download") { respondTrackFile(call, context.libraryScanService, context.mediaCatalogService, true, false) }
    head("/tracks/{id}/download") {
        respondTrackFile(call, context.libraryScanService, context.mediaCatalogService, true, true)
    }
    get("/tracks/{id}/artwork") { respondTrackArtwork(call, context.mediaCatalogService, context.libraryScanService, head = false) }
    head("/tracks/{id}/artwork") { respondTrackArtwork(call, context.mediaCatalogService, context.libraryScanService, head = true) }
    get("/albums/{id}/artwork") {
        respondCatalogEntityArtwork(call, context.mediaCatalogService, context.libraryScanService, artist = false, head = false)
    }
    head("/albums/{id}/artwork") {
        respondCatalogEntityArtwork(call, context.mediaCatalogService, context.libraryScanService, artist = false, head = true)
    }
    get("/artists/{id}/artwork") {
        respondCatalogEntityArtwork(call, context.mediaCatalogService, context.libraryScanService, artist = true, head = false)
    }
    head("/artists/{id}/artwork") {
        respondCatalogEntityArtwork(call, context.mediaCatalogService, context.libraryScanService, artist = true, head = true)
    }
    get("/playlists/{id}/artwork") { respondPlaylistArtwork(call, context.playlistService, context.libraryScanService, head = false) }
    head("/playlists/{id}/artwork") { respondPlaylistArtwork(call, context.playlistService, context.libraryScanService, head = true) }
}
