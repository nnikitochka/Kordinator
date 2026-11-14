package org.airsonic.player.controller.rest.home

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import org.airsonic.player.domain.AlbumListType
import org.airsonic.player.domain.CoverArtScheme
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.MediaScannerService
import org.airsonic.player.service.RatingService
import org.airsonic.player.service.SearchService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import java.time.Instant
import java.time.LocalDate

/** Website home page data. */
class HomePageController(
    private val settings: SettingsService,
    private val scanner: MediaScannerService,
    private val ratings: RatingService,
    private val security: SecurityService,
    private val mediaFiles: MediaFileService,
    private val folders: MediaFolderService,
    private val search: SearchService,
    private val json: ObjectMapper,
) {
    data class Request(val username: String)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(DATA_PATH) { respond(call, request(call)) }
            head(DATA_PATH) { respond(call, request(call)) }
        }
    }

    fun shouldRedirectToGettingStarted(username: String): Boolean =
        security.getUserByName(username).isAdminRole && settings.isGettingStartedEnabled

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val query = call.request.queryParameters
        val userSettings = settings.getUserSettings(request.username)
        val listType = AlbumListType.fromId(query["listType"]) ?: userSettings.defaultAlbumList
        val offset = query["listOffset"]?.toIntOrNull() ?: 0
        val decades = createDecades()
        val decade = query["decade"]?.toIntOrNull() ?: decades.first()
        val musicFolders = folders.getMusicFoldersForUser(request.username, userSettings.selectedMusicFolderId)
        val selectedMusicFolder = musicFolders.firstOrNull { it.id == userSettings.selectedMusicFolderId }

        val genres = if (listType == AlbumListType.GENRE) mediaFiles.getGenres(true) else emptyList()
        val genre = query["genre"]?.takeIf { selected -> genres.any { it.name == selected } }
            ?: genres.firstOrNull()?.name
        val albums = when (listType) {
            AlbumListType.HIGHEST -> ratings.getHighestRatedAlbums(offset, LIST_SIZE, musicFolders).map { file ->
                album(file, rating = (ratings.getAverageRating(file) * 10.0).let { kotlin.math.round(it).toInt() })
            }
            AlbumListType.FREQUENT -> mediaFiles.getMostFrequentlyPlayedAlbums(offset, LIST_SIZE, musicFolders)
                .map { album(it, playCount = it.playCount) }
            AlbumListType.RECENT -> mediaFiles.getMostRecentlyPlayedAlbums(offset, LIST_SIZE, musicFolders)
                .map { album(it, lastPlayed = it.lastPlayed) }
            AlbumListType.NEWEST -> mediaFiles.getNewestAlbums(offset, LIST_SIZE, musicFolders).map {
                album(it, created = it.created ?: it.changed)
            }
            AlbumListType.STARRED -> mediaFiles.getStarredAlbums(offset, LIST_SIZE, request.username, musicFolders).map(::album)
            AlbumListType.RANDOM -> search.getRandomAlbums(LIST_SIZE, musicFolders).map(::album)
            AlbumListType.ALPHABETICAL -> mediaFiles.getAlphabeticalAlbums(offset, LIST_SIZE, true, musicFolders).map(::album)
            AlbumListType.DECADE -> mediaFiles.getAlbumsByYear(offset, LIST_SIZE, decade, decade + 9, musicFolders)
                .map { album(it, year = it.year) }
            AlbumListType.GENRE -> if (genre == null) emptyList() else
                mediaFiles.getAlbumsByGenre(offset, LIST_SIZE, genre, musicFolders).map(::album)
        }

        val body = mapOf(
            "albums" to albums,
            "welcomeTitle" to settings.welcomeTitle,
            "welcomeSubtitle" to settings.welcomeSubtitle,
            "welcomeMessage" to settings.welcomeMessage,
            "isIndexBeingCreated" to scanner.isScanning,
            "musicFoldersExist" to folders.allMusicFolders.isNotEmpty(),
            "listType" to listType.id,
            "listSize" to LIST_SIZE,
            "coverArtSize" to CoverArtScheme.MEDIUM.size,
            "listOffset" to offset,
            "musicFolder" to selectedMusicFolder?.let { mapOf("id" to it.id, "name" to it.name) },
            "decades" to if (listType == AlbumListType.DECADE) decades else emptyList<Int>(),
            "decade" to decade,
            "genres" to genres.map { mapOf("name" to it.name, "albumCount" to it.getAlbumCount()) },
            "genre" to genre,
        )
        call.response.header("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(body), ContentType.Application.Json)
    }

    private fun album(
        file: MediaFile,
        created: Instant? = null,
        lastPlayed: Instant? = null,
        playCount: Int? = null,
        rating: Int? = null,
        year: Int? = null,
    ) = Album(file.id, file.artist, file.albumName, created, lastPlayed, playCount, rating, year)

    private data class Album(
        val id: Int,
        val artist: String?,
        val albumTitle: String?,
        val created: Instant? = null,
        val lastPlayed: Instant? = null,
        val playCount: Int? = null,
        val rating: Int? = null,
        val year: Int? = null,
    )

    private fun createDecades(): List<Int> {
        val decade = LocalDate.now().year / 10
        return (0 until 10).map { (decade - it) * 10 }
    }

    companion object {
        const val DATA_PATH = "/rest/homePage"
        const val REQUEST_ATTRIBUTE = "org.airsonic.player.homePageRequest"
        @JvmField val PAGE_PATHS = arrayOf("/home", "/home.view")
        private const val LIST_SIZE = 40
    }
}
