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
import org.airsonic.player.service.DatabaseService
import org.subsonic.restapi.DatabaseStatuses
import org.subsonic.restapi.DatabaseOperationStatus
import org.slf4j.LoggerFactory
import org.subsonic.restapi.UploadStatus
import org.subsonic.restapi.UploadStatuses
import kotlin.coroutines.cancellation.CancellationException

class UploadStatusController(private val status: StatusService, private val writer: JAXBWriter, private val database: DatabaseService) {
    data class Request(val username: String, val allowed: Boolean, val admin: Boolean, val params: Parameters)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        UPLOAD_PATHS.forEach { path -> root.post(path) { respond(call, request(call)) } }
        DATABASE_PATHS.forEach { path -> root.post(path) { respondDatabase(call, request(call)) } }
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

    private suspend fun respondDatabase(call: ApplicationCall, request: Request) {
        val result = if (!request.admin) writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Admin access required")
        else writer.createResponse(true).apply {
            databaseStatuses = DatabaseStatuses().apply {
                backup = database.backupStatus.toDto()
                export = database.exportStatus.toDto()
                `import` = database.importStatus.toDto()
            }
        }
        val serialized = writer.serializeForType(request.params["f"] ?: "xml", request.params["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun DatabaseService.OperationStatus.toDto() = DatabaseOperationStatus().also {
        it.version = version(); it.running = running(); it.failed = failed(); it.message = message()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(UploadStatusController::class.java)
        private val UPLOAD_PATHS = arrayOf("/rest/getUploadStatus", "/rest/getUploadStatus.view")
        private val DATABASE_PATHS = arrayOf("/rest/getDatabaseStatus", "/rest/getDatabaseStatus.view")
        @JvmField val PATHS = UPLOAD_PATHS + DATABASE_PATHS
    }
}
