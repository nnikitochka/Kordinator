package org.airsonic.player.ktor

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.airsonic.player.service.LyricsService

/** Exposes lyrics lookup through the authenticated Kordinator API. */
class StandaloneLyricsRoutes(
    private val service: LyricsService?,
    private val dispatcher: CoroutineDispatcher,
) {
    fun routes(root: Route) {
        root.get("/lyrics") {
            val lyricsService = service
            if (lyricsService == null) {
                return@get call.respondJson(
                    ApiError("unavailable", "Lyrics lookup is not configured"), HttpStatusCode.ServiceUnavailable,
                )
            }
            val artist = call.request.queryParameters["artist"]
            val title = call.request.queryParameters["title"]
            val result = withContext(dispatcher) { lyricsService.getLyrics(artist, title) }
            call.respondJson(
                LyricsResponse(
                    result.artist(), result.title(), result.lyrics(),
                    if (call.request.queryParameters["includeLookupStatus"] == "true") result.tryLater() else null,
                ),
            )
        }
    }
}

data class LyricsResponse(val artist: String?, val title: String?, val lyrics: String?, val tryLater: Boolean?)
