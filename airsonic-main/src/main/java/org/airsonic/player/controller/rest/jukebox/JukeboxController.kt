package org.airsonic.player.controller.rest.jukebox

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.domain.PlayQueue
import org.airsonic.player.service.JukeboxService
import org.airsonic.player.service.PlayQueueService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.JukeboxPlaylist
import org.subsonic.restapi.JukeboxStatus
import org.subsonic.restapi.Response
import kotlin.coroutines.cancellation.CancellationException

class JukeboxController(
    private val queues: PlayQueueService,
    private val jukebox: JukeboxService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> JukeboxRequest) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: JukeboxRequest) {
        val result = try {
            control(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in jukebox API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        if (result == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun control(request: JukeboxRequest): Response? {
        if (!request.authorized) {
            return writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "${request.username} is not authorized to use jukebox.")
        }
        val player = requireNotNull(request.player)
        val params = request.parameters
        val action = params["action"] ?: return missing("action")
        when (action) {
            "start" -> queues.start(player)
            "stop" -> queues.stop(player)
            "skip" -> {
                val index = params["index"] ?: return missing("index")
                val parsed = index.toIntOrNull() ?: return null
                val offset = (params["offset"]?.toLongOrNull() ?: 0L) * 1000
                queues.skip(player, parsed, offset)
            }
            "add" -> queues.add(player, ids(request), null, true, true)
            "set" -> queues.reset(player, ids(request), true)
            "clear" -> queues.clear(player)
            "remove" -> {
                val index = params["index"] ?: return missing("index")
                queues.remove(player, java.util.List.of(index.toIntOrNull() ?: return null))
            }
            "shuffle" -> queues.shuffle(player)
            "setGain" -> {
                val gain = params["gain"] ?: return missing("gain")
                queues.setJukeboxGain(player, gain.toFloatOrNull() ?: return null)
            }
            "get", "status" -> Unit
            else -> throw Exception("Unknown jukebox action: '$action'.")
        }
        val queue = player.playQueue
        val controls = jukebox.canControl(player)
        val index = if (controls && !queue.isEmpty) queue.index else -1
        val playing = controls && !queue.isEmpty && queue.status == PlayQueue.Status.PLAYING
        val gain = jukebox.getGain(player)
        val position = if (controls && !queue.isEmpty) jukebox.getPosition(player) else 0
        val response = writer.createResponse(true)
        if (action == "get") {
            response.jukeboxPlaylist = JukeboxPlaylist().apply {
                currentIndex = index
                this.playing = playing
                this.gain = gain
                this.position = position
                queue.files.forEach { entry.add(mapper.child(player, it, request.username)) }
            }
        } else {
            response.jukeboxStatus = JukeboxStatus().apply {
                currentIndex = index
                this.playing = playing
                this.gain = gain
                this.position = position
            }
        }
        return response
    }

    private fun ids(request: JukeboxRequest): List<Int> {
        return request.parameters.getAll("id")?.map { it.toIntOrNull() ?: return emptyList() } ?: emptyList()
    }

    private fun missing(name: String) = writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Required param ($name) is missing")

    companion object {
        private val LOG = LoggerFactory.getLogger(JukeboxController::class.java)
        @JvmField val PATHS = arrayOf("/rest/jukeboxControl", "/rest/jukeboxControl.view")
    }
}
