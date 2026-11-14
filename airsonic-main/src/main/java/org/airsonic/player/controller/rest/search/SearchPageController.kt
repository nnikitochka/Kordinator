package org.airsonic.player.controller.rest.search

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
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.SearchCriteria
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SearchService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.search.IndexType
import org.airsonic.player.dao.AlbumDao
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

/** Website search endpoint. Subsonic search operations remain in SearchController. */
class SearchPageController(
    private val search: SearchService,
    private val security: SecurityService,
    private val folders: MediaFolderService,
    private val settings: SettingsService,
    private val albums: AlbumDao,
    private val mediaFiles: MediaFileService,
    private val json: ObjectMapper,
) {
    data class Request(val username: String, val player: Player)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(DATA_PATH) {
                val query = call.request.queryParameters["query"].orEmpty().trim()
                respond(call, request(call), query)
            }
            head(DATA_PATH) {
                val query = call.request.queryParameters["query"].orEmpty().trim()
                respond(call, request(call), query)
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request, query: String) {
        try {
            val body = search(request, query)
            call.response.header("Cache-Control", "no-store")
            call.respondText(json.writeValueAsString(body), ContentType.Application.Json)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in website search", e)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }

    private fun search(request: Request, query: String): Map<String, Any> {
        val userSettings = settings.getUserSettings(request.username)
        val streamAllowed = security.getUserByName(request.username).isStreamRole
        if (query.isBlank()) return emptyResponse(request, streamAllowed, userSettings.partyModeEnabled)

        val musicFolders = folders.getMusicFoldersForUser(request.username)
        val criteria = SearchCriteria().apply {
            count = userSettings.searchCount
            this.query = query
        }

        val artistIndex = search.search(criteria, musicFolders, IndexType.ARTIST)
        val artistId3Index = search.search(criteria, musicFolders, IndexType.ARTIST_ID3)
        val artistMatches = LinkedHashMap<String, LinkedHashSet<Int>>()
        artistId3Index.artists.forEach { artist ->
            albums.getAlbumsForArtist(artist.name, musicFolders).forEach { album ->
                mediaFiles.getMediaFile(album.path, album.folderId)?.let { add(artistMatches, artist.name, it.id) }
            }
        }
        artistIndex.mediaFiles.forEach { file ->
            val artist = file.artist ?: file.albumArtist ?: "(Unknown)"
            add(artistMatches, artist, file.id)
        }

        val albumIndex = search.search(criteria, musicFolders, IndexType.ALBUM)
        val albumId3Index = search.search(criteria, musicFolders, IndexType.ALBUM_ID3)
        val albumMatches = LinkedHashMap<Pair<String, String>, LinkedHashSet<Int>>()
        albumId3Index.albums.forEach { album ->
            mediaFiles.getMediaFile(album.path, album.folderId)?.let { file ->
                add(albumMatches, album.name to album.artist, file.id)
            }
        }
        albumIndex.mediaFiles.forEach { file -> add(albumMatches, file.albumName to file.albumArtist, file.id) }

        val songs = search.search(criteria, musicFolders, IndexType.SONG).mediaFiles.map { file ->
            mapOf(
                "id" to file.id,
                "title" to file.title,
                "albumName" to file.albumName,
                "artist" to file.artist,
                "folderId" to file.folderId,
                "parentPath" to file.parentPath,
                "video" to (file.isVideo && request.player.isWeb),
            )
        }

        return mapOf(
            "artists" to artistMatches.map { (name, ids) -> mapOf("name" to name, "ids" to ids) },
            "albums" to albumMatches.map { (key, ids) -> mapOf("name" to key.first, "artist" to key.second, "ids" to ids) },
            "songs" to songs,
            "streamAllowed" to streamAllowed,
            "partyMode" to userSettings.partyModeEnabled,
            "webPlayer" to request.player.isWeb,
        )
    }

    private fun emptyResponse(request: Request, streamAllowed: Boolean, partyMode: Boolean): Map<String, Any> = mapOf(
        "artists" to emptyList<Any>(),
        "albums" to emptyList<Any>(),
        "songs" to emptyList<Any>(),
        "streamAllowed" to streamAllowed,
        "partyMode" to partyMode,
        "webPlayer" to request.player.isWeb,
    )

    private fun <K> add(target: MutableMap<K, LinkedHashSet<Int>>, key: K, id: Int) {
        target.getOrPut(key) { LinkedHashSet() }.add(id)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SearchPageController::class.java)
        const val DATA_PATH = "/rest/searchPage"
        const val REQUEST_ATTRIBUTE = "org.airsonic.player.searchPageRequest"
        const val INITIAL_QUERY_ATTRIBUTE = "initialQuery"
        @JvmField val PAGE_PATHS = arrayOf("/search", "/search.view")
    }
}
