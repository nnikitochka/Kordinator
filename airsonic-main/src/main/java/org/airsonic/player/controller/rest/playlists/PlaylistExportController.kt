package org.airsonic.player.controller.rest.playlists

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.util.StringUtil
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import kotlin.coroutines.cancellation.CancellationException

/** Website extension exporting the existing playlist file format. */
class PlaylistExportController(private val playlists: PlaylistService, private val writer: JAXBWriter) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        root.get(PATH) {
            val userRequest = request(call)
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            val result = try {
                withContext(Dispatchers.IO) {
                    val rawId = userRequest.parameters["id"]
                        ?: return@withContext writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Missing id")
                    val id = rawId.toIntOrNull()
                        ?: return@withContext writer.createErrorResponse(ErrorCode.GENERIC, "Invalid id")
                    val playlist = playlists.getPlaylist(id)
                        ?: return@withContext writer.createErrorResponse(ErrorCode.NOT_FOUND, "Playlist not found")
                    if (!playlists.isReadAllowed(playlist, userRequest.user.username)) {
                        return@withContext writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Permission denied for playlist")
                    }
                    // Finish serialization before committing attachment headers, so failures remain REST errors.
                    val bytes = ByteArrayOutputStream().use { output ->
                        playlists.exportPlaylist(id, output)
                        output.toByteArray()
                    }
                    val name = StringUtil.fileSystemSafe(playlist.name).filter { !it.isISOControl() } + ".m3u8"
                    call.response.headers.append(HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment
                            .withParameter(ContentDisposition.Parameters.FileName, name.map { if (it.code in 32..126) it else '_' }.joinToString(""))
                            .withParameter(ContentDisposition.Parameters.FileNameAsterisk, name).toString())
                    call.respondBytes(bytes, ContentType.parse("application/x-download"))
                    null
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                LOG.warn("Playlist export failed", e)
                writer.createErrorResponse(ErrorCode.GENERIC, "Playlist export failed")
            }
            if (result != null) {
                val serialized = writer.serializeForType(userRequest.parameters["f"] ?: "xml", userRequest.parameters["callback"], result)
                call.respondText(serialized.value, ContentType.parse(serialized.key))
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PlaylistExportController::class.java)
        const val PATH = "/rest/exportPlaylist"
    }
}
