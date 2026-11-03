package org.airsonic.player.controller.rest.music.directory

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.ajax.media.MediaFileDirectoryEntry
import org.airsonic.player.controller.rest.toWebsiteEntry
import org.airsonic.player.service.MediaDirectoryService
import org.subsonic.restapi.WebsiteDirectory
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.RatingService
import org.airsonic.player.service.SecurityService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Directory
import kotlin.coroutines.cancellation.CancellationException

class MusicDirectoryController(
    private val mediaFiles: MediaFileService,
    private val mediaFileDao: MediaFileDao,
    private val security: SecurityService,
    private val ratings: RatingService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
    private val websiteDirectories: MediaDirectoryService,
) {
    fun routes(root: Route, request: (ApplicationCall) -> MusicDirectoryRequest) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: MusicDirectoryRequest) {
        val params = request.parameters
        val rawId = params["id"]
        val id = rawId?.toIntOrNull()
        val website = params["includeWebsiteDetails"] == "true"
        if (!website && rawId != null && id == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val result = try {
            if (website) {
                websiteDirectory(request)
            } else if (id == null) {
                writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Required param (id) is missing")
            } else {
                val dir = mediaFiles.getMediaFile(id)
                when {
                    dir == null -> writer.createErrorResponse(ErrorCode.NOT_FOUND, "Directory not found")
                    !security.isFolderAccessAllowed(dir, request.username) -> writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Access denied")
                    else -> {
                        val parent = mediaFiles.getParentOf(dir)
                        val directory = Directory().apply {
                            this.id = id.toString()
                            try {
                                if (!mediaFiles.isRoot(parent)) this.parent = parent.id.toString()
                            } catch (_: SecurityException) {
                                // Match the legacy handling of inaccessible parents.
                            }
                            name = dir.name
                            starred = writer.convertDate(mediaFileDao.getMediaFileStarredDate(id, request.username))
                            playCount = dir.playCount.toLong()
                            accentColor = mapper.accentColor(dir, parent)
                            if (dir.isAlbum) {
                                averageRating = ratings.getAverageRating(dir)
                                userRating = ratings.getRatingForUser(request.username, dir)
                            }
                            mediaFiles.getChildrenOf(dir, true, true, true).forEach {
                                child.add(mapper.child(request.player, it, request.username))
                            }
                        }
                        writer.createResponse(true).apply { this.directory = directory }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in music directory API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        val serialized = writer.serializeForType(params["f"] ?: "xml", params["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun websiteDirectory(request: MusicDirectoryRequest): org.subsonic.restapi.Response {
        val params = request.parameters
        val ids = params.getAll("id")?.map { requireNotNull(it.toIntOrNull()) } ?: emptyList()
        val paths = linkedMapOf<Int, MutableList<String>>()
        params.getAll("folderPath")?.forEach { raw ->
            val colon = raw.indexOf(':')
            require(colon > 0) { "Invalid folder path" }
            val folder = requireNotNull(raw.substring(0, colon).toIntOrNull())
            paths.getOrPut(folder) { mutableListOf() }.add(raw.substring(colon + 1))
        }
        if (ids.isEmpty() && paths.isEmpty()) return writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "ID or folder path required")
        val value = websiteDirectories.get(request.username, ids, paths)
        val result = WebsiteDirectory()
        when (value) {
            is MediaFileDirectoryEntry -> {
                result.detail = value.toWebsiteEntry()
                result.contentType = value.contentType
                result.comment = value.comment
                result.userRating = value.userRating
                result.averageRating = value.averageRating
                result.musicBrainzReleaseId = value.musicBrainzReleaseId
                value.files?.mapTo(result.file) { it.toWebsiteEntry() }
                value.subDirs?.mapTo(result.subDir) { it.toWebsiteEntry() }
                value.ancestors?.mapTo(result.ancestor) { it.toWebsiteEntry() }
                value.siblingAlbums?.mapTo(result.siblingAlbum) { it.toWebsiteEntry() }
            }
            is Map<*, *> -> result.contentType = value["contentType"] as? String
            else -> error("Unexpected directory response")
        }
        return writer.createResponse(true).apply { websiteDirectory = result }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MusicDirectoryController::class.java)
        @JvmField val PATHS = arrayOf("/rest/getMusicDirectory", "/rest/getMusicDirectory.view")
    }
}
