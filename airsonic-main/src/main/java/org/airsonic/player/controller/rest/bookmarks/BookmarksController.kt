package org.airsonic.player.controller.rest.bookmarks

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
import org.airsonic.player.service.BookmarkService
import org.airsonic.player.service.MediaFileService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Bookmark
import org.subsonic.restapi.Bookmarks
import org.subsonic.restapi.Response
import kotlin.coroutines.cancellation.CancellationException

class BookmarksController(
    private val bookmarks: BookmarkService,
    private val mediaFiles: MediaFileService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> BookmarksRequest) {
        with(root) {
            PATHS.forEach { path ->
                val operation = path.substringAfterLast('/').removeSuffix(".view")
                get(path) { respond(call, request(call), operation) }
                post(path) { respond(call, request(call), operation) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: BookmarksRequest, operation: String) {
        val result = try {
            when (operation) {
                "getBookmarks" -> getBookmarks(request)
                "createBookmark" -> createBookmark(request)
                "deleteBookmark" -> deleteBookmark(request)
                else -> error("Unknown bookmark operation")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in bookmarks API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        if (result == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun getBookmarks(request: BookmarksRequest): Response {
        val result = Bookmarks()
        bookmarks.getBookmarks(request.username).forEach { bookmark ->
            val mediaFile = mediaFiles.getMediaFile(bookmark.mediaFileId) ?: return@forEach
            val entry = Bookmark().apply {
                position = bookmark.positionMillis
                username = bookmark.username
                comment = bookmark.comment
                created = writer.convertDate(bookmark.created)
                changed = writer.convertDate(bookmark.changed)
                if (request.parameters["includeEntryAvailability"] == "true") {
                    entryPresent = mediaFile.isPresent
                }
                this.entry = mapper.child(requireNotNull(request.player), mediaFile, request.username)
            }
            result.bookmark.add(entry)
        }
        return writer.createResponse(true).apply { this.bookmarks = result }
    }

    private fun createBookmark(request: BookmarksRequest): Response? {
        val id = request.id ?: return missing("id")
        val position = request.parameters["position"] ?: return missing("position")
        val parsedPosition = position.toLongOrNull() ?: return null
        bookmarks.setBookmark(request.username, id, parsedPosition, request.parameters["comment"])
        return writer.createResponse(true)
    }

    private fun deleteBookmark(request: BookmarksRequest): Response {
        val id = request.id ?: return missing("id")
        bookmarks.deleteBookmark(request.username, id)
        return writer.createResponse(true)
    }

    private fun missing(name: String) = writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Required param ($name) is missing")

    companion object {
        private val LOG = LoggerFactory.getLogger(BookmarksController::class.java)
        @JvmField val PATHS = arrayOf(
            "/rest/getBookmarks", "/rest/getBookmarks.view",
            "/rest/createBookmark", "/rest/createBookmark.view",
            "/rest/deleteBookmark", "/rest/deleteBookmark.view",
        )
    }
}
