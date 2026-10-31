package org.airsonic.player.controller.rest.scrobble

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.domain.PlayStatus
import org.airsonic.player.service.AudioScrobblerService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.StatusService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class ScrobbleController(
    private val mediaFiles: MediaFileService,
    private val status: StatusService,
    private val scrobbler: AudioScrobblerService,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> ScrobbleRequest) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: ScrobbleRequest) {
        val params = request.parameters
        val rawIds = params.getAll("id")
        val ids = rawIds?.map { it.toIntOrNull() }
        if (ids != null && ids.any { it == null }) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val result = try {
            if (ids == null) {
                writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Required param (id) is missing")
            } else {
                val parsedTimes = params.getAll("time")?.map { it.toLongOrNull() }.orEmpty()
                val times = if (parsedTimes.any { it == null }) emptyList() else parsedTimes.filterNotNull()
                if (times.isNotEmpty() && times.size != ids.size) {
                    writer.createErrorResponse(ErrorCode.GENERIC, "Wrong number of timestamps: ${times.size}")
                } else {
                    val submission = params["submission"]?.let {
                        it.equals("true", true) || it.equals("on", true) || it.equals("yes", true) || it == "1"
                    } ?: true
                    ids.forEachIndexed { index, id ->
                        val file = mediaFiles.getMediaFile(id!!)
                        if (file == null) {
                            LOG.warn("File to scrobble not found: {}", id)
                        } else {
                            val time = if (times.isEmpty()) null else Instant.ofEpochMilli(times[index])
                            status.addRemotePlay(PlayStatus(UUID.randomUUID(), file, request.player, time ?: Instant.now()))
                            mediaFiles.incrementPlayCount(file)
                            scrobbler.register(file, request.player.username, submission, time)
                        }
                    }
                    writer.createResponse(true)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in scrobble API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        val serialized = writer.serializeForType(params["f"] ?: "xml", params["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ScrobbleController::class.java)
        @JvmField val PATHS = arrayOf("/rest/scrobble", "/rest/scrobble.view")
    }
}
