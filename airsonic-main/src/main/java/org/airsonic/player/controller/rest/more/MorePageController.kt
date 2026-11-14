package org.airsonic.player.controller.rest.more

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import org.airsonic.player.domain.User
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.SettingsService
import java.time.LocalDate
import java.util.function.Supplier

/** REST data endpoint for the website's More page. */
class MorePageController(
    private val settings: SettingsService,
    private val mediaFiles: MediaFileService,
    private val folders: MediaFolderService,
    private val json: ObjectMapper,
) {
    data class Request(val user: User, val playerClientPlaylist: Boolean)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(PATH) { respond(call, request(call)) }
            head(PATH) { respond(call, request(call)) }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val user = request.user
        val musicFolders = folders.getMusicFoldersForUser(user.username)
        val contextSupplier = Supplier {
            val context = settings.buildSpelContext()
            if (user.username.isNotEmpty()) {
                context["USER_NAME"] = user.username
                context["USER_MUSIC_FOLDERS"] = musicFolders.map { it.path.toString() }
            }
            context
        }
        val uploadsFolder = settings.uploadsFolder
        val uploadDirectory = if (musicFolders.isEmpty() && uploadsFolder?.contains("USER_MUSIC_FOLDERS") == true) ""
        else settings.resolveContextualString(uploadsFolder, contextSupplier)
        val data = mapOf(
            "user" to mapOf("uploadRole" to user.isUploadRole, "streamRole" to user.isStreamRole),
            "uploadDirectory" to if (user.isUploadRole) uploadDirectory else null,
            "genres" to mediaFiles.getGenres(false).map { mapOf("name" to it.name, "songCount" to it.getSongCount()) },
            "currentYear" to LocalDate.now().year,
            "musicFolders" to musicFolders.map { mapOf("id" to it.id, "name" to it.name) },
            "clientSidePlaylist" to request.playerClientPlaylist,
            "brand" to settings.brand,
        )
        call.response.header("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(data), ContentType.Application.Json)
    }

    companion object {
        const val PATH = "/rest/morePageData"
        const val REQUEST_ATTRIBUTE = "org.airsonic.player.morePageRequest"
        @JvmField val PAGE_PATHS = arrayOf("/more", "/more.view")
    }
}
