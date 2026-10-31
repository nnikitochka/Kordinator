package org.airsonic.player.controller.rest.hls

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post

class HlsRestController {
    fun routes(root: Route, playlist: (ApplicationCall) -> String) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { call.respondText(playlist(call), ContentType.parse("application/x-mpegurl; charset=UTF-8")) }
                post(path) { call.respondText(playlist(call), ContentType.parse("application/x-mpegurl; charset=UTF-8")) }
                head(path) { call.respondText(playlist(call), ContentType.parse("application/x-mpegurl; charset=UTF-8")) }
            }
        }
    }

    companion object {
        @JvmField
        val PATHS = arrayOf("/rest/hls", "/rest/hls.view")
    }
}
