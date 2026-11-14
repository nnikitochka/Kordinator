package org.airsonic.player.controller.rest.bookmarks

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import org.airsonic.player.service.SettingsService

/** REST settings used to initialize the bookmarks page. */
class BookmarksPageController(private val settings: SettingsService, private val json: ObjectMapper) {
    data class Request(val username: String)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(DATA_PATH) { respond(call, request(call)) }
            head(DATA_PATH) { respond(call, request(call)) }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val size = settings.getUserSettings(request.username).paginationSizeBookmarks
        call.response.header("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(mapOf("initialPaginationSize" to size)), ContentType.Application.Json)
    }

    companion object {
        const val DATA_PATH = "/rest/bookmarksPageSettings"
        const val REQUEST_ATTRIBUTE = "org.airsonic.player.bookmarksPageRequest"
        @JvmField val PAGE_PATHS = arrayOf("/bookmarks", "/bookmarks.view")
    }
}
