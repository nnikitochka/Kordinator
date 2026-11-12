package org.airsonic.player.controller.rest.playqueue

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.response.header
import io.ktor.server.routing.head
import org.airsonic.player.domain.User
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.SettingsService

/** REST data endpoints used by the play queue web page. */
class PlayQueuePageController(
    private val players: PlayerService,
    private val settings: SettingsService,
    private val json: ObjectMapper,
) {
    data class Request(val user: User, val player: org.airsonic.player.domain.Player?)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(SETTINGS_PATH) { respondSettings(call, request(call)) }
            head(SETTINGS_PATH) { respondSettings(call, request(call)) }
            PLAYER_PATHS.forEach { path ->
                get(path) { respondPlayer(call, request(call)) }
                head(path) { respondPlayer(call, request(call)) }
            }
        }
    }

    private suspend fun respondSettings(call: ApplicationCall, request: Request) {
        val user = request.user
        val userSettings = settings.getUserSettings(user.username)
        val result = mapOf(
            "user" to mapOf("settingsRole" to user.isSettingsRole, "streamRole" to user.isStreamRole,
                "downloadRole" to user.isDownloadRole, "shareRole" to user.isShareRole),
            "players" to players.getPlayersForUserAndClientId(user.username, null).map {
                mapOf("id" to it.id, "description" to it.shortDescription)
            },
            "visibility" to userSettings.playqueueVisibility,
            "partyMode" to userSettings.partyModeEnabled,
            "notify" to userSettings.songNotificationEnabled,
            "autoHide" to userSettings.autoHidePlayQueue,
            "initialPaginationSize" to userSettings.paginationSizePlayqueue,
            "autoBookmark" to userSettings.autoBookmark,
            "audioBookmarkFrequency" to userSettings.audioBookmarkFrequency,
        )
        respond(call, result)
    }

    private suspend fun respondPlayer(call: ApplicationCall, request: Request) {
        val player = request.player
        if (player == null) {
            call.respondText("{}", ContentType.Application.Json)
            return
        }
        respond(call, mapOf("id" to player.id, "description" to player.shortDescription,
            "tech" to player.technology))
    }

    private suspend fun respond(call: ApplicationCall, value: Any) {
        call.response.header("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(value), ContentType.Application.Json)
    }

    companion object {
        const val SETTINGS_PATH = "/rest/playQueueSettings"
        const val PLAYER_PATH = "/rest/playQueuePlayer"
        @JvmField val PATHS = arrayOf(SETTINGS_PATH, PLAYER_PATH, "/playQueue/player")
        @JvmField val PLAYER_PATHS = arrayOf(PLAYER_PATH, "/playQueue/player")
    }
}
