package org.airsonic.player.controller.rest.server

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.service.ServerInfoService
import org.airsonic.player.service.SettingsService

class ServerInfoController(private val info: ServerInfoService, private val json: ObjectMapper, private val settings: SettingsService) {
    data class Request(val serverInfo: String, val admin: Boolean)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(INFO_PATH) { respond(call, request(call), false) }
            head(INFO_PATH) { respond(call, request(call), false) }
            get(LOG_PATH) { respond(call, request(call), true) }
            head(LOG_PATH) { respond(call, request(call), true) }
            get(GETTING_STARTED_PATH) { gettingStarted(call, request(call), false) }
            head(GETTING_STARTED_PATH) { gettingStarted(call, request(call), false) }
            post(GETTING_STARTED_PATH) { gettingStarted(call, request(call), true) }
        }
    }

    private suspend fun gettingStarted(call: ApplicationCall, request: Request, disable: Boolean) {
        call.response.header("Cache-Control", "no-store")
        if (disable && !request.admin) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }
        val result = withContext(Dispatchers.IO) {
            if (disable) {
                settings.isGettingStartedEnabled = false
                settings.save()
            }
            mapOf("runningAsRoot" to (System.getProperty("user.name") == "root"),
                "enabled" to settings.isGettingStartedEnabled, "canDisable" to request.admin)
        }
        call.respondText(json.writeValueAsString(result), ContentType.Application.Json)
    }

    private suspend fun respond(call: ApplicationCall, request: Request, log: Boolean) {
        call.response.header("Cache-Control", "no-store")
        if (log && !request.admin) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }
        val result = withContext(Dispatchers.IO) {
            if (log) info.log() else info.snapshot(request.serverInfo) + mapOf("canReadLog" to request.admin)
        }
        call.respondText(json.writeValueAsString(result), ContentType.Application.Json)
    }

    companion object {
        const val INFO_PATH = "/rest/serverInfo"
        const val LOG_PATH = "/rest/serverLog"
        const val GETTING_STARTED_PATH = "/rest/gettingStarted"
        @JvmField val PATHS = arrayOf(INFO_PATH, LOG_PATH)
    }
}
