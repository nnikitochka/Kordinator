package org.airsonic.player.controller.rest.tags

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.airsonic.player.ajax.tag.TagData
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.security.CallerContext
import org.airsonic.player.service.TagEditService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.TagEdit
import kotlin.coroutines.cancellation.CancellationException

class TagEditController(private val service: TagEditService, private val writer: JAXBWriter) {
    data class Request(val callerContext: CallerContext, val allowed: Boolean, val params: Parameters)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) { PATHS.forEach { path -> post(path) { respond(call, request(call)) } } }
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val p = request.params
        val result = try {
            when {
                !request.allowed -> writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Tag editing permission required")
                p["id"] == null -> writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "ID is required")
                else -> {
                    val id = p["id"]?.toIntOrNull() ?: throw IllegalArgumentException("Invalid ID")
                    fun number(name: String): Int? = p[name]?.takeUnless { it.isBlank() }?.let {
                        it.toIntOrNull() ?: throw IllegalArgumentException("Invalid $name")
                    }
                    val data = TagData().apply {
                        mediaFileId = id; artist = p["artist"]; album = p["album"]; title = p["title"]
                        genre = p["genre"]; track = number("track"); year = number("year")
                    }
                    writer.createResponse(true).apply {
                        tagEdit = TagEdit().apply { this.result = service.setTags(data, request.callerContext) }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Tag editing access denied")
        } catch (e: IllegalArgumentException) {
            writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Invalid tag data")
        } catch (e: Exception) {
            LOG.warn("Tag editing request failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Tag editing request failed")
        }
        val serialized = writer.serializeForType(p["f"] ?: "xml", p["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(TagEditController::class.java)
        @JvmField val PATHS = arrayOf("/rest/editTags", "/rest/editTags.view")
    }
}
