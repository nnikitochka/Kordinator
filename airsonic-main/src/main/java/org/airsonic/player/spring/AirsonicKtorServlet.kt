package org.airsonic.player.spring

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.isHandled
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingFailureStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.servlet.jakarta.ServletApplicationEngine
import io.ktor.server.servlet.jakarta.ServletApplicationRequest
import io.ktor.util.AttributeKey
import io.ktor.utils.io.InternalAPI
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.controller.rest.RestPlayerResolver
import org.airsonic.player.controller.rest.search.SubsonicSearchController
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.SecurityService

class AirsonicKtorServlet(
    private val searchController: SubsonicSearchController,
    private val playerResolver: RestPlayerResolver,
    private val playerService: PlayerService,
    private val securityService: SecurityService,
) : ServletApplicationEngine() {
    @OptIn(InternalAPI::class)
    override fun init() {
        super.init()
        application.intercept(ApplicationCallPipeline.Setup) {
            val request = (call.request as ServletApplicationRequest).servletRequest
            val searchRequest = request.getAttribute(SEARCH_REQUEST) as? SubsonicSearchController.Request
            if (searchRequest != null) {
                call.attributes.put(SEARCH_REQUEST_KEY, searchRequest)
            }
        }
        application.routing {
            searchController.routes(this) { call -> call.attributes[SEARCH_REQUEST_KEY] }
        }
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

    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
        if (request.servletPath in SubsonicSearchController.PATHS) {
            // Capture Spring's thread-bound authentication before Ktor dispatches to a coroutine.
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false
            )
            val playerRequest = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(playerRequest, response)
            // Security filters may already have consumed a POST body. Use the servlet's parsed parameters.
            val parameters = Parameters.build {
                request.parameterNames.asSequence().distinct().forEach { name ->
                    request.getParameterValues(name)?.let { appendAll(name, it.toList()) }
                }
            }
            request.setAttribute(SEARCH_REQUEST, SubsonicSearchController.Request(username, player, parameters))
        }
        super.service(request, response)
    }

    private companion object {
        const val SEARCH_REQUEST = "org.airsonic.player.searchRequest"
        val SEARCH_REQUEST_KEY = AttributeKey<SubsonicSearchController.Request>(SEARCH_REQUEST)
    }
}
