package org.airsonic.player.controller.rest.proxy

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.service.ProxyService
import org.slf4j.LoggerFactory

/** Ktor implementation of the legacy external HTTP proxy endpoint. */
class ProxyRestController(private val proxyService: ProxyService = ProxyService()) {
    fun routes(root: Route) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { proxy(call) }
                head(path) { proxy(call) }
            }
        }
    }

    private suspend fun proxy(call: ApplicationCall) {
        // Count legacy clients without exposing target URLs, credentials or tokens.
        LOG.info("Legacy /proxy endpoint invoked; check usage before removing this route")
        val url = call.request.queryParameters["url"]
        if (url.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }

        val upstream = withContext(Dispatchers.IO) {
            proxyService.get(url)
        }

        val status = HttpStatusCode.fromValue(upstream.statusCode)
        if (status != HttpStatusCode.OK) {
            upstream.close()
            call.respond(status)
            return
        }
        if (call.request.httpMethod == HttpMethod.Head) {
            upstream.close()
            call.respond(status)
            return
        }

        try {
            call.respondOutputStream(contentType = null, status = status) {
                withContext(Dispatchers.IO) {
                    upstream.copyBodyTo(this@respondOutputStream)
                }
            }
        } finally {
            withContext(Dispatchers.IO) { upstream.close() }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ProxyRestController::class.java)
        @JvmField val PATHS = arrayOf("/proxy", "/proxy.view")
    }
}
