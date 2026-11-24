package org.airsonic.player.ktor

import io.ktor.server.routing.Route
import io.ktor.server.auth.principal
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LocalUserPrincipal
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.scanDispatcher

internal class KordinatorLibraryScanRoutes(private val api: ApiContext) {
    fun routes(route: Route) = route.apply {
        with(api) {
            post("/admin/libraries/{id}/files") {
                val actor = call.principal<LocalUserPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                if (actor.role != "ADMIN") {
                    call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                    return@post
                }
                val scanService = libraryScanService
                if (scanService == null) {
                    call.respondJson(ApiError("unavailable", "Library storage is not configured"), HttpStatusCode.ServiceUnavailable)
                    return@post
                }
                val libraryId = call.parameters["id"]?.toLongOrNull()
                if (libraryId == null || libraryId !in 0..Int.MAX_VALUE.toLong()) {
                    call.respondJson(ApiError("invalid_request", "Library ID must be a non-negative number"), HttpStatusCode.BadRequest)
                    return@post
                }
                val requestedPath = call.request.queryParameters["path"]?.trim()
                if (requestedPath != null && requestedPath.isBlank()) {
                    call.respondJson(ApiError("invalid_request", "Upload path must not be blank"), HttpStatusCode.BadRequest)
                    return@post
                }
                try {
                    var uploaded: org.airsonic.player.service.StandaloneUploadedFile? = null
                    call.receiveMultipart(formFieldLimit = 1024L).forEachPart { part ->
                        try {
                            if (part is PartData.FileItem) {
                                require(uploaded == null && part.name == "file") { "Exactly one file field is required" }
                                val filename = part.originalFileName?.replace('\\', '/')?.substringAfterLast('/')
                                require(!filename.isNullOrBlank() && filename != "." && filename != ".." && filename.none(Char::isISOControl)) {
                                    "Invalid upload filename"
                                }
                                val destination = requestedPath ?: filename
                                uploaded = withContext(Dispatchers.IO) {
                                    scanService.uploadFile(libraryId, destination, part.provider().toInputStream())
                                }
                            }
                        } finally {
                            part.dispose()
                        }
                    }
                    val result = uploaded
                    if (result == null) {
                        call.respondJson(ApiError("invalid_request", "A single file field is required"), HttpStatusCode.BadRequest)
                    } else {
                        call.respondJson(
                            UploadedLibraryFileResponse(libraryId, result.relativePath, result.sizeBytes),
                            HttpStatusCode.Created,
                        )
                    }
                } catch (failure: java.nio.file.FileAlreadyExistsException) {
                    call.respondJson(ApiError("file_exists", "The destination file already exists"), HttpStatusCode.Conflict)
                } catch (failure: java.nio.file.AccessDeniedException) {
                    call.respondJson(ApiError("upload_denied", failure.message ?: "Upload path is not allowed"), HttpStatusCode.Forbidden)
                } catch (failure: IllegalArgumentException) {
                    val tooLarge = failure.message?.contains("2 GiB") == true
                    call.respondJson(
                        ApiError(
                            if (tooLarge) "upload_too_large" else "invalid_upload",
                            failure.message ?: "Upload is invalid"
                        ),
                        if (tooLarge) HttpStatusCode.PayloadTooLarge else HttpStatusCode.BadRequest,
                    )
                }
            }
            post("/admin/libraries/{id}/scan") {
                val actor = call.principal<LocalUserPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                if (actor.role != "ADMIN") {
                    call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                    return@post
                }
                if (libraryScanService == null || scanScope == null) {
                    call.respondJson(ApiError("unavailable", "Library scanning is not configured"), HttpStatusCode.ServiceUnavailable)
                    return@post
                }
                val libraryId = call.parameters["id"]?.toLongOrNull()
                if (libraryId == null) {
                    call.respondJson(ApiError("invalid_request", "Library ID must be a number"), HttpStatusCode.BadRequest)
                    return@post
                }
                val scan = try {
                    withContext(scanDispatcher) { libraryScanService.enqueue(libraryId) }
                } catch (failure: IllegalArgumentException) {
                    val status = if (failure.message?.contains("does not exist") == true) {
                        HttpStatusCode.NotFound
                    } else {
                        HttpStatusCode.Conflict
                    }
                    call.respondJson(ApiError("scan_unavailable", failure.message ?: "Unable to start scan"), status)
                    return@post
                }
                scanScope.launch { withContext(scanDispatcher) { libraryScanService.run(scan.id) } }
                call.respondJson(scan.toResponse(), HttpStatusCode.Accepted)
            }
            get("/admin/libraries/{id}/scan") {
                val actor = call.principal<LocalUserPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                if (actor.role != "ADMIN") {
                    call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                    return@get
                }
                val libraryId = call.parameters["id"]?.toLongOrNull()
                if (libraryId == null) {
                    call.respondJson(ApiError("invalid_request", "Library ID must be a number"), HttpStatusCode.BadRequest)
                    return@get
                }
                val scan = libraryScanService?.let { withContext(scanDispatcher) { it.latestScan(libraryId) } }
                if (scan == null) call.respond(HttpStatusCode.NotFound) else call.respondJson(scan.toResponse())
            }
        }
    }
}
