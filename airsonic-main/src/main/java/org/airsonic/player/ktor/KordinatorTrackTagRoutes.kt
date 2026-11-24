package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.domain.User
import org.airsonic.player.security.CallerContext
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.TagEditService
import org.airsonic.player.service.TrackTagUpdate
import su.nnedition.kord.api.ApiError

class KordinatorTrackTagRoutes(
    private val tags: TagEditService,
    private val mediaFiles: MediaFileService,
    private val mediaFolders: MediaFolderService,
) {
    private val mapper = jacksonObjectMapper()

    fun routes(root: Route) {
        root.put("/tracks/{id}/tags") { update(call) }
    }

    private suspend fun update(call: ApplicationCall) {
        val user = call.principal<LocalUserPrincipal>()
            ?: return call.respond(HttpStatusCode.Unauthorized)
        if (user.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Only administrators can edit track tags"), HttpStatusCode.Forbidden)
            return
        }
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null || id <= 0) {
            call.respondJson(ApiError("invalid_request", "Track ID must be a positive number"), HttpStatusCode.BadRequest)
            return
        }
        val file = withContext(Dispatchers.IO) { mediaFiles.getMediaFile(id) }
            ?: return call.respond(HttpStatusCode.NotFound)
        if (!file.isFile) {
            call.respondJson(ApiError("invalid_track", "Tags can only be edited on files"), HttpStatusCode.BadRequest)
            return
        }
        val request = try {
            mapper.readValue(call.receiveText(), EditTrackTagsRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected artist, album, title, genre, track and year fields"), HttpStatusCode.BadRequest)
            return
        }
        if (request.track != null && request.track < 0 || request.year != null && request.year < 0) {
            call.respondJson(ApiError("invalid_request", "Track number and year must be non-negative"), HttpStatusCode.BadRequest)
            return
        }
        val folderIds = withContext(Dispatchers.IO) {
            mediaFolders.getAllMusicFolders(false, false).mapNotNull { it.id }.toSet()
        }
        val context = CallerContext(user.username, setOf(User.Role.ADMIN), folderIds, true)
        val result = withContext(Dispatchers.IO) {
            tags.setTags(
                TrackTagUpdate(id, request.track, request.artist, request.album, request.title, request.year, request.genre),
                context,
            )
        }
        if (result == "UPDATED" || result == "SKIPPED") {
            call.respondJson(EditTrackTagsResponse(result))
        } else {
            call.respondJson(ApiError("tag_edit_failed", result), HttpStatusCode.UnprocessableEntity)
        }
    }
}

data class EditTrackTagsRequest(
    val artist: String? = null,
    val album: String? = null,
    val title: String? = null,
    val genre: String? = null,
    val track: Int? = null,
    val year: Int? = null,
)

data class EditTrackTagsResponse(val result: String)
