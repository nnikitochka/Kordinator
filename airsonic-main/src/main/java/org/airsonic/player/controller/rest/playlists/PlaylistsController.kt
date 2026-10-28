package org.airsonic.player.controller.rest.playlists

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.cover.CoverArtController
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.domain.Playlist as DomainPlaylist
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Playlist
import org.subsonic.restapi.Playlists
import org.subsonic.restapi.PlaylistWithSongs
import org.subsonic.restapi.Response
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class PlaylistsController(
    private val playlists: PlaylistService,
    private val mediaFiles: MediaFileService,
    private val security: SecurityService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> PlaylistsRequest) {
        with(root) {
            PATHS.forEach { path ->
                val operation = path.substringAfterLast('/').removeSuffix(".view")
                get(path) { respond(call, request(call), operation) }
                post(path) { respond(call, request(call), operation) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: PlaylistsRequest, operation: String) {
        val result = try {
            when (operation) {
                "getPlaylists" -> getPlaylists(request)
                "getPlaylist" -> getPlaylist(request)
                "createPlaylist" -> createPlaylist(request)
                "updatePlaylist" -> updatePlaylist(request)
                "deletePlaylist" -> deletePlaylist(request)
                else -> error("Unknown playlist operation")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in playlists API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: e.javaClass.simpleName)
        }
        val serialized = writer.serializeForType(
            request.parameters["f"] ?: "xml", request.parameters["callback"], result
        )
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun getPlaylists(request: PlaylistsRequest): Response {
        val requested = request.parameters["username"]
        if (requested != null && !request.admin) {
            return errorResponse(ErrorCode.NOT_AUTHORIZED, "${request.username} is not authorized to get playlists for $requested")
        }
        val result = Playlists()
        playlists.getReadablePlaylistsForUser(requested ?: request.username).forEach {
            result.playlist.add(toRest(Playlist(), it))
        }
        return writer.createResponse(true).apply { this.playlists = result }
    }

    private fun getPlaylist(request: PlaylistsRequest): Response {
        val id = request.id ?: return missing("id")
        val playlist = playlists.getPlaylist(id) ?: return notFound(id)
        if (!playlists.isReadAllowed(playlist, request.username)) return denied(id)
        val result = toRest(PlaylistWithSongs(), playlist)
        playlists.getFilesInPlaylist(id).forEach {
            if (security.isFolderAccessAllowed(it, request.username)) {
                result.entry.add(mapper.child(requireNotNull(request.player), it, request.username))
            }
        }
        return writer.createResponse(true).apply { this.playlist = result }
    }

    private fun createPlaylist(request: PlaylistsRequest): Response {
        val id = request.id
        val name = request.parameters["name"]
        if (id == null && name == null) return errorResponse(ErrorCode.MISSING_PARAMETER, "Playlist ID or name must be specified.")
        val playlist = if (id != null) {
            val existing = playlists.getPlaylist(id) ?: return notFound(id)
            if (!playlists.isWriteAllowed(existing, request.username)) return denied(id)
            existing
        } else {
            val now = Instant.now()
            DomainPlaylist().apply {
                this.name = name
                created = now
                changed = now
                shared = false
                username = request.username
                playlists.createPlaylist(this)
            }
        }
        val ids = integers(request.parameters, "songId")
        val songs = ids.mapNotNull { mediaFiles.getMediaFile(it) }
        playlists.setFilesInPlaylist(playlist.id, songs)
        return writer.createResponse(true)
    }

    private fun updatePlaylist(request: PlaylistsRequest): Response {
        val id = request.id ?: return missing("playlistId")
        val original = playlists.getPlaylist(id) ?: return notFound(id)
        if (!playlists.isWriteAllowed(original, request.username)) return denied(id)
        val playlist = DomainPlaylist(original)
        request.parameters["name"]?.let { playlist.name = it }
        request.parameters["comment"]?.let { playlist.comment = it }
        request.parameters["public"]?.let {
            playlist.shared = it.equals("true", true) || it.equals("on", true) || it.equals("yes", true) || it == "1"
        }
        playlists.updatePlaylist(playlist)
        val songs = playlists.getFilesInPlaylist(id)
        var changed = false
        val indices = integers(request.parameters, "songIndexToRemove")
        indices.toSortedSet().toList().asReversed().forEach {
            songs.removeAt(it)
            changed = true
        }
        val additions = integers(request.parameters, "songIdToAdd")
        additions.forEach { songId ->
            mediaFiles.getMediaFile(songId)?.let {
                songs.add(it)
                changed = true
            }
        }
        if (changed) playlists.setFilesInPlaylist(id, songs)
        return writer.createResponse(true)
    }

    private fun deletePlaylist(request: PlaylistsRequest): Response {
        val id = request.id ?: return missing("id")
        val playlist = playlists.getPlaylist(id) ?: return notFound(id)
        if (!playlists.isWriteAllowed(playlist, request.username)) return denied(id)
        playlists.deletePlaylist(id)
        return writer.createResponse(true)
    }

    private fun integers(parameters: Parameters, name: String): List<Int> {
        return parameters.getAll(name)?.map { it.toIntOrNull() ?: return emptyList() } ?: emptyList()
    }

    private fun <T : Playlist> toRest(target: T, playlist: DomainPlaylist): T = target.apply {
        id = playlist.id.toString()
        name = playlist.name
        comment = playlist.comment
        owner = playlist.username
        publicValue = playlist.shared
        songCount = playlist.fileCount
        duration = Math.round(playlist.duration).toInt()
        created = writer.convertDate(playlist.created)
        changed = writer.convertDate(playlist.changed)
        coverArt = CoverArtController.PLAYLIST_COVERART_PREFIX + playlist.id
        allowedUser.addAll(playlists.getPlaylistUsers(playlist.id))
    }

    private fun missing(name: String) = errorResponse(ErrorCode.MISSING_PARAMETER, "Required param ($name) is missing")
    private fun notFound(id: Int) = errorResponse(ErrorCode.NOT_FOUND, "Playlist not found: $id")
    private fun denied(id: Int) = errorResponse(ErrorCode.NOT_AUTHORIZED, "Permission denied for playlist $id")
    private fun errorResponse(code: ErrorCode, message: String) = writer.createErrorResponse(code, message)

    companion object {
        private val LOG = LoggerFactory.getLogger(PlaylistsController::class.java)
        @JvmField
        val PATHS = arrayOf(
            "/rest/getPlaylists", "/rest/getPlaylists.view",
            "/rest/getPlaylist", "/rest/getPlaylist.view",
            "/rest/createPlaylist", "/rest/createPlaylist.view",
            "/rest/updatePlaylist", "/rest/updatePlaylist.view",
            "/rest/deletePlaylist", "/rest/deletePlaylist.view",
        )
    }
}
