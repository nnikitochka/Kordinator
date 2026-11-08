package org.airsonic.player.controller.rest.upload

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.service.StatusService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.UploadStatus
import org.subsonic.restapi.UploadStatuses
import kotlin.coroutines.cancellation.CancellationException

class UploadStatusController(private val status: StatusService, private val writer: JAXBWriter) {
    data class Request(val username: String, val allowed: Boolean, val params: Parameters)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        PATHS.forEach { path -> root.post(path) { respond(call, request(call)) } }
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val result = try {
            if (!request.allowed) writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Upload access denied")
            else writer.createResponse(true).apply {
                uploadStatuses = UploadStatuses().apply {
                    this@UploadStatusController.status.allUploadStatuses.filter { it.player.username == request.username }.forEach { transfer ->
                        upload.add(UploadStatus().apply {
                            transferId = transfer.id.toString()
                            bytesUploaded = transfer.bytesTransferred
                            bytesTotal = transfer.bytesTotal
                        })
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Upload status request failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Upload status request failed")
        }
        val resultText = writer.serializeForType(request.params["f"] ?: "xml", request.params["callback"], result)
        call.respondText(resultText.value, ContentType.parse(resultText.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(UploadStatusController::class.java)
        @JvmField val PATHS = arrayOf("/rest/getUploadStatus", "/rest/getUploadStatus.view")
    }
}
