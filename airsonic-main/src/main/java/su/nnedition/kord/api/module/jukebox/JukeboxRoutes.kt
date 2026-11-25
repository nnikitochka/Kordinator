package su.nnedition.kord.api.module.jukebox

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.service.jukebox.JukeboxQueueTrack
import org.airsonic.player.service.KtorCoreMediaCatalogService
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.jukebox.JukeboxService
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.support.respondJson

fun Route.jukeboxRoutes(
    catalog: KtorCoreMediaCatalogService,
    mediaFiles: MediaFileService,
    jukebox: JukeboxService,
) {
    val MAX_QUEUE_SIZE = 5_000

    val mapper = jacksonObjectMapper()

    fun LocalUserPrincipal.libraryScope(): Set<Long>? = if (role == "ADMIN") null else allowedLibraryIds

    suspend fun loadAccessibleTracks(ids: List<Long>, user: LocalUserPrincipal): List<JukeboxQueueTrack>? {
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

    route("/jukebox") {
        get {
            val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            try {
                call.respondJson(jukebox.state(user.id))
            } catch (_: IllegalStateException) {
                call.respondJson(ApiError("jukebox_in_use", "Server audio output is in use by another user"), HttpStatusCode.Conflict)
            }
        }
        get("/queue/export") {
            val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val state = try {
                jukebox.state(user.id)
            } catch (_: IllegalStateException) {
                call.respondJson(ApiError("jukebox_in_use", "Server audio output is in use by another user"), HttpStatusCode.Conflict)
                return@get
            }
            val playlist = buildString {
                append("#EXTM3U\n")
                state.tracks.forEach { track ->
                    val artist = track.artist.orEmpty().replace('\r', ' ').replace('\n', ' ')
                    val title = track.title.replace('\r', ' ').replace('\n', ' ')
                    append("#EXTINF:").append(track.durationSeconds ?: -1).append(',')
                    .append(artist).append(" - ").append(title).append('\n')
                    append("/api/v1/tracks/").append(track.trackId).append("/stream\n")
                }
            }
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.response.headers.append(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "jukebox.m3u").toString(),
            )
            call.respondText(playlist, ContentType.parse("audio/x-mpegurl").withParameter("charset", "UTF-8"))
        }
        put("/queue") {
            val user = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
            val request = try {
                mapper.readValue(call.receiveText(), ReplaceJukeboxQueueRequest::class.java)
            } catch (_: Exception) {
                return@put call.respondJson(ApiError("invalid_request", "Expected a JSON trackIds array"), HttpStatusCode.BadRequest)
            }
            if (request.trackIds.size > MAX_QUEUE_SIZE || request.trackIds.any { it < 0 }) {
                call.respondJson(
                    ApiError(
                        "invalid_request",
                        "Queue must contain at most 5000 non-negative track IDs"
                    ), HttpStatusCode.BadRequest)
                return@put
            }
            val result = loadAccessibleTracks(request.trackIds, user)
                ?: return@put call.respondJson(
                ApiError(
                    "track_not_found",
                    "A track does not exist or is not accessible"
                ), HttpStatusCode.NotFound)
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
                call.respondJson(
                    ApiError(
                        "jukebox_not_found",
                        "Create a jukebox queue before sending commands"
                    ), HttpStatusCode.NotFound)
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
