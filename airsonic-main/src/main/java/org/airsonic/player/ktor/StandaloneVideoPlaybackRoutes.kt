package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.StandaloneVideoPlaybackService

class StandaloneVideoPlaybackRoutes(
    private val mediaFiles: MediaFileService,
    private val playback: StandaloneVideoPlaybackService,
) {
    private val mapper = jacksonObjectMapper()

    fun routes(root: Route) {
        with(root) {
            route("/videos/{videoId}/playback-sessions") {
                post {
                    val user = call.principal<LocalUserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["videoId"]?.toIntOrNull()?.takeIf { it >= 0 }
                        ?: return@post call.respondJson(ApiError("invalid_request", "Video ID must be a non-negative integer"), HttpStatusCode.BadRequest)
                    val file = withContext(Dispatchers.IO) { mediaFiles.getMediaFile(id) }
                    if (!file.isAccessibleVideo(user)) return@post call.respond(HttpStatusCode.NotFound)
                    val request = try {
                        mapper.readValue(call.receiveText(), CreateVideoPlaybackRequest::class.java)
                    } catch (_: Exception) {
                        return@post call.respondJson(ApiError("invalid_request", "Expected video playback settings as JSON"), HttpStatusCode.BadRequest)
                    }
                    try {
                        val session = playback.create(user.id, file, request.bitrateKbps, request.width, request.height, request.audioTrack)
                        call.respondJson(session, HttpStatusCode.Created)
                    } catch (failure: IllegalArgumentException) {
                        call.respondJson(ApiError("invalid_request", failure.message ?: "Invalid playback settings"), HttpStatusCode.BadRequest)
                    }
                }
                get("/{sessionId}/playlist.m3u8") {
                    val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["videoId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.NotFound)
                    val sessionId = call.parameters["sessionId"] ?: return@get call.respond(HttpStatusCode.NotFound)
                    val video = withContext(Dispatchers.IO) { mediaFiles.getMediaFile(id) }
                    if (!video.isAccessibleVideo(user)) return@get call.respond(HttpStatusCode.NotFound)
                    val playlist = playback.playlist(user.id, sessionId, user.libraryScope()) ?: return@get call.respond(HttpStatusCode.NotFound)
                    if (playlist.first.videoId != id) return@get call.respond(HttpStatusCode.NotFound)
                    call.response.headers.append("Cache-Control", "no-store")
                    call.respondText(playlist.second, ContentType.parse("application/vnd.apple.mpegurl"))
                }
                get("/{sessionId}/segments/{index}.ts") {
                    val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["videoId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.NotFound)
                    val sessionId = call.parameters["sessionId"] ?: return@get call.respond(HttpStatusCode.NotFound)
                    val index = call.parameters["index"]?.toIntOrNull()
                        ?: return@get call.respondJson(ApiError("invalid_request", "Segment index must be a non-negative integer"), HttpStatusCode.BadRequest)
                    val video = withContext(Dispatchers.IO) { mediaFiles.getMediaFile(id) }
                    if (!video.isAccessibleVideo(user)) return@get call.respond(HttpStatusCode.NotFound)
                    val session = playback.playlist(user.id, sessionId, user.libraryScope())
                    if (session?.first?.videoId != id) return@get call.respond(HttpStatusCode.NotFound)
                    val segment = try {
                        playback.segment(user.id, sessionId, index, user.libraryScope())
                    } catch (failure: IllegalArgumentException) {
                        return@get call.respondJson(ApiError("invalid_request", failure.message ?: "Invalid segment"), HttpStatusCode.BadRequest)
                    } catch (_: java.util.concurrent.TimeoutException) {
                        return@get call.respond(HttpStatusCode.ServiceUnavailable)
                    } ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.response.headers.append("Cache-Control", "private, no-store")
                    call.respondOutputStream(ContentType.parse("video/mp2t"), contentLength = segment.contentLength) {
                        segment.stream.use { it.copyTo(this) }
                    }
                }
                delete("/{sessionId}") {
                    val user = call.principal<LocalUserPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    val sessionId = call.parameters["sessionId"] ?: return@delete call.respond(HttpStatusCode.NotFound)
                    if (!playback.close(user.id, sessionId)) return@delete call.respond(HttpStatusCode.NotFound)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }

    private fun org.airsonic.player.domain.MediaFile?.isAccessibleVideo(user: LocalUserPrincipal): Boolean {
        if (this == null || !isVideo || !isPresent) return false
        val allowed = user.libraryScope()
        return allowed == null || folderId.toLong() in allowed
    }

    private fun LocalUserPrincipal.libraryScope(): Set<Long>? = if (role == "ADMIN") null else allowedLibraryIds
}

data class CreateVideoPlaybackRequest(
    val bitrateKbps: Int = 2_000,
    val width: Int = 1280,
    val height: Int = 720,
    val audioTrack: Int? = null,
)
