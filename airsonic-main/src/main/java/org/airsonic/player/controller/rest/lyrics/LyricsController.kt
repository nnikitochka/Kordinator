package org.airsonic.player.controller.rest.lyrics

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.ajax.lyrics.LyricsWSController
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Lyrics
import kotlin.coroutines.cancellation.CancellationException

class LyricsController(
    private val lyrics: LyricsWSController,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, parameters: (ApplicationCall) -> Parameters) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, parameters(call)) }
                post(path) { respond(call, parameters(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, params: Parameters) {
        val result = try {
            val info = lyrics.getLyrics(params["artist"], params["title"])
            val node = Lyrics().apply {
                artist = info.artist
                title = info.title
                content = info.lyrics
            }
            writer.createResponse(true).apply { this.lyrics = node }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in lyrics API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        val serialized = writer.serializeForType(params["f"] ?: "xml", params["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(LyricsController::class.java)
        @JvmField val PATHS = arrayOf("/rest/getLyrics", "/rest/getLyrics.view")
    }
}
