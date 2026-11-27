package su.nnedition.kord.api.module.track.tag

import su.nnedition.kord.model.ReleaseDate

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.nnedition.kord.domain.User
import su.nnedition.kord.security.CallerContext
import su.nnedition.kord.security.LocalUserPrincipal
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.TagEditService
import su.nnedition.kord.service.TrackTagUpdate
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.support.respondJson

fun Route.trackTagRoutes(
    tags: TagEditService,
    mediaFiles: MediaFileService,
    mediaFolders: MediaFolderService,
) {
    val mapper = jacksonObjectMapper()

    suspend fun update(call: ApplicationCall) {
        val user = call.principal<LocalUserPrincipal>()
            ?: return call.respond(HttpStatusCode.Unauthorized)
        if (user.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Only administrators can edit track tags"), HttpStatusCode.Forbidden)
            return
        }
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null || id < 0) {
            call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
            return
        }
        val file = withContext(Dispatchers.IO) { mediaFiles.getMediaFile(id) }
            ?: return call.respond(HttpStatusCode.NotFound)
        if (!file.isFile) {
            call.respondJson(ApiError("invalid_track", "Tags can only be edited on files"), HttpStatusCode.BadRequest)
            return
        }
        val body = try {
            mapper.readTree(call.receiveText()).also { require(it.isObject) }
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected a JSON object"), HttpStatusCode.BadRequest)
            return
        }
        val request = try {
            mapper.treeToValue(body, EditTrackTagsRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Invalid track tags"), HttpStatusCode.BadRequest)
            return
        }
        val hasDate = body.has("releaseDate")
        val hasYear = body.has("year")
        val date = ReleaseDate.parse(request.releaseDate)
        if (hasDate && (hasYear || !body["releaseDate"].isNull &&
                (!body["releaseDate"].isTextual || date == null)) ||
            hasYear && !body["year"].isNull &&
                (!body["year"].isIntegralNumber || request.year !in 1..9999) ||
            request.track != null && request.track < 0
        ) {
            call.respondJson(ApiError("invalid_request", "Invalid releaseDate, year, or track number; supply only one date field"), HttpStatusCode.BadRequest)
            return
        }
        val folderIds = withContext(Dispatchers.IO) {
            mediaFolders.getAllMusicFolders(false, false).mapNotNull { it.id }.toSet()
        }
        val context = CallerContext(user.username, setOf(User.Role.ADMIN), folderIds, true)
        val result = withContext(Dispatchers.IO) {
            tags.setTags(
                TrackTagUpdate(
                    id, request.track, request.artist, request.album, request.title, request.year, request.genre,
                    if (hasDate) date?.toString() else request.year?.toString(), hasDate || hasYear,
                    body.fieldNames().asSequence().toSet(),
                ),
                context,
            )
        }
        if (result == "UPDATED" || result == "SKIPPED") {
            call.respondJson(EditTrackTagsResponse(result))
        } else {
            call.respondJson(ApiError("tag_edit_failed", result), HttpStatusCode.UnprocessableEntity)
        }
    }

    put("/tracks/{id}/tags") { update(call) }
}
