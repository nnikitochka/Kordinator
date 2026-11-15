package org.airsonic.player.controller.rest.nowplaying

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import org.airsonic.player.domain.Version
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.VersionService
import java.net.URI

/** Minimal website data for the right frame. */
class RightPageController(
    private val settings: SettingsService,
    private val versions: VersionService,
    private val json: ObjectMapper,
) {
    data class Request(val username: String)
    data class PageData(
        val brand: String,
        val showNowPlaying: Boolean,
        val newVersionAvailable: Boolean,
        val latestVersion: String?,
        val latestVersionUrl: String?,
    )

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(DATA_PATH) { respond(call, request(call)) }
            head(DATA_PATH) { respond(call, request(call)) }
        }
    }

    fun page(request: Request): PageData {
        val userSettings = settings.getUserSettings(request.username)
        val latest = when {
            userSettings.finalVersionNotificationEnabled && versions.isNewFinalVersionAvailable -> versions.latestFinalVersion
            userSettings.betaVersionNotificationEnabled && versions.isNewBetaVersionAvailable -> versions.latestBetaVersion
            else -> null
        }
        return PageData(
            settings.brand,
            userSettings.showNowPlayingEnabled,
            latest != null,
            latest?.toString(),
            latest?.let(::trustedReleaseUrl),
        )
    }

    private fun trustedReleaseUrl(version: Version): String? {
        val uri = runCatching { URI(version.url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "github.com" || uri.userInfo != null || uri.port != -1 || uri.query != null || uri.fragment != null) {
            return null
        }
        if (!uri.path.orEmpty().startsWith("/airsonic-advanced/airsonic-advanced/releases/tag/")) return null
        return uri.toASCIIString()
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        call.response.header("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(page(request)), ContentType.Application.Json)
    }

    companion object {
        const val DATA_PATH = "/rest/rightPageSettings"
        const val REQUEST_ATTRIBUTE = "org.airsonic.player.rightPageRequest"
        @JvmField val PAGE_PATHS = arrayOf("/right", "/right.view")
    }
}
