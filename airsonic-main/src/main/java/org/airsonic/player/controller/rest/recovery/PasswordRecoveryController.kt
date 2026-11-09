package org.airsonic.player.controller.rest.recovery

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.service.PasswordRecoveryService

class PasswordRecoveryController(private val recovery: PasswordRecoveryService, private val json: ObjectMapper) {
    data class Request(val usernameOrEmail: String?, val captcha: String?, val csrfHeader: String?, val csrfToken: String?)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(PATH) { configuration(call, request(call)) }
            head(PATH) { configuration(call, request(call)) }
            post(PATH) {
                val data = request(call)
                val result = withContext(Dispatchers.IO) { recovery.recover(data.usernameOrEmail, data.captcha) }
                respond(call, result)
            }
        }
    }

    private suspend fun configuration(call: ApplicationCall, request: Request) {
        val config = withContext(Dispatchers.IO) { recovery.configuration() }
        respond(call, config + mapOf("csrfHeader" to request.csrfHeader, "csrfToken" to request.csrfToken))
    }

    private suspend fun respond(call: ApplicationCall, data: Map<String, Any?>) {
        call.response.header("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(data), ContentType.Application.Json)
    }

    companion object { const val PATH = "/rest/recover" }
}
