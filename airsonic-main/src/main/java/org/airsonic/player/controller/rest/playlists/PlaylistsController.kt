package org.airsonic.player.controller.rest.playlists

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.ResourceBundle
import kotlin.coroutines.cancellation.CancellationException
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.service.CoverArtImageService
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.domain.Playlist as DomainPlaylist
import org.airsonic.player.i18n.LocaleService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.util.StringUtil
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Child
import org.subsonic.restapi.Playlist
import org.subsonic.restapi.PlaylistWithSongs
import org.subsonic.restapi.Playlists
import org.subsonic.restapi.Response

class PlaylistsController(
    private val playlists: PlaylistService,
    private val mediaFiles: MediaFileService,
    private val security: SecurityService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
    private val mediaFileDao: MediaFileDao,
    private val folders: MediaFolderService,
    private val players: PlayerService,
    private val locales: LocaleService,
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
            if (request.parameters["writableOnly"] != "true" || playlists.isWriteAllowed(it, request.username)) {
                result.playlist.add(toRest(Playlist(), it))
            }
        }
        return writer.createResponse(true).apply { this.playlists = result }
    }

    private fun getPlaylist(request: PlaylistsRequest): Response {
        val id = request.id ?: return missing("id")
        val playlist = playlists.getPlaylist(id) ?: return notFound(id)
        if (!playlists.isReadAllowed(playlist, request.username)) return denied(id)
        val result = toRest(PlaylistWithSongs(), playlist)
        val details = request.parameters["includeEntryDetails"] == "true"
        playlists.getFilesInPlaylist(id, details).forEachIndexed { index, file ->
            val allowed = security.isFolderAccessAllowed(file, request.callerContext)
            if (allowed || details) {
                // Keep every occurrence addressable without exposing restricted file metadata.
                val child = if (allowed) mapper.child(requireNotNull(request.player), file, request.username) else Child().apply {
                    this.id = file.id.toString()
                    title = ""
                }
                if (details) {
                    child.present = file.isPresent && allowed
                    child.playlistIndex = index
                    if (allowed) {
                        child.albumArtist = file.albumArtist
                        child.lastPlayed = writer.convertDate(file.lastPlayed)
                        child.lastScanned = writer.convertDate(file.lastScanned)
                        child.changed = writer.convertDate(file.changed)
                        child.formattedFileSize = StringUtil.formatBytes(file.fileSize, locales.resolveLocale(request.username))
                        child.formattedBitRate = file.bitRate?.let {
                            (if (file.isVariableBitRate) "vbr " else "") + "$it Kbps"
                        }
                    }
                }
                result.entry.add(child)
            }
        }
        return writer.createResponse(true).apply { this.playlist = result }
    }

    private fun createPlaylist(request: PlaylistsRequest): Response {
        val id = request.id
        val source = request.parameters["source"]
        if (source != null && (id != null || source !in setOf("empty", "starred", "playQueue"))) {
            return errorResponse(ErrorCode.GENERIC, "Invalid playlist source")
        }
        val sourceSongs = when (source) {
            "starred" -> mediaFileDao.getStarredFiles(0, Int.MAX_VALUE, request.username, folders.getMusicFoldersForUser(request.username))
            "playQueue" -> {
                val playerId = request.parameters["playerId"]?.toIntOrNull() ?: return missing("playerId")
                val player = players.getPlayerById(playerId) ?: return errorResponse(ErrorCode.NOT_FOUND, "Player not found")
                if (player.username != request.username) return errorResponse(ErrorCode.NOT_AUTHORIZED, "Permission denied for player")
                player.playQueue.files.toList()
            }
            else -> null
        }
        val name = request.parameters["name"] ?: source?.let {
            val locale = locales.resolveLocale(request.username)
            val date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale).format(Instant.now().atZone(ZoneId.systemDefault()))
            if (it == "starred") ResourceBundle.getBundle("org.airsonic.player.i18n.ResourceBundle", locale).getString("top.starred") + " " + date else date
        }
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
        val songs = sourceSongs ?: ids.mapNotNull { mediaFiles.getMediaFile(it) }
        playlists.setFilesInPlaylist(playlist.id, songs)
        return writer.createResponse(true).apply {
            if (request.parameters["includePlaylist"] == "true") {
                this.playlist = toRest(PlaylistWithSongs(), requireNotNull(this@PlaylistsController.playlists.getPlaylist(playlist.id)))
            }
        }
    }

    private fun updatePlaylist(request: PlaylistsRequest): Response {
        val id = request.id ?: return missing("playlistId")
        val original = playlists.getPlaylist(id) ?: return notFound(id)
        if (!playlists.isWriteAllowed(original, request.username)) return denied(id)
        // Validate ordering before changing metadata or files. Indices identify occurrences,
        // so repeated songs remain distinct and unavailable entries are preserved.
        val reordered = request.parameters.getAll("songIndexOrder")?.let { raw ->
            val files = playlists.getFilesInPlaylist(id, true)
            val order = raw.map { it.toIntOrNull() ?: return errorResponse(ErrorCode.GENERIC, "Invalid playlist order") }
            if (order.size != files.size || order.toSet() != files.indices.toSet()) {
                return errorResponse(ErrorCode.GENERIC, "Playlist order must be a complete permutation")
            }
            order.map { files[it] }
        }
        if (request.parameters["includeUnavailable"] == "true") {
            val size = playlists.getFilesInPlaylist(id, true).size
            val invalidIndex = request.parameters.getAll("songIndexToRemove")?.any { raw ->
                val index = raw.toIntOrNull()
                index == null || index !in 0 until size
            } == true
            if (invalidIndex) {
                return errorResponse(ErrorCode.GENERIC, "Invalid playlist index")
            }
        }
        val playlist = DomainPlaylist(original)
        request.parameters["name"]?.let { playlist.name = it }
        request.parameters["comment"]?.let { playlist.comment = it }
        request.parameters["public"]?.let {
            playlist.shared = it.equals("true", true) || it.equals("on", true) || it.equals("yes", true) || it == "1"
        }
        playlists.updatePlaylist(playlist)
        val songs = (reordered ?: playlists.getFilesInPlaylist(id, request.parameters["includeUnavailable"] == "true")).toMutableList()
        var changed = reordered != null
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
        coverArt = CoverArtImageService.PLAYLIST_COVERART_PREFIX + playlist.id
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
