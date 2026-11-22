package org.airsonic.player.controller.rest.playlists

import io.ktor.http.ContentType
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.service.PlaylistService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.PlaylistImport
import java.io.ByteArrayInputStream
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException

/** Website extension: exactly one multipart file; authentication and format parameters are in the query. */
class PlaylistImportController(private val playlists: PlaylistService, private val writer: JAXBWriter) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        root.post(PATH) {
            val userRequest = request(call)
            call.response.headers.append("Cache-Control", "no-store")
            val result = try {
                val (name, bytes) = receiveFile(call)
                val imported = withContext(Dispatchers.IO) {
                    ByteArrayInputStream(bytes).use { input ->
                        playlists.importPlaylist(userRequest.user.username, name.substringBeforeLast('.', name), name, null, input, null)
                    }
                }
                writer.createResponse(true).apply {
                    playlistImport = PlaylistImport().apply {
                        id = imported.id.toString()
                        this.name = imported.name!!
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                LOG.warn("Playlist import failed", e)
                writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Playlist import failed")
            }
            val serialized = writer.serializeForType(userRequest.parameters["f"] ?: "xml", userRequest.parameters["callback"], result)
            call.respondText(serialized.value, ContentType.parse(serialized.key))
        }
    }

    private suspend fun receiveFile(call: ApplicationCall): Pair<String, ByteArray> {
        require(call.request.headers["Content-Type"]?.startsWith("multipart/form-data", true) == true) { "Multipart upload required" }
        val length = call.request.headers["Content-Length"]?.toLongOrNull()
        require(length == null || length <= MAX_BYTES + 65536L) { "Playlist exceeds 5 MiB" }
        var data: ByteArray? = null
        var name: String? = null
        var error: String? = null
        call.receiveMultipart(formFieldLimit = MAX_BYTES + 65536L).forEachPart { part ->
            try {
                if (part is PartData.FileItem) {
                    val duplicate = data != null
                    val bytes = withContext(Dispatchers.IO) {
                        part.provider().toInputStream().use { input ->
                            val received = input.readNBytes(MAX_BYTES + 1)
                            if (received.size > MAX_BYTES) input.transferTo(OutputStream.nullOutputStream())
                            received
                        }
                    }
                    when {
                        duplicate || part.name != "file" -> error = "Exactly one file field is required"
                        bytes.size > MAX_BYTES -> error = "Playlist exceeds 5 MiB"
                    }
                    if (!duplicate) {
                        name = part.originalFileName?.replace('\\', '/')?.substringAfterLast('/')
                        data = bytes
                    }
                }
            } finally { part.dispose() }
        }
        require(error == null) { error!! }
        require(!name.isNullOrBlank() && data?.isNotEmpty() == true) { "Missing playlist file" }
        return name!! to data!!
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PlaylistImportController::class.java)
        const val PATH = "/rest/importPlaylist"
        private const val MAX_BYTES = 5 * 1024 * 1024
    }
}
