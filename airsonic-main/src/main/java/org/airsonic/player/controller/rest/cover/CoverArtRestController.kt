package org.airsonic.player.controller.rest.cover

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import org.airsonic.player.service.CoverArtImageService

/** Serves standard REST and signed external cover art responses directly from Ktor. */
class CoverArtRestController(private val images: CoverArtImageService) {
    fun routes(root: Route, parameters: (ApplicationCall) -> Parameters) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, parameters(call), false) }
                post(path) { respond(call, parameters(call), false) }
                head(path) { respond(call, parameters(call), true) }
            }
            EXTERNAL_PATHS.forEach { path ->
                get(path) { respond(call, parameters(call), false) }
                head(path) { respond(call, parameters(call), true) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, parameters: Parameters, head: Boolean) {
        val size = parameters["size"]?.takeUnless { it.isBlank() }?.toInt()
        val offset = parameters["offset"]
        val image = images.getImage(parameters["id"], size, offset)
        val type = ContentType.parse(image.contentType())
        if (head) {
            image.close()
            call.respond(object : OutgoingContent.NoContent() { override val contentType = type })
        } else {
            call.respondOutputStream(type) { image.use { it.stream().copyTo(this) } }
        }
    }

    companion object {
        @JvmField val PATHS = arrayOf("/rest/getCoverArt", "/rest/getCoverArt.view")
        @JvmField val EXTERNAL_PATHS = arrayOf("/ext/coverArt", "/ext/coverArt.view")
    }
}
