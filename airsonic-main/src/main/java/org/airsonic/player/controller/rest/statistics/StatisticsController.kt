package org.airsonic.player.controller.rest.statistics

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.domain.TransferStatus
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.StatusService
import org.airsonic.player.util.FileUtil
import org.airsonic.player.util.StringUtil
import org.slf4j.LoggerFactory
import org.subsonic.restapi.*
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Website statistics extension, retaining access for every authenticated user. */
class StatisticsController(private val status: StatusService, private val security: SecurityService, private val writer: JAXBWriter) {
    data class Request(val params: Parameters, val locale: Locale)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        root.get(TRANSFERS_PATH) { respond(call, request(call), false) }
        root.get(USERS_PATH) { respond(call, request(call), true) }
    }

    private suspend fun respond(call: ApplicationCall, request: Request, users: Boolean) {
        call.response.headers.append("Cache-Control", "no-store")
        val result = try {
            withContext(Dispatchers.IO) {
                writer.createResponse(true).apply {
                    if (users) userTransferStatistics = UserTransferStatistics().apply {
                        security.allUsers.forEach { value -> user.add(UserTransferStatisticsEntry().apply {
                            username = value.username; streamed = value.bytesStreamed
                            downloaded = value.bytesDownloaded; uploaded = value.bytesUploaded
                        }) }
                    } else transferStatistics = TransferStatistics().apply {
                        this@StatisticsController.status.allStreamStatuses.filter { it.millisSinceLastUpdate < 3_600_000L }
                            .forEach { transfer.add(snapshot(it, "stream", request.locale)) }
                        this@StatisticsController.status.allDownloadStatuses.forEach { transfer.add(snapshot(it, "download", request.locale)) }
                        this@StatisticsController.status.allUploadStatuses.forEach { transfer.add(snapshot(it, "upload", request.locale)) }
                    }
                }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            LOG.warn("Statistics request failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Statistics request failed")
        }
        val serialized = writer.serializeForType(request.params["f"] ?: "xml", request.params["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun snapshot(value: TransferStatus, type: String, locale: Locale) = TransferStatisticsEntry().apply {
        transferType = type
        playerDescription = value.player?.toString(); playerType = value.player?.type; username = value.player?.username
        path = (value.mediaFile?.relativePath ?: value.externalFile)?.let { FileUtil.getShortPath(it) }
        bytesTransferred = StringUtil.formatBytes(value.bytesTransferred, locale)
        value.history.forEach { entry -> sample.add(TransferStatisticsSample().apply {
            timestamp = entry.timestamp; bytesTransferred = entry.bytesTransferred
        }) }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(StatisticsController::class.java)
        const val TRANSFERS_PATH = "/rest/getTransferStatistics"
        const val USERS_PATH = "/rest/getUserTransferStatistics"
        @JvmField val PATHS = arrayOf(TRANSFERS_PATH, USERS_PATH)
    }
}
