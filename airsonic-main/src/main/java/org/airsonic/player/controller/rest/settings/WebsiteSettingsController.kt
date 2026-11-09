package org.airsonic.player.controller.rest.settings

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.domain.AvatarScheme
import org.airsonic.player.service.SettingsService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.WebsiteSettings
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class WebsiteSettingsController(
    private val settings: SettingsService,
    private val writer: JAXBWriter
) {
    data class Request(val username: String, val params: Parameters, val settingsRole: Boolean)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        UPDATE_PATHS.forEach { path -> root.post(path) { respond(call, request(call), false) } }
        READ_PATHS.forEach { path -> root.post(path) { respond(call, request(call), true) } }
    }

    private suspend fun respond(call: ApplicationCall, request: Request, read: Boolean) {
        call.response.headers.append("Cache-Control", "no-store")
        val p = request.params
        val sidebar = p["showSideBar"]
        val list = p["viewAsList"]
        val result = try {
            if (read) {
                val userSettings = settings.getUserSettings(request.username)
                writer.createResponse(true).apply {
                    websiteSettings = WebsiteSettings().apply {
                        username = request.username
                        settingsRole = request.settingsRole
                        showAvatar = userSettings.avatarScheme != AvatarScheme.NONE
                        showSideBar = userSettings.showSideBar
                        viewAsList = userSettings.viewAsList
                    }
                }
            } else if (sidebar == null && list == null) {
                writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "A setting is required")
            } else if (listOfNotNull(sidebar, list).any { it != "true" && it != "false" }) {
                writer.createErrorResponse(ErrorCode.GENERIC, "Invalid boolean setting")
            } else {
                val userSettings = settings.getUserSettings(request.username)
                var changed = false
                sidebar?.toBooleanStrict()?.let {
                    if (userSettings.showSideBar != it) { userSettings.showSideBar = it; changed = true }
                }
                list?.toBooleanStrict()?.let {
                    if (userSettings.viewAsList != it) { userSettings.viewAsList = it; changed = true }
                }
                if (changed) {
                    userSettings.changed = Instant.now()
                    settings.updateUserSettings(userSettings)
                }
                writer.createResponse(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Website settings request failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Website settings request failed")
        }
        val serialized = writer.serializeForType(p["f"] ?: "xml", p["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(WebsiteSettingsController::class.java)
        private val UPDATE_PATHS = arrayOf("/rest/updateWebsiteSettings", "/rest/updateWebsiteSettings.view")
        private val READ_PATHS = arrayOf("/rest/getWebsiteSettings", "/rest/getWebsiteSettings.view")
        @JvmField val PATHS = UPDATE_PATHS + READ_PATHS
    }
}
