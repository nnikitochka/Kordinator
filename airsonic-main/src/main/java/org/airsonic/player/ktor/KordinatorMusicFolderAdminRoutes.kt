package org.airsonic.player.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.MediaScannerService
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

data class CreateMusicFolderRequest(val name: String?, val path: String?)
data class MusicFolderResponse(
    val id: Int,
    val name: String,
    val path: String,
    val type: String,
    val enabled: Boolean,
    val changedAt: String?,
)
data class MusicScanStatusResponse(val scanning: Boolean, val scannedFiles: Int)

/** Exposes existing music-folder records and overlap validation to the Ktor API. */
class KordinatorMusicFolderAdminRoutes(
    private val mediaFolderService: MediaFolderService,
    private val mediaScannerService: MediaScannerService?,
    private val objectMapper: ObjectMapper,
) {
    fun routes(root: Route) {
        root.get("/admin/music-folders") {
            if (!requireAdministrator(call.principal<LocalUserPrincipal>())) {
                call.respondError("forbidden", "Administrator role required", HttpStatusCode.Forbidden)
                return@get
            }
            val folders = withContext(Dispatchers.IO) {
                mediaFolderService.getAllMusicFolders(true, true)
                    .sortedWith(compareBy<MusicFolder> { it.name }.thenBy { it.id })
                    .map { it.toResponse() }
            }
            call.respondText(objectMapper.writeValueAsString(folders), ContentType.Application.Json)
        }

        root.post("/admin/music-folders") {
            if (!requireAdministrator(call.principal<LocalUserPrincipal>())) {
                call.respondError("forbidden", "Administrator role required", HttpStatusCode.Forbidden)
                return@post
            }
            val request = try {
                objectMapper.readValue(call.receiveText(), CreateMusicFolderRequest::class.java)
            } catch (_: Exception) {
                call.respondError("invalid_request", "Expected a JSON object with name and path", HttpStatusCode.BadRequest)
                return@post
            }
            val name = request.name?.trim().orEmpty()
            if (name.isEmpty() || name.length > 120 || request.path.isNullOrBlank()) {
                call.respondError("invalid_request", "Name and path are required", HttpStatusCode.BadRequest)
                return@post
            }
            val path = try {
                Path.of(request.path).toRealPath()
            } catch (_: IOException) {
                call.respondError("invalid_folder", "Music folder path is not accessible", HttpStatusCode.BadRequest)
                return@post
            } catch (_: RuntimeException) {
                call.respondError("invalid_folder", "Music folder path is invalid", HttpStatusCode.BadRequest)
                return@post
            }
            if (!Files.isDirectory(path) || !Files.isReadable(path)) {
                call.respondError("invalid_folder", "Music folder path must be a readable directory", HttpStatusCode.BadRequest)
                return@post
            }

            val folder = MusicFolder(null, path, name, MusicFolder.Type.MEDIA, true, Instant.now())
            try {
                withContext(Dispatchers.IO) { mediaFolderService.createMusicFolder(folder) }
            } catch (failure: IllegalArgumentException) {
                val conflict = failure.message?.contains("overlap", ignoreCase = true) == true
                call.respondError(
                    if (conflict) "folder_overlap" else "invalid_folder",
                    if (conflict) "Music folder overlaps an existing folder" else "Music folder is invalid",
                    if (conflict) HttpStatusCode.Conflict else HttpStatusCode.BadRequest,
                )
                return@post
            }
            val created = folder.toResponse()
            call.respondText(objectMapper.writeValueAsString(created), ContentType.Application.Json, HttpStatusCode.Created)
        }

        root.get("/admin/music-scan") {
            if (!requireAdministrator(call.principal<LocalUserPrincipal>())) {
                call.respondError("forbidden", "Administrator role required", HttpStatusCode.Forbidden)
                return@get
            }
            val scanner = mediaScannerService
            if (scanner == null) {
                call.respondError("unavailable", "Media scanner is not configured", HttpStatusCode.ServiceUnavailable)
                return@get
            }
            val status = withContext(Dispatchers.IO) {
                MusicScanStatusResponse(scanner.isScanning, scanner.scanCount)
            }
            call.respondText(objectMapper.writeValueAsString(status), ContentType.Application.Json)
        }

        root.post("/admin/music-scan") {
            if (!requireAdministrator(call.principal<LocalUserPrincipal>())) {
                call.respondError("forbidden", "Administrator role required", HttpStatusCode.Forbidden)
                return@post
            }
            val scanner = mediaScannerService
            if (scanner == null) {
                call.respondError("unavailable", "Media scanner is not configured", HttpStatusCode.ServiceUnavailable)
                return@post
            }
            val scanning = withContext(Dispatchers.IO) { scanner.isScanning }
            if (scanning) {
                call.respondError("scan_in_progress", "A media scan is already running", HttpStatusCode.Conflict)
                return@post
            }
            withContext(Dispatchers.IO) { scanner.scanLibrary() }
            val status = withContext(Dispatchers.IO) {
                MusicScanStatusResponse(scanner.isScanning, scanner.scanCount)
            }
            call.respondText(objectMapper.writeValueAsString(status), ContentType.Application.Json, HttpStatusCode.Accepted)
        }
    }

    private fun MusicFolder.toResponse() = MusicFolderResponse(
        id = requireNotNull(id),
        name = name,
        path = path.toString(),
        type = type.name,
        enabled = isEnabled,
        changedAt = changed?.toString(),
    )

    private suspend fun io.ktor.server.application.ApplicationCall.respondError(
        code: String,
        message: String,
        status: HttpStatusCode,
    ) {
        respondText(objectMapper.writeValueAsString(ApiError(code, message)), ContentType.Application.Json, status)
    }

    private fun requireAdministrator(principal: LocalUserPrincipal?) = principal?.role == "ADMIN"
}
