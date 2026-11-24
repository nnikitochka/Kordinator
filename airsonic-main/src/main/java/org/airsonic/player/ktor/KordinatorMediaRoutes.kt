package org.airsonic.player.ktor

import io.ktor.server.routing.Route

import io.ktor.server.http.HttpRequestLifecycle
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import su.nnedition.kord.api.ApiContext


internal class KordinatorMediaRoutes(private val api: ApiContext) {
    fun routes(route: Route) = route.apply {
        with(api) {
            route("/tracks/{id}/stream") {
                install(HttpRequestLifecycle) { cancelCallOnClose = true }
                get {
                    respondTrackFile(call, libraryScanService, mediaCatalogService, false, false,
                        audioTranscoding = audioTranscoding, mediaFiles = mediaFileService)
                }
                head {
                    respondTrackFile(call, libraryScanService, mediaCatalogService, false, true,
                        audioTranscoding = audioTranscoding, mediaFiles = mediaFileService)
                }
            }
            get("/tracks/{id}/download") { respondTrackFile(call, libraryScanService, mediaCatalogService, true, false) }
            head("/tracks/{id}/download") {
                respondTrackFile(call, libraryScanService, mediaCatalogService, true, true)
            }
            get("/tracks/{id}/artwork") { respondTrackArtwork(call, mediaCatalogService, libraryScanService, head = false) }
            head("/tracks/{id}/artwork") { respondTrackArtwork(call, mediaCatalogService, libraryScanService, head = true) }
            get("/albums/{id}/artwork") {
                respondCatalogEntityArtwork(call, mediaCatalogService, libraryScanService, artist = false, head = false)
            }
            head("/albums/{id}/artwork") {
                respondCatalogEntityArtwork(call, mediaCatalogService, libraryScanService, artist = false, head = true)
            }
            get("/artists/{id}/artwork") {
                respondCatalogEntityArtwork(call, mediaCatalogService, libraryScanService, artist = true, head = false)
            }
            head("/artists/{id}/artwork") {
                respondCatalogEntityArtwork(call, mediaCatalogService, libraryScanService, artist = true, head = true)
            }
            get("/playlists/{id}/artwork") { respondPlaylistArtwork(call, playlistService, libraryScanService, head = false) }
            head("/playlists/{id}/artwork") { respondPlaylistArtwork(call, playlistService, libraryScanService, head = true) }
        }
    }
}
