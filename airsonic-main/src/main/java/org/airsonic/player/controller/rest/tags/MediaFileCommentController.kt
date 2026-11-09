package org.airsonic.player.controller.rest.tags

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.SecurityService
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

/** Website extension for database comments; does not rewrite audio tags. */
class MediaFileCommentController(private val files: MediaFileService,
                                 private val security: SecurityService, private val writer: JAXBWriter) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        root.post(PATH) {
            val r = request(call)
            val p = r.parameters
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            val result = try {
                withContext(Dispatchers.IO) {
                    when {
                        !r.user.isCommentRole -> writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Comment permission required")
                        p["id"] == null || p["comment"] == null -> writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Missing id or comment")
                        else -> {
                            val id = p["id"]!!.toIntOrNull() ?: throw IllegalArgumentException("Invalid id")
                            val file = files.getMediaFile(id)
                            when {
                                file == null -> writer.createErrorResponse(ErrorCode.NOT_FOUND, "Media file not found")
                                !security.isFolderAccessAllowed(file, r.user.username) -> writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Folder access denied")
                                else -> {
                                    file.comment = p["comment"]!!
                                    files.updateMediaFile(file)
                                    writer.createResponse(true)
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: IllegalArgumentException) {
                writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Invalid comment data")
            } catch (e: Exception) {
                LOG.warn("Comment update failed", e)
                writer.createErrorResponse(ErrorCode.GENERIC, "Comment update failed")
            }
            val serialized = writer.serializeForType(p["f"] ?: "xml", p["callback"], result)
            call.respondText(serialized.value, ContentType.parse(serialized.key))
        }
    }
    companion object {
        private val LOG = LoggerFactory.getLogger(MediaFileCommentController::class.java)
        const val PATH = "/rest/setMediaFileComment"
    }
}
