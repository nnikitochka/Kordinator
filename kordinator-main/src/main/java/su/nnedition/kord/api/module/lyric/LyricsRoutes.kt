package su.nnedition.kord.api.module.lyric

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import su.nnedition.kord.service.LyricsService
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.support.respondJson

/** Exposes lyrics lookup through the authenticated Kordinator API. */
fun Route.lyricsRoutes(
    lyricsService: LyricsService?,
    dispatcher: CoroutineDispatcher,
) {
    get("/lyrics") {
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
