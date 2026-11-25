package su.nnedition.kord.api.module.video.caption

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.captions.CaptionInfo
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.service.CaptionService
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.media.MediaFolderService
import su.nnedition.kord.api.ApiError

fun Route.videoCaptionRoutes(
    captions: CaptionService,
    mediaFiles: MediaFileService,
    mediaFolders: MediaFolderService,
) {
    val HTTP_DATE: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)

    val json = jacksonObjectMapper()

    suspend fun accessibleVideo(call: ApplicationCall): MediaFile? {
        val user = call.principal<LocalUserPrincipal>()
        if (user == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return null
        }
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null || id <= 0) {
            call.respondText(
                json.writeValueAsString(ApiError("invalid_request", "Video ID must be a positive number")),
                ContentType.Application.Json,
                HttpStatusCode.BadRequest,
            )
            return null
        }
        val video = withContext(Dispatchers.IO) { mediaFiles.getMediaFile(id) }
        if (video == null || video.isDirectory || video.mediaType != MediaFile.MediaType.VIDEO) {
            call.respond(HttpStatusCode.NotFound)
            return null
        }
        val folderId = video.folderId?.toLong()
        val scope = if (user.role == "ADMIN") null else user.allowedLibraryIds
        val isReadableFolder = folderId != null && (scope == null || folderId in scope) &&
        withContext(Dispatchers.IO) {
            mediaFolders.getAllMusicFolders(false, false).any { it.id?.toLong() == folderId }
        }
        if (!isReadableFolder) {
            call.respond(HttpStatusCode.NotFound)
            return null
        }
        return video
    }

    fun captionToken(identifier: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(identifier.toByteArray(Charsets.UTF_8)),
    )

    fun parseHttpDate(value: String): Instant? = runCatching {
        Instant.from(HTTP_DATE.parse(value))
    }.getOrNull()

    suspend fun list(call: ApplicationCall) {
        val video = accessibleVideo(call) ?: return
        val available = withContext(Dispatchers.IO) { captions.listCaptions(video) }
        call.response.header(HttpHeaders.CacheControl, "no-store")
        val id = requireNotNull(video.id)
        val response = available.map { caption ->
            val token = captionToken(caption.identifier)
            VideoCaptionResponse(
                id = token,
                location = caption.location.name.lowercase(),
                format = caption.format,
                language = caption.language,
                href = "/api/v1/videos/$id/captions/$token",
            )
        }
        call.respondText(json.writeValueAsString(response), ContentType.Application.Json)
    }

    suspend fun read(call: ApplicationCall, head: Boolean) {
        val video = accessibleVideo(call) ?: return
        val token = call.parameters["captionId"] ?: return call.respond(HttpStatusCode.NotFound)
        val available = withContext(Dispatchers.IO) { captions.listCaptions(video) }
        val selected = available.firstOrNull { captionToken(it.identifier) == token }
            ?: return call.respond(HttpStatusCode.NotFound)
        val format = (call.request.queryParameters["format"] ?: selected.format).lowercase()
        val external = selected.location == CaptionInfo.Location.EXTERNAL
        if ((format !in setOf("vtt", "srt") && format != selected.format.lowercase()) ||
            (external && format != selected.format.lowercase() && !(selected.format.equals("srt", true) && format == "vtt"))
        ) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val folderId = requireNotNull(video.folderId)
        val folder = withContext(Dispatchers.IO) { mediaFolders.getMusicFolderById(folderId) }
            ?: return call.respond(HttpStatusCode.NotFound)
        val root = withContext(Dispatchers.IO) { folder.path.toRealPath() }
        val candidate = if (external) Path.of(selected.identifier) else video.getFullPath(folder.path)
        val input = withContext(Dispatchers.IO) {
            runCatching { candidate.toRealPath() }.getOrNull()?.takeIf { it.startsWith(root) && Files.isRegularFile(it) }
        } ?: return call.respond(HttpStatusCode.NotFound)

        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        val modified = withContext(Dispatchers.IO) { Files.getLastModifiedTime(input).toInstant() }
        call.response.header(HttpHeaders.LastModified, HTTP_DATE.format(modified.atZone(ZoneOffset.UTC)))
        val since = call.request.headers[HttpHeaders.IfModifiedSince]?.let { parseHttpDate(it) }
        if (since != null && modified.epochSecond <= since.epochSecond) {
            call.respond(HttpStatusCode.NotModified)
            return
        }

        val type = ContentType.parse(if (format == "vtt") "text/vtt; charset=UTF-8" else "text/plain; charset=UTF-8")
        if (head) {
            call.respondText("", type)
        } else {
            call.respondOutputStream(type) {
                val stream = if (external && format == selected.format.lowercase()) captions.openExternal(input, format)
                else captions.openConverted(input, if (external) "0" else selected.identifier, format)
                stream.use { it.copyTo(this) }
            }
        }
    }

    get("/videos/{id}/captions") { list(call) }
    get("/videos/{id}/captions/{captionId}") { read(call, false) }
    head("/videos/{id}/captions/{captionId}") { read(call, true) }
}
