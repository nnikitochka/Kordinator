package org.airsonic.player.controller.rest.tags

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
import org.airsonic.player.security.CallerContext
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.metadata.JaudiotaggerParser
import org.airsonic.player.service.metadata.MetaDataParser
import org.apache.commons.io.FilenameUtils

/** REST data for the embedded tag editor. */
class TagEditorPageController(
    private val mediaFiles: MediaFileService,
    private val folders: MediaFolderService,
    private val security: SecurityService,
    private val json: ObjectMapper,
) {
    data class Request(val username: String, val callerContext: CallerContext, val id: Int?, val allowed: Boolean)

    data class Song(
        val id: Int,
        val fileName: String,
        val track: Int?,
        val suggestedTrack: Int,
        val title: String?,
        val suggestedTitle: String,
        val artist: String?,
        val album: String?,
        val year: Int?,
        val genre: String?,
    )

    data class PageData(
        val id: Int,
        val defaultArtist: String?,
        val defaultAlbum: String?,
        val defaultYear: Int?,
        val defaultGenre: String?,
        val allGenres: List<String>,
        val songs: List<Song>,
    )

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get(PATH) { respond(call, request(call)) }
            head(PATH) { respond(call, request(call)) }
        }
    }

    fun page(username: String, callerContext: CallerContext, allowed: Boolean, id: Int): PageData {
        if (!allowed) throw SecurityException("Tag editing permission required")
        val directory = mediaFiles.getMediaFile(id) ?: throw NoSuchElementException("Media file not found")
        if (!directory.isDirectory) throw IllegalArgumentException("Tag editor requires a directory")
        if (!security.isFolderAccessAllowed(directory, callerContext) || folders.getMusicFoldersForUser(username).none { it.id == directory.folderId }) {
            throw SecurityException("Tag editing access denied")
        }
        val songs = mediaFiles.getChildrenOf(directory, true, false, true, false).mapIndexed { index, file ->
            Song(
                id = file.id,
                fileName = FilenameUtils.getBaseName(file.path),
                track = file.trackNumber,
                suggestedTrack = index + 1,
                title = file.title,
                suggestedTitle = MetaDataParser.guessTitle(file.relativePath),
                artist = file.artist,
                album = file.albumName,
                year = file.year,
                genre = file.genre,
            )
        }
        val first = songs.firstOrNull()
        return PageData(id, first?.artist, first?.album, first?.year, first?.genre,
            JaudiotaggerParser.getID3V1Genres().toList(), songs)
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        call.response.header("Cache-Control", "no-store")
        if (!request.allowed) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }
        val id = request.id
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val data = try {
            page(request.username, request.callerContext, request.allowed, id)
        } catch (_: NoSuchElementException) {
            call.respond(HttpStatusCode.NotFound)
            return
        } catch (_: SecurityException) {
            call.respond(HttpStatusCode.Forbidden)
            return
        } catch (_: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        call.respondText(json.writeValueAsString(data), ContentType.Application.Json)
    }

    companion object {
        const val PATH = "/rest/tagEditor"
        const val REQUEST_ATTRIBUTE = "org.airsonic.player.tagEditorPageRequest"
        @JvmField val PAGE_PATHS = arrayOf("/editTags", "/editTags.view")
    }
}
