package org.airsonic.player.controller.rest.info.album

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.service.LastFmService
import org.airsonic.player.service.MediaFileService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.AlbumInfo
import org.subsonic.restapi.Response
import kotlin.coroutines.cancellation.CancellationException

class AlbumInfoController(
    private val mediaFiles: MediaFileService,
    private val albums: AlbumDao,
    private val lastFm: LastFmService,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> AlbumInfoRequest) {
        with(root) {
            PATHS.forEach { path ->
                val second = path.removeSuffix(".view").endsWith("2")
                get(path) { respond(call, request(call), second) }
                post(path) { respond(call, request(call), second) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: AlbumInfoRequest, second: Boolean) {
        val response = try {
            albumInfo(request, second)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in album info API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: e.javaClass.simpleName)
        }
        val serialized = writer.serializeForType(
            request.parameters["f"] ?: "xml", request.parameters["callback"], response
        )
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun albumInfo(request: AlbumInfoRequest, second: Boolean): Response {
        val id = request.id ?: return writer.createErrorResponse(
            ErrorCode.MISSING_PARAMETER, "Required param (id) is missing"
        )
        val notes = if (second) {
            val album = albums.getAlbum(id)
                ?: return writer.createErrorResponse(ErrorCode.NOT_FOUND, "Album not found.")
            lastFm.getAlbumNotes(album)
        } else {
            val mediaFile = mediaFiles.getMediaFile(id)
                ?: return writer.createErrorResponse(ErrorCode.NOT_FOUND, "Media file not found.")
            lastFm.getAlbumNotes(mediaFile)
        }
        val result = AlbumInfo()
        if (notes != null) {
            result.notes = notes.notes
            result.musicBrainzId = notes.musicBrainzId
            result.lastFmUrl = notes.lastFmUrl
            result.smallImageUrl = notes.smallImageUrl
            result.mediumImageUrl = notes.mediumImageUrl
            result.largeImageUrl = notes.largeImageUrl
        }
        return writer.createResponse(true).apply { albumInfo = result }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(AlbumInfoController::class.java)
        @JvmField
        val PATHS = arrayOf(
            "/rest/getAlbumInfo", "/rest/getAlbumInfo.view",
            "/rest/getAlbumInfo2", "/rest/getAlbumInfo2.view",
        )
    }
}
