package org.airsonic.player.controller.rest.nowplaying

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.service.StatusService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.NowPlaying
import org.subsonic.restapi.NowPlayingEntry
import kotlin.coroutines.cancellation.CancellationException

class NowPlayingController(
    private val status: StatusService,
    private val mapper: SubsonicResponseMapper,
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

    private suspend fun respond(call: ApplicationCall, parameters: Parameters) {
        val result = try {
            val nowPlaying = NowPlaying()
            val plays = status.activePlays.asSequence() + status.inactivePlays.asSequence()
            plays.forEach { info ->
                val play = requireNotNull(info.fromPlayStatus())
                val player = play.player
                val entry = NowPlayingEntry().apply {
                    username = player.username
                    playerId = player.id
                    playerName = player.name
                    minutesAgo = play.minutesAgo.toInt()
                }
                nowPlaying.entry.add(mapper.child(entry, player, play.mediaFile, requireNotNull(entry.username)))
            }
            writer.createResponse(true).apply { this.nowPlaying = nowPlaying }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in now playing API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        val serialized = writer.serializeForType(parameters["f"] ?: "xml", parameters["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(NowPlayingController::class.java)
        @JvmField val PATHS = arrayOf("/rest/getNowPlaying", "/rest/getNowPlaying.view")
    }
}
