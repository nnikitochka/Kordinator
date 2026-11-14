package org.airsonic.player.controller.rest.music

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import org.airsonic.player.domain.CoverArtScheme
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService

/** Website configuration for the main music directory page. */
class MainPageController(
    private val security: SecurityService,
    private val settings: SettingsService,
    private val json: ObjectMapper,
) {
    data class Request(val username: String, val ids: List<Int>, val paths: Map<String, Set<String>>)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(DATA_PATH) { respond(call, request(call)) }
            head(DATA_PATH) { respond(call, request(call)) }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val user = security.getUserByName(request.username)
        val userSettings = settings.getUserSettings(request.username)
        val body = mapOf(
            "coverArtSizeMedium" to CoverArtScheme.MEDIUM.size,
            "coverArtSizeLarge" to CoverArtScheme.LARGE.size,
            "visibility" to userSettings.mainVisibility,
            "showArtistInfo" to userSettings.showArtistInfoEnabled,
            "partyMode" to userSettings.partyModeEnabled,
            "viewAsList" to userSettings.viewAsList,
            "initialPaginationSizeFiles" to userSettings.paginationSizeFiles,
            "initialPaginationSizeFolders" to userSettings.paginationSizeFolders,
            "initialIds" to request.ids,
            "initialPaths" to request.paths,
            "roles" to mapOf(
                "stream" to user.isStreamRole,
                "download" to user.isDownloadRole,
                "coverArt" to user.isCoverArtRole,
                "comment" to user.isCommentRole,
                "share" to user.isShareRole,
            ),
        )
        call.response.header("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(body), ContentType.Application.Json)
    }

    companion object {
        const val DATA_PATH = "/rest/mainPageSettings"
        const val REQUEST_ATTRIBUTE = "org.airsonic.player.mainPageRequest"
        @JvmField val PAGE_PATHS = arrayOf("/main", "/main.view")
    }
}
