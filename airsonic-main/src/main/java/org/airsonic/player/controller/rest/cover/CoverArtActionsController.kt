package org.airsonic.player.controller.rest.cover

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.service.CoverArtSearchService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.CoverArtSearchImage
import org.subsonic.restapi.CoverArtSearchResults
import kotlin.coroutines.cancellation.CancellationException

class CoverArtActionsController(private val service: CoverArtSearchService, private val writer: JAXBWriter) {
    data class Request(val username: String, val allowed: Boolean, val params: Parameters)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            PATHS.forEach { path -> post(path) { respond(call, path, request(call)) } }
        }
    }

    private suspend fun respond(call: ApplicationCall, path: String, request: Request) {
        val p = request.params
        val result = try {
            when {
                !request.allowed -> writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Cover art permission required")
                path.substringAfterLast('/').removeSuffix(".view") == "searchCoverArt" -> {
                    val artist = p["artist"]
                    val album = p["album"]
                    if (artist == null || album == null) writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Artist and album are required")
                    else {
                        val images = CoverArtSearchResults()
                        service.searchCoverArt(artist, album).forEach { item ->
                            images.image.add(CoverArtSearchImage().apply {
                                imageUrl = item.imageUrl; this.artist = item.artist; this.album = item.album
                            })
                        }
                        writer.createResponse(true).apply { coverArtSearchResults = images }
                    }
                }
                p["id"] == null || p["url"].isNullOrBlank() -> writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "ID and URL are required")
                p["id"]?.toIntOrNull() == null -> writer.createErrorResponse(ErrorCode.GENERIC, "Invalid ID")
                else -> {
                    service.setCoverArtImage(p["id"]!!.toInt(), p["url"]!!, request.username)
                    writer.createResponse(true)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Cover art access denied")
        } catch (e: Exception) {
            LOG.warn("Cover art request failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Cover art request failed")
        }
        val serialized = writer.serializeForType(p["f"] ?: "xml", p["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(CoverArtActionsController::class.java)
        @JvmField val PATHS = arrayOf("/rest/searchCoverArt", "/rest/searchCoverArt.view", "/rest/setCoverArt", "/rest/setCoverArt.view")
    }
}
