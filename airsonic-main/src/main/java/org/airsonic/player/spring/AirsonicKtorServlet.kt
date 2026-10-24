package org.airsonic.player.spring

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.isHandled
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingFailureStatusCode
import io.ktor.server.servlet.jakarta.ServletApplicationEngine
import io.ktor.utils.io.InternalAPI

class AirsonicKtorServlet : ServletApplicationEngine() {
    @OptIn(InternalAPI::class)
    override fun init() {
        super.init()
        // The servlet engine omits BaseApplicationEngine's default fallback in Ktor 3.6.0.
        // Keep this compatibility code in the temporary servlet adapter.
        application.intercept(ApplicationCallPipeline.Fallback) {
            if (!call.isHandled) {
                call.respond(
                    call.response.status()
                        ?: call.attributes.getOrNull(RoutingFailureStatusCode)
                        ?: HttpStatusCode.NotFound
                )
            }
        }
    }
}
