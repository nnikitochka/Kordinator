package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.service.KtorCoreMediaCatalogService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.StandaloneJukeboxService
import org.airsonic.player.service.JukeboxQueueTrack

class StandaloneJukeboxRoutes(
    private val catalog: KtorCoreMediaCatalogService,
    private val mediaFiles: MediaFileService,
    private val jukebox: StandaloneJukeboxService,
) {
    private val mapper = jacksonObjectMapper()

    fun routes(root: Route) {
        with(root) {
            route("/jukebox") {
                get {
                    val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    try {
                        call.respondJson(jukebox.state(user.id))
                    } catch (_: IllegalStateException) {
                        call.respondJson(ApiError("jukebox_in_use", "Server audio output is in use by another user"), HttpStatusCode.Conflict)
                    }
                }
                put("/queue") {
                    val user = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val request = try {
                        mapper.readValue(call.receiveText(), ReplaceJukeboxQueueRequest::class.java)
                    } catch (_: Exception) {
                        return@put call.respondJson(ApiError("invalid_request", "Expected a JSON trackIds array"), HttpStatusCode.BadRequest)
                    }
                    if (request.trackIds.size > MAX_QUEUE_SIZE || request.trackIds.any { it < 0 }) {
                        call.respondJson(ApiError("invalid_request", "Queue must contain at most 5000 non-negative track IDs"), HttpStatusCode.BadRequest)
                        return@put
                    }
                    val result = loadAccessibleTracks(request.trackIds, user)
                        ?: return@put call.respondJson(ApiError("track_not_found", "A track does not exist or is not accessible"), HttpStatusCode.NotFound)
                    try {
                        call.respondJson(jukebox.replaceQueue(user.id, user.username, result))
                    } catch (failure: IllegalArgumentException) {
                        call.respondJson(ApiError("invalid_request", failure.message ?: "Invalid jukebox queue"), HttpStatusCode.BadRequest)
                    } catch (_: IllegalStateException) {
                        call.respondJson(ApiError("jukebox_in_use", "Server audio output is in use by another user"), HttpStatusCode.Conflict)
                    }
                }
                post("/commands") {
                    val user = call.principal<LocalUserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val request = try {
                        mapper.readValue(call.receiveText(), JukeboxCommandRequest::class.java)
                    } catch (_: Exception) {
                        return@post call.respondJson(ApiError("invalid_request", "Expected a JSON jukebox command"), HttpStatusCode.BadRequest)
                    }
                    try {
                        call.respondJson(jukebox.command(user.id, request.action, request.index, request.offsetSeconds, request.gain))
                    } catch (_: NoSuchElementException) {
                        call.respondJson(ApiError("jukebox_not_found", "Create a jukebox queue before sending commands"), HttpStatusCode.NotFound)
                    } catch (failure: IllegalArgumentException) {
                        call.respondJson(ApiError("invalid_request", failure.message ?: "Invalid jukebox command"), HttpStatusCode.BadRequest)
                    } catch (_: IllegalStateException) {
                        call.respondJson(ApiError("jukebox_in_use", "Server audio output is in use by another user"), HttpStatusCode.Conflict)
                    }
                }
                delete {
                    val user = call.principal<LocalUserPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    if (!jukebox.release(user.id)) return@delete call.respond(HttpStatusCode.NotFound)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }

    private suspend fun loadAccessibleTracks(ids: List<Long>, user: LocalUserPrincipal): List<JukeboxQueueTrack>? {
        if (ids.isEmpty()) return emptyList()
        val visible = withContext(Dispatchers.IO) { catalog.tracksByCoreIds(ids.distinct(), user.libraryScope()) }
        if (visible.size != ids.distinct().size) return null
        val byId = visible.associateBy { it.id }
        return ids.map { id ->
            val track = byId[id] ?: return null
            val file = withContext(Dispatchers.IO) { mediaFiles.getMediaFile(track.relativePath, track.libraryId.toInt()) }
                ?: return null
            if (file.isVideo) return null
            JukeboxQueueTrack(id, file)
        }
    }

    private fun LocalUserPrincipal.libraryScope(): Set<Long>? = if (role == "ADMIN") null else allowedLibraryIds

    private companion object {
        const val MAX_QUEUE_SIZE = 5_000
    }
}

data class ReplaceJukeboxQueueRequest(val trackIds: List<Long> = emptyList())
data class JukeboxCommandRequest(
    val action: String,
    val index: Int? = null,
    val offsetSeconds: Int? = null,
    val gain: Float? = null,
)
