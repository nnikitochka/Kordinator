package org.airsonic.player.controller.rest.ping

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter

class PingController(private val jaxbWriter: JAXBWriter) {
    fun routes(root: Route, parameters: (ApplicationCall) -> Parameters) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, parameters(call)) }
                post(path) { respond(call, parameters(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, parameters: Parameters) {
        val serialized = jaxbWriter.serializeForType(
            parameters["f"] ?: "xml", parameters["callback"], jaxbWriter.createResponse(true)
        )
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        @JvmField
        val PATHS = arrayOf("/rest/ping", "/rest/ping.view")
    }
}
