package org.airsonic.player.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SecurityService

data class KordinatorLibraryFolder(val id: Int, val name: String)

data class KordinatorLibraryEntry(
    val id: Int,
    val type: String,
    val name: String,
    val artist: String? = null,
    val album: String? = null,
    val durationSeconds: Int? = null,
    val format: String? = null,
)

data class KordinatorDirectoryContents(
    val id: Int,
    val name: String,
    val folderId: Int,
    val parentId: Int?,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    val entries: List<KordinatorLibraryEntry>,
)

class KordinatorLibraryRoutes(
    private val mediaFileService: MediaFileService,
    private val mediaFolderService: MediaFolderService,
    private val securityService: SecurityService,
) {
    fun routes(
        root: Route,
        currentUsername: (ApplicationCall) -> String?,
        objectMapper: ObjectMapper,
    ) {
        root.get("/api/v1/library/folders") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }
            val folders = mediaFolderService.getMusicFoldersForUser(username)
                .map { KordinatorLibraryFolder(requireNotNull(it.id), it.name) }
            call.respondText(objectMapper.writeValueAsString(folders), ContentType.Application.Json)
        }

        root.get("/api/v1/library/folders/{id}") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }
            val id = positiveId(call.parameters["id"])
            if (id == null) {
                invalidRequest(call, objectMapper, "Music folder ID must be a positive number")
                return@get
            }
            val folder = mediaFolderService.getMusicFoldersForUser(username).firstOrNull { it.id == id }
            if (folder == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val rootDirectory = mediaFileService.getMediaFile("", id)
            if (rootDirectory == null || !rootDirectory.isDirectory) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            respondDirectory(call, username, rootDirectory, folder.name, true, objectMapper)
        }

        root.get("/api/v1/library/directories/{id}") {
            val username = currentUsername(call)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }
            val id = positiveId(call.parameters["id"])
            if (id == null) {
                invalidRequest(call, objectMapper, "Directory ID must be a positive number")
                return@get
            }
            val directory = mediaFileService.getMediaFile(id)
            if (directory == null || !directory.isDirectory ||
                !securityService.isFolderAccessAllowed(directory, username)
            ) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val folderId = directory.folderId
            if (folderId == null || mediaFolderService.getMusicFoldersForUser(username).none { it.id == folderId }) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            respondDirectory(call, username, directory, directory.name, false, objectMapper)
        }
    }

    private suspend fun respondDirectory(
        call: ApplicationCall,
        username: String,
        directory: MediaFile,
        name: String,
        root: Boolean,
        objectMapper: ObjectMapper,
    ) {
        val offsetValue = call.request.queryParameters["offset"]
        val limitValue = call.request.queryParameters["limit"]
        val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
        val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 100 else null
        if (offset == null || limit == null || offset < 0 || limit !in 1..200) {
            invalidRequest(call, objectMapper, "Offset must be non-negative and limit must be between 1 and 200")
            return
        }

        val folderId = requireNotNull(directory.folderId)
        val children = mediaFileService.getChildrenOf(directory, true, true, true)
            .asSequence()
            .filter { securityService.isFolderAccessAllowed(it, username) }
            .filter { it.isDirectory || it.isAudio }
            .toList()
        val page = children.drop(offset).take(limit + 1)
        val hasMore = page.size > limit
        val entries = page.take(limit).map { it.toKordinatorEntry() }
        val parentId = if (root) null else mediaFileService.getParentOf(directory)?.id
        val response = KordinatorDirectoryContents(
            id = requireNotNull(directory.id),
            name = name,
            folderId = folderId,
            parentId = parentId,
            offset = offset,
            limit = limit,
            hasMore = hasMore,
            entries = entries,
        )
        call.respondText(objectMapper.writeValueAsString(response), ContentType.Application.Json)
    }

    private fun MediaFile.toKordinatorEntry() = KordinatorLibraryEntry(
        id = requireNotNull(id),
        type = if (isDirectory) "directory" else "track",
        name = name,
        artist = if (isDirectory) null else artist,
        album = if (isDirectory) null else albumName,
        durationSeconds = if (isDirectory) null else duration?.toInt() ?: 0,
        format = if (isDirectory) null else format,
    )

    private fun positiveId(value: String?): Int? = value?.toIntOrNull()?.takeIf { it > 0 }

    private suspend fun invalidRequest(call: ApplicationCall, objectMapper: ObjectMapper, message: String) {
        call.respondText(
            objectMapper.writeValueAsString(ApiError("invalid_request", message)),
            ContentType.Application.Json,
            HttpStatusCode.BadRequest,
        )
    }
}
