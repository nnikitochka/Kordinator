package org.airsonic.player.controller.rest.indexes

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.User
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.MediaScannerService
import org.airsonic.player.service.MusicIndexService
import org.airsonic.player.service.MusicIndexStateService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.search.IndexManager
import org.airsonic.player.util.StringUtil
import java.util.Locale

/** REST data and actions for the legacy left navigation frame. */
class LeftPageController(
    private val settings: SettingsService,
    private val folders: MediaFolderService,
    private val musicIndex: MusicIndexService,
    private val indexState: MusicIndexStateService,
    private val scanner: MediaScannerService,
    private val indexManager: IndexManager,
    private val players: PlayerService,
    private val json: ObjectMapper,
) {
    data class Request(val user: User, val player: Player, val locale: Locale, val refresh: Boolean = false)
    data class Selection(val username: String, val folderId: Int?)

    fun routes(root: Route, request: (ApplicationCall) -> Request, selection: (ApplicationCall) -> Selection) {
        with(root) {
            get(DATA_PATH) { respond(call, request(call)) }
            head(DATA_PATH) { respond(call, request(call)) }
            post(SELECT_PATH) {
                val selected = selection(call)
                val changed = indexState.selectMusicFolder(selected.username, selected.folderId)
                call.response.header("Cache-Control", "no-store")
                call.respondText(json.writeValueAsString(mapOf("changed" to changed)), ContentType.Application.Json)
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val user = request.user
        if (request.refresh) folders.clearMusicFolderCache()
        val allFolders = folders.getMusicFoldersForUser(user.username)
        val userSettings = settings.getUserSettings(user.username)
        val selectedFolders = folders.getMusicFoldersForUser(user.username, userSettings.selectedMusicFolderId)
        val selected = selectedFolders.firstOrNull { it.id == userSettings.selectedMusicFolderId }
        val content = musicIndex.getMusicFolderContent(selectedFolders, request.refresh)
        val statistics = indexManager.statistics
        val data = mapOf(
            "folders" to allFolders.map { mapOf("id" to it.id, "name" to it.name) },
            "selectedFolderId" to selected?.id,
            "scanning" to scanner.isScanning,
            "partyMode" to userSettings.partyModeEnabled,
            "organizeByFolderStructure" to settings.isOrganizeByFolderStructure,
            "streamRole" to user.isStreamRole,
            "downloadRole" to user.isDownloadRole,
            "playerWeb" to request.player.isWeb,
            "partyMode" to userSettings.partyModeEnabled,
            "shortcuts" to musicIndex.getShortcuts(selectedFolders).map { mapOf("id" to it.id, "name" to it.name) },
            "indexes" to content.indexedArtists.map { (index, artists) ->
                mapOf("index" to index.index, "artists" to artists.map { artist ->
                    mapOf("name" to artist.name, "ids" to artist.mediaFiles.map { it.id })
                })
            },
            "songs" to content.singleSongs.map { mapOf("id" to it.id, "title" to it.title, "video" to it.isVideo, "present" to it.isPresent) },
            "statistics" to statistics?.let { mapOf("artistCount" to it.artistCount, "albumCount" to it.albumCount,
                "songCount" to it.songCount, "bytes" to StringUtil.formatBytes(it.totalLengthInBytes, request.locale),
                "hours" to (it.totalDurationInSeconds / 3600.0).toLong()) },
        )
        call.response.header("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(data), ContentType.Application.Json)
    }

    companion object {
        const val DATA_PATH = "/rest/leftPageData"
        const val SELECT_PATH = "/rest/selectLeftMusicFolder"
        const val REQUEST_ATTRIBUTE = "org.airsonic.player.leftPageRequest"
        const val SELECTION_ATTRIBUTE = "org.airsonic.player.leftPageSelection"
        const val PAGE_PATH = "/left.view"
    }
}
