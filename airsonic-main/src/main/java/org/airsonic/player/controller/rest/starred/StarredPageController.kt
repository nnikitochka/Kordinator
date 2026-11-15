package org.airsonic.player.controller.rest.starred

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.domain.CoverArtScheme
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Player
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import java.time.Instant

/** Minimal website data for the starred page. */
class StarredPageController(
    private val files: MediaFileDao,
    private val mediaFiles: MediaFileService,
    private val folders: MediaFolderService,
    private val security: SecurityService,
    private val settings: SettingsService,
    private val json: ObjectMapper,
) {
    data class Request(val username: String, val player: Player?)
    data class ArtistItem(val id: Int, val name: String?, val directory: Boolean, val starredDate: Instant?)
    data class AlbumItem(val id: Int, val name: String?, val artist: String?, val starredDate: Instant?)
    data class SongItem(val id: Int, val title: String?, val album: String?, val artist: String?, val parentId: Int?, val starredDate: Instant?)
    data class VideoItem(val id: Int, val name: String?, val starredDate: Instant?)
    data class PageData(
        val artists: List<ArtistItem>,
        val albums: List<AlbumItem>,
        val songs: List<SongItem>,
        val videos: List<VideoItem>,
        val partyModeEnabled: Boolean,
        val streamAllowed: Boolean,
        val webPlayer: Boolean,
        val coverArtSize: Int,
    )

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(DATA_PATH) { respond(call, request(call)) }
            head(DATA_PATH) { respond(call, request(call)) }
        }
    }

    fun page(request: Request): PageData {
        val user = security.getUserByName(request.username)
        val userSettings = settings.getUserSettings(request.username)
        val musicFolders = folders.getMusicFoldersForUser(request.username)
        val artists = files.getStarredDirectories(0, Int.MAX_VALUE, request.username, musicFolders)
        val albums = files.getStarredAlbums(0, Int.MAX_VALUE, request.username, musicFolders)
        val starredFiles = files.getStarredFiles(0, Int.MAX_VALUE, request.username, musicFolders)
        mediaFiles.populateStarredDate(artists, request.username)
        mediaFiles.populateStarredDate(albums, request.username)
        mediaFiles.populateStarredDate(starredFiles, request.username)

        val songs = ArrayList<SongItem>()
        val videos = ArrayList<VideoItem>()
        starredFiles.forEach { file ->
            if (file.isVideo) {
                videos.add(VideoItem(file.id, file.name, file.starredDate))
            } else {
                val parentId = try { mediaFiles.getParentOf(file)?.id } catch (_: SecurityException) { null }
                songs.add(SongItem(file.id, file.title, file.albumName, file.artist, parentId, file.starredDate))
            }
        }
        return PageData(
            artists.map { ArtistItem(it.id, it.name, it.isDirectory, it.starredDate) },
            albums.map { AlbumItem(it.id, it.name, it.artist, it.starredDate) },
            songs,
            videos,
            userSettings.partyModeEnabled,
            user.isStreamRole,
            request.player?.isWeb == true,
            CoverArtScheme.MEDIUM.size,
        )
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        call.response.header("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(page(request)), ContentType.Application.Json)
    }

    companion object {
        const val DATA_PATH = "/rest/starredPage"
        const val REQUEST_ATTRIBUTE = "org.airsonic.player.starredPageRequest"
        @JvmField val PAGE_PATHS = arrayOf("/starred", "/starred.view")
    }
}
