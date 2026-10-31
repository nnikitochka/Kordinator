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
        if (rawId != null && id == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val result = try {
            if (id == null) {
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

    companion object {
        private val LOG = LoggerFactory.getLogger(MusicDirectoryController::class.java)
        @JvmField val PATHS = arrayOf("/rest/getMusicDirectory", "/rest/getMusicDirectory.view")
    }
}
