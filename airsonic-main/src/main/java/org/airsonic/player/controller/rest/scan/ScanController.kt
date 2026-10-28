package org.airsonic.player.controller.rest.scan

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.service.MediaScannerService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.ScanStatus
import kotlin.coroutines.cancellation.CancellationException

class ScanController(
    private val scanner: MediaScannerService,
    private val jaxbWriter: JAXBWriter,
) {
    fun routes(root: Route, parameters: (ApplicationCall) -> Parameters) {
        with(root) {
            START_PATHS.forEach { path ->
                get(path) { respond(call, parameters(call), true) }
                post(path) { respond(call, parameters(call), true) }
            }
            STATUS_PATHS.forEach { path ->
                get(path) { respond(call, parameters(call), false) }
                post(path) { respond(call, parameters(call), false) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, parameters: Parameters, start: Boolean) {
        val response = try {
            if (start) scanner.scanLibrary()
            val status = ScanStatus().apply {
                scanning = scanner.isScanning
                count = scanner.scanCount.toLong()
            }
            jaxbWriter.createResponse(true).apply { scanStatus = status }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in Subsonic scan API", e)
            jaxbWriter.createErrorResponse(ErrorCode.GENERIC, e.message ?: e.javaClass.simpleName)
        }
        val serialized = jaxbWriter.serializeForType(parameters["f"] ?: "xml", parameters["callback"], response)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ScanController::class.java)
        private val START_PATHS = arrayOf("/rest/startScan", "/rest/startScan.view")
        private val STATUS_PATHS = arrayOf("/rest/getScanStatus", "/rest/getScanStatus.view")

        @JvmField
        val PATHS = START_PATHS + STATUS_PATHS
    }
}
