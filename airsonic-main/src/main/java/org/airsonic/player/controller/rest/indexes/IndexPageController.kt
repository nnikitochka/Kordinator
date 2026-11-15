package org.airsonic.player.controller.rest.indexes

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import org.airsonic.player.service.SettingsService

/** Minimal user-specific settings used to initialize the website frames. */
class IndexPageController(
    private val settings: SettingsService,
    private val json: ObjectMapper,
) {
    data class Request(val username: String)

    data class PageSettings(
        val showSideBar: Boolean,
        val showRight: Boolean,
        val keyboardShortcutsEnabled: Boolean,
    )

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(DATA_PATH) { respond(call, request(call)) }
            head(DATA_PATH) { respond(call, request(call)) }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val userSettings = settings.getUserSettings(request.username)
        val data = PageSettings(
            userSettings.showSideBar,
            userSettings.showNowPlayingEnabled,
            userSettings.keyboardShortcutsEnabled,
        )
        call.response.header("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(data), ContentType.Application.Json)
    }

    companion object {
        const val DATA_PATH = "/rest/indexPageSettings"
        const val REQUEST_ATTRIBUTE = "org.airsonic.player.indexPageRequest"
        @JvmField val PAGE_PATHS = arrayOf("/index", "/index.view")
    }
}
