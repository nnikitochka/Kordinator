package org.airsonic.player.controller.captions

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.CaptionService
import org.airsonic.player.service.MediaFolderService
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The website and signed public links share the same subtitle implementation. */
class CaptionsRestController(
    private val captions: CaptionService,
    private val folders: MediaFolderService,
    private val json: ObjectMapper,
) {
    data class Request(val file: MediaFile, val baseUrl: String, val externalUser: String?,
                       val expiration: Instant?, val captionId: String?, val format: String?)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call), false) }
                head(path) { respond(call, request(call), true) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request, head: Boolean) {
        withContext(Dispatchers.IO) {
            val available = captions.listCaptions(request.file, request.baseUrl, request.externalUser, request.expiration)
            call.response.header("Cache-Control", "no-store")
            if (call.request.path().endsWith("/list")) {
                call.respondText(json.writeValueAsString(available), ContentType.Application.Json)
                return@withContext
            }
            val selected = if (request.captionId == null) available.firstOrNull()
                else available.firstOrNull { it.identifier.equals(request.captionId, true) }
            if (selected == null) {
                call.respond(HttpStatusCode.NotFound)
                return@withContext
            }
            val format = (request.format ?: selected.format).lowercase()
            val external = selected.location == CaptionInfo.Location.EXTERNAL
            if ((format !in setOf("vtt", "srt") && format != selected.format) || (external && format != selected.format &&
                        !(selected.format == "srt" && format == "vtt"))) {
                call.respond(HttpStatusCode.NotFound)
                return@withContext
            }
            val input = if (external) Path.of(selected.identifier)
                else request.file.getFullPath(folders.getMusicFolderById(request.file.folderId).path)
            if (!Files.isRegularFile(input)) {
                call.respond(HttpStatusCode.NotFound)
                return@withContext
            }
            call.response.header("Access-Control-Allow-Origin", "*")
            val modified = Files.getLastModifiedTime(input).toInstant()
            call.response.header("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME.format(modified.atZone(ZoneOffset.UTC)))
            val since = call.request.headers["If-Modified-Since"]?.let {
                runCatching { java.time.ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
            }
            if (since != null && modified.epochSecond <= since.epochSecond) {
                call.respond(HttpStatusCode.NotModified)
                return@withContext
            }
            val type = ContentType.parse(if (format == "vtt") "text/vtt; charset=UTF-8" else "text/plain; charset=UTF-8")
            if (head) {
                call.respondText("", type)
            } else {
                call.respondOutputStream(type) {
                    val stream = if (external && format == selected.format) captions.openExternal(input, format)
                        else captions.openConverted(input, if (external) "0" else selected.identifier, format)
                    stream.use { it.copyTo(this) }
                }
            }
        }
    }

    companion object {
        @JvmField val PATHS = arrayOf("/rest/captions", "/rest/captions/list", "/ext/rest/captions", "/ext/rest/captions/list",
            "/captions", "/captions/list", "/ext/captions", "/ext/captions/list")
    }
}
