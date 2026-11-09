package org.airsonic.player.controller.rest.admin

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.UPnPService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.DLNASettings
import kotlin.coroutines.cancellation.CancellationException

/** Admin extensions; saving requires a complete snapshot, including an explicit boolean. */
class DLNASettingsController(private val settings: SettingsService, private val upnp: UPnPService,
                             private val writer: JAXBWriter) {
    private val lock = Any()

    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        with(root) {
            get(READ_PATH) { respond(call, request(call), false) }
            post(SAVE_PATH) { respond(call, request(call), true) }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: UsersRequest, save: Boolean) {
        val response = try {
            if (!request.user.isAdminRole) writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Administrator access required")
            else withContext(Dispatchers.IO) {
                synchronized(lock) {
                    if (save) save(request.parameters)
                    writer.createResponse(true).apply { if (!save) dlnaSettings = read() }
                }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: IllegalArgumentException) {
            writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Invalid DLNA settings")
        } catch (e: Exception) {
            LOG.warn("DLNA settings administration failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "DLNA settings administration failed")
        }
        call.response.headers.append("Cache-Control", "no-store")
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], response)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun read() = DLNASettings().apply {
        dlnaEnabled = settings.isDlnaEnabled
        dlnaServerName = settings.dlnaServerName
        dlnaBaseLANURL = settings.dlnaBaseLANURL.orEmpty()
    }

    private fun save(p: Parameters) {
        fun text(key: String) = p[key] ?: throw IllegalArgumentException("Missing $key")
        val enabled = when (text("dlnaEnabled")) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid boolean: dlnaEnabled")
        }
        val name = text("dlnaServerName").trim().ifEmpty { "Airsonic" }
        val url = text("dlnaBaseLANURL").trim().ifEmpty { null }
        // Preserve the existing stop / save / start lifecycle after validating every field.
        upnp.setMediaServerEnabled(false)
        settings.isDlnaEnabled = enabled
        settings.dlnaServerName = name
        settings.dlnaBaseLANURL = url
        settings.save()
        upnp.setMediaServerEnabled(enabled)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DLNASettingsController::class.java)
        const val READ_PATH = "/rest/getDlnaSettings"
        const val SAVE_PATH = "/rest/saveDlnaSettings"
    }
}
