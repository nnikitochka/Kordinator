package su.nnedition.kord.api.module.admin.library

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.service.library.scan.LibraryScan
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.authDispatcher
import su.nnedition.kord.api.scanDispatcher
import su.nnedition.kord.api.support.jsonMapper
import su.nnedition.kord.api.support.respondJson
import su.nnedition.kord.api.support.toLibraryResponse

fun Route.adminLibraryRoutes(
    context: ApiContext,
) {
    val authService = requireNotNull(context.authService)

    get("/admin/libraries") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@get
        }
        val folders = context.mediaFolderService?.let { service ->
            withContext(authDispatcher) { service.getAllMusicFolders(true, true) }
            .sortedWith(compareBy<MusicFolder> { it.name }.thenBy { it.id })
            .map { it.toLibraryResponse() }
        }.orEmpty()
        call.respondJson(folders)
    }
    put("/admin/libraries/{id}") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@put
        }
        val folderService = context.mediaFolderService
        if (folderService == null) {
            call.respondJson(ApiError("unavailable", "Music folder storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@put
        }
        val libraryId = call.parameters["id"]?.toIntOrNull()
        if (libraryId == null || libraryId <= 0) {
            call.respondJson(ApiError("invalid_request", "Library ID must be a positive number"), HttpStatusCode.BadRequest)
            return@put
        }
        val request = try {
            jsonMapper.readValue(call.receiveText(), UpdateLibraryRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected a name"), HttpStatusCode.BadRequest)
            return@put
        }
        val normalizedName = request.name.trim()
        if (normalizedName.isEmpty() || normalizedName.length > 120) {
            call.respondJson(ApiError("invalid_library", "Library name must contain 1 to 120 characters"), HttpStatusCode.BadRequest)
            return@put
        }
        val folders = withContext(authDispatcher) { folderService.getAllMusicFolders(true, true, true) }
        val folder = folders.firstOrNull { it.id == libraryId }
        if (folder == null) {
            call.respondJson(ApiError("library_not_found", "Library does not exist"), HttpStatusCode.NotFound)
            return@put
        }
        if (folders.any { it.id != libraryId && it.name.equals(normalizedName, ignoreCase = true) }) {
            call.respondJson(ApiError("library_exists", "A library with that name already exists"), HttpStatusCode.Conflict)
            return@put
        }
        val updatedPath = request.path?.let { suppliedPath ->
            if (suppliedPath.isBlank()) {
                call.respondJson(ApiError("invalid_library", "Library path must not be blank"), HttpStatusCode.BadRequest)
                return@put
            }
            try {
                Path.of(suppliedPath).toRealPath().also { path ->
                    if (!Files.isDirectory(path) || !Files.isReadable(path)) {
                        throw IllegalArgumentException("Library path must be a readable directory")
                    }
                }
            } catch (_: Exception) {
                call.respondJson(ApiError("invalid_library", "Library path is not an accessible directory"), HttpStatusCode.BadRequest)
                return@put
            }
        }
        val pathChanged = updatedPath != null && updatedPath != folder.path
        if (pathChanged && (context.libraryScanService == null || context.scanScope == null)) {
            call.respondJson(ApiError("unavailable", "Library scanning is required to change a library path"), HttpStatusCode.ServiceUnavailable)
            return@put
        }
        if (pathChanged && context.libraryScanService?.latestScan(libraryId.toLong())?.state in setOf("QUEUED", "RUNNING")) {
            call.respondJson(
                ApiError(
                    "scan_in_progress",
                    "Cannot change the library path while a scan is running"
                ), HttpStatusCode.Conflict)
            return@put
        }
        val oldPath = folder.path
        folder.name = normalizedName
        if (updatedPath != null) folder.path = updatedPath
        try {
            withContext(authDispatcher) { folderService.updateMusicFolder(folder) }
        } catch (failure: IllegalArgumentException) {
            val overlap = failure.message?.contains("overlap", ignoreCase = true) == true
            call.respondJson(
                ApiError(
                    if (overlap) "library_exists" else "invalid_library",
                    failure.message ?: "Library update is invalid"
                ),
                if (overlap) HttpStatusCode.Conflict else HttpStatusCode.BadRequest,
            )
            return@put
        }
        var scan: LibraryScan? = null
        if (pathChanged) {
            try {
                scan = withContext(scanDispatcher) { requireNotNull(context.libraryScanService).enqueue(libraryId.toLong()) }
            } catch (failure: IllegalArgumentException) {
                folder.path = oldPath
                withContext(authDispatcher) { folderService.updateMusicFolder(folder) }
                call.respondJson(
                    ApiError(
                        "scan_unavailable",
                        failure.message ?: "Unable to start library scan"
                    ), HttpStatusCode.Conflict)
                return@put
            }
            requireNotNull(context.scanScope).launch {
                withContext(scanDispatcher) { requireNotNull(context.libraryScanService).run(requireNotNull(scan).id) }
            }
        }
        call.respondJson(folder.toLibraryResponse(scan?.id), if (pathChanged) HttpStatusCode.Accepted else HttpStatusCode.OK)
    }
    delete("/admin/libraries/{id}") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@delete
        }
        val auth = authService
        if (auth == null) {
            call.respondJson(ApiError("unavailable", "Account storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@delete
        }
        val folderService = context.mediaFolderService
        if (folderService == null) {
            call.respondJson(ApiError("unavailable", "Music folder storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@delete
        }
        val libraryId = call.parameters["id"]?.toIntOrNull()
        if (libraryId == null || libraryId <= 0) {
            call.respondJson(ApiError("invalid_request", "Library ID must be a positive number"), HttpStatusCode.BadRequest)
            return@delete
        }
        val exists = withContext(authDispatcher) {
            folderService.getAllMusicFolders(true, true, true).any { it.id == libraryId }
        }
        if (!exists) {
            call.respondJson(ApiError("library_not_found", "Library does not exist"), HttpStatusCode.NotFound)
            return@delete
        }
        withContext(authDispatcher) { folderService.deleteMusicFolder(libraryId) }
        withContext(authDispatcher) { auth.removeLibraryAccess(libraryId.toLong()) }
        call.respond(HttpStatusCode.NoContent)
    }
    post("/admin/libraries") {
        val actor = call.principal<LocalUserPrincipal>()
            ?: return@post call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@post
        }
        val folderService = context.mediaFolderService
        if (folderService == null) {
            call.respondJson(ApiError("unavailable", "Music folder storage is not configured"), HttpStatusCode.ServiceUnavailable)
            return@post
        }
        val request = try {
            jsonMapper.readValue(call.receiveText(), CreateLibraryRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected name and path"), HttpStatusCode.BadRequest)
            return@post
        }
        val normalizedName = request.name.trim()
        if (normalizedName.isEmpty() || normalizedName.length > 120 || request.path.isBlank()) {
            call.respondJson(ApiError("invalid_library", "Library name and path are required"), HttpStatusCode.BadRequest)
            return@post
        }
        val path = try {
            Path.of(request.path).toRealPath()
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_library", "Library path is not accessible"), HttpStatusCode.BadRequest)
            return@post
        }
        if (!Files.isDirectory(path) || !Files.isReadable(path)) {
            call.respondJson(ApiError("invalid_library", "Library path must be a readable directory"), HttpStatusCode.BadRequest)
            return@post
        }
        val existingFolders = withContext(authDispatcher) { folderService.getAllMusicFolders(true, true, true) }
        if (existingFolders.any { it.name.equals(normalizedName, ignoreCase = true) }) {
            call.respondJson(ApiError("library_exists", "A library with that name already exists"), HttpStatusCode.Conflict)
            return@post
        }
        val folder = MusicFolder(null, path, normalizedName, MusicFolder.Type.MEDIA, true, Instant.now())
        try {
            withContext(authDispatcher) {
                folderService.createMusicFolder(folder)
            }
            call.respondJson(folder.toLibraryResponse(), HttpStatusCode.Created)
        } catch (failure: IllegalArgumentException) {
            val overlap = failure.message?.contains("overlap", ignoreCase = true) == true
            call.respondJson(
                ApiError(
                    if (overlap) "library_exists" else "invalid_library",
                    if (overlap) "Library path overlaps an existing music folder" else "Library path is invalid"
                ),
                if (overlap) HttpStatusCode.Conflict else HttpStatusCode.BadRequest,
            )
        }
    }
}
