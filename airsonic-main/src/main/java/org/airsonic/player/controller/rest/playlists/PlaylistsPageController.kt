package org.airsonic.player.controller.rest.playlists

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import org.airsonic.player.service.SettingsService

/** Website configuration for the playlist listing page. */
class PlaylistsPageController(private val settings: SettingsService, private val json: ObjectMapper) {
    data class Request(val username: String)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(DATA_PATH) { respond(call, request(call)) }
            head(DATA_PATH) { respond(call, request(call)) }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val userSettings = settings.getUserSettings(request.username)
        val body = mapOf(
            "username" to request.username,
            "viewAsList" to userSettings.viewAsList,
            "initialPaginationSize" to userSettings.paginationSizePlaylist,
        )
        call.response.header("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(body), ContentType.Application.Json)
    }

    companion object {
        const val DATA_PATH = "/rest/playlistsPageSettings"
        const val REQUEST_ATTRIBUTE = "org.airsonic.player.playlistsPageRequest"
        @JvmField val PAGE_PATHS = arrayOf("/playlists", "/playlists.view")
    }
}
