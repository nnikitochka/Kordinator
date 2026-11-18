package org.airsonic.player.controller.rest.playlists

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import java.text.MessageFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.ResourceBundle
import org.airsonic.player.i18n.LocaleService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService

/** Supplies the website playlist page configuration through Ktor REST. */
class PlaylistPageController(
    private val playlists: PlaylistService,
    private val security: SecurityService,
    private val settings: SettingsService,
    private val locales: LocaleService,
    private val json: ObjectMapper,
) {
    data class Request(val username: String, val playlistId: Int)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(DATA_PATH) { respond(call, request(call)) }
            head(DATA_PATH) { respond(call, request(call)) }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val playlist = playlists.getPlaylist(request.playlistId)
        if (playlist == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        if (!playlists.isReadAllowed(playlist, request.username)) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }

        val userSettings = settings.getUserSettings(request.username)
        val locale = locales.resolveLocale(request.username)
        val createdDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
            .withZone(ZoneId.systemDefault()).format(playlist.created)
        val createdLabel = MessageFormat.format(
            ResourceBundle.getBundle("org.airsonic.player.i18n.ResourceBundle", locale).getString("playlist2.created"),
            playlist.username,
            createdDate,
        )
        val user = security.getUserByName(request.username)
        val body = mapOf(
            "playlistId" to playlist.id,
            "createdLabel" to createdLabel,
            "editAllowed" to (request.username == playlist.username || user.isAdminRole),
            "downloadAllowed" to user.isDownloadRole,
            "shareAllowed" to user.isShareRole,
            "initialPaginationSize" to userSettings.paginationSizePlaylist,
            "visibility" to userSettings.playlistVisibility,
        )
        call.response.header("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(body), ContentType.Application.Json)
    }

    companion object {
        const val DATA_PATH = "/rest/playlistPageSettings"
        const val REQUEST_ATTRIBUTE = "org.airsonic.player.playlistPageRequest"
        @JvmField val PAGE_PATHS = arrayOf("/playlist", "/playlist.view")
    }
}
