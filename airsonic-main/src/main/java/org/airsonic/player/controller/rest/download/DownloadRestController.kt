package org.airsonic.player.controller.rest.download

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.User
import org.airsonic.player.service.DownloadService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.util.StringUtil
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory
import java.util.Optional
import java.util.function.BiPredicate
import kotlin.coroutines.cancellation.CancellationException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class DownloadRestController(
    private val writer: JAXBWriter,
    private val downloads: DownloadService,
    private val media: MediaFileService,
    private val playlists: PlaylistService,
    private val security: SecurityService,
) {
    data class WebsiteRequest(val user: User, val player: Player?, val parameters: Parameters,
                              val notModified: BiPredicate<String?, Long>, val conditionalStatus: () -> Int)

    fun routes(root: Route, request: (ApplicationCall) -> DownloadRequest, websiteRequest: (ApplicationCall) -> WebsiteRequest) {
        with(root) {
            get(WEBSITE_PATH) { respondWebsite(call, websiteRequest(call), false) }
            head(WEBSITE_PATH) { respondWebsite(call, websiteRequest(call), true) }
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
                head(path) { respond(call, request(call), true) }
            }
        }
    }

    private suspend fun respondWebsite(call: ApplicationCall, r: WebsiteRequest, head: Boolean) {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        val prepared = try {
            withContext(Dispatchers.IO) { prepareWebsite(r) }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            LOG.warn("Website download failed", e)
            DownloadRequest(r.parameters, null, ErrorCode.GENERIC, "Download failed")
        }
        val download = prepared.download
        if (download != null && r.notModified.test(download.size.toString(), download.changed)) {
            call.respond(HttpStatusCode.fromValue(r.conditionalStatus()))
            return
        }
        respond(call, prepared, head)
    }

    private fun prepareWebsite(r: WebsiteRequest): DownloadRequest {
        val p = r.parameters
        fun fail(code: ErrorCode, message: String) = DownloadRequest(p, null, code, message)
        if (!r.user.isDownloadRole) return fail(ErrorCode.NOT_AUTHORIZED, "Downloading is not allowed")
        val sources = listOf("id", "playlist", "player").filter { p.contains(it) }
        if (sources.isEmpty()) return fail(ErrorCode.MISSING_PARAMETER, "A media, playlist or player selection is required")
        if (sources.size != 1) return fail(ErrorCode.GENERIC, "Specify one selection source")
        val source = sources.single()
        val id = p[source]?.toIntOrNull() ?: return fail(ErrorCode.GENERIC, "Invalid selection id")
        val player = r.player ?: return fail(ErrorCode.NOT_AUTHORIZED, "Player access denied")
        if (player.username != r.user.username) return fail(ErrorCode.NOT_AUTHORIZED, "Player access denied")
        val files = when (source) {
            "id" -> {
                val file = media.getMediaFile(id) ?: return fail(ErrorCode.NOT_FOUND, "Media not found")
                if (!security.isFolderAccessAllowed(file, r.user.username)) return fail(ErrorCode.NOT_AUTHORIZED, "Music folder access denied")
                if (file.isFile) listOf(file) else media.getChildrenOf(file, true, false, true)
            }
            "playlist" -> {
                val playlist = playlists.getPlaylist(id) ?: return fail(ErrorCode.NOT_FOUND, "Playlist not found")
                if (!playlists.isReadAllowed(playlist, r.user.username)) return fail(ErrorCode.NOT_AUTHORIZED, "Playlist access denied")
                playlists.getFilesInPlaylist(id)
            }
            else -> player.playQueue.files.toList()
        }
        val indices = p.getAll("i")?.flatMap { it.split(',') }?.map { raw ->
            raw.trim().toIntOrNull()?.takeIf { it in files.indices }
                ?: return fail(ErrorCode.GENERIC, "Invalid selection index")
        }
        val selected = indices?.map { files[it] } ?: files
        if (selected.any { !security.isFolderAccessAllowed(it, r.user.username) })
            return fail(ErrorCode.NOT_AUTHORIZED, "Music folder access denied")
        val download = downloads.prepare(r.user, player, Optional.ofNullable(if (source == "id") id else null),
            if (source == "playlist") id else null, if (source == "player") id else null, indices, files)
            ?: return fail(ErrorCode.NOT_FOUND, "Download not found")
        val name = download.filename.filter { !it.isISOControl() }
        val disposition = ContentDisposition.Attachment
            .withParameter(ContentDisposition.Parameters.FileName, name.map { if (it.code in 32..126) it else '_' }.joinToString(""))
            .withParameter(ContentDisposition.Parameters.FileNameAsterisk, name).toString()
        return DownloadRequest(p, download, contentDisposition = disposition,
            contentType = StringUtil.getMimeType(FilenameUtils.getExtension(name)))
    }

    private suspend fun respond(call: ApplicationCall, request: DownloadRequest, head: Boolean = false) {
        if (request.errorCode != null) {
            val error = writer.createErrorResponse(request.errorCode, request.errorMessage)
            val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], error)
            call.respondText(serialized.value, ContentType.parse(serialized.key))
            return
        }
        val download = request.download
        if (download == null) {
            call.respond(HttpStatusCode.OK)
            return
        }
        val responseType = ContentType.parse(requireNotNull(request.contentType))
        call.response.header(HttpHeaders.ContentDisposition, requireNotNull(request.contentDisposition))
        if (!download.hasContent()) {
            call.respondText("", responseType, HttpStatusCode.OK)
            return
        }
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        val rangeHeader = call.request.header(HttpHeaders.Range)
        val ranges = if (rangeHeader == null) null else parseRanges(rangeHeader, download.size)
        if (rangeHeader != null && ranges == null) {
            call.response.header(HttpHeaders.ContentRange, "bytes */${download.size}")
            call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
            return
        }
        if (head && (ranges == null || ranges.size == 1)) {
            val range = ranges?.single()
            if (range != null) call.response.header(HttpHeaders.ContentRange, "bytes ${range.start}-${range.end}/${download.size}")
            call.respond(object : OutgoingContent.NoContent() {
                override val status = if (range == null) HttpStatusCode.OK else HttpStatusCode.PartialContent
                override val contentType = responseType
                override val contentLength = range?.length ?: download.size
            })
            return
        }
        when {
            ranges == null -> call.respondOutputStream(responseType, HttpStatusCode.OK, download.size) {
                download.openStream().use { it.copyTo(this) }
            }
            ranges.size == 1 -> {
                val range = ranges.single()
                call.response.header(HttpHeaders.ContentRange, "bytes ${range.start}-${range.end}/${download.size}")
                call.respondOutputStream(responseType, HttpStatusCode.PartialContent, range.length) {
                    download.openStream().use { input -> copyRange(input, this, range) }
                }
            }
            else -> {
                val boundary = UUID.randomUUID().toString()
                val multipart = ContentType.parse("multipart/byteranges; boundary=$boundary")
                call.respondOutputStream(multipart, HttpStatusCode.PartialContent) {
                    for (range in ranges) {
                        write("--$boundary\r\nContent-Type: $responseType\r\nContent-Range: bytes ${range.start}-${range.end}/${download.size}\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        download.openStream().use { input -> copyRange(input, this, range) }
                        write("\r\n".toByteArray(Charsets.US_ASCII))
                    }
                    write("--$boundary--\r\n".toByteArray(Charsets.US_ASCII))
                }
            }
        }
    }

    private fun copyRange(input: InputStream, output: OutputStream, range: DownloadRange) {
        var skip = range.start
        while (skip > 0) {
            val n = input.skip(skip)
            if (n > 0) skip -= n else if (input.read() < 0) return else skip--
        }
        var remaining = range.length
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (n < 0) break
            output.write(buffer, 0, n)
            remaining -= n
        }
    }

    private fun parseRanges(header: String, size: Long): List<DownloadRange>? {
        if (!header.startsWith("bytes=") || size <= 0) return null
        val parts = header.substring(6).split(',')
        if (parts.isEmpty() || parts.size > 100) return null
        val result = mutableListOf<DownloadRange>()
        for (part in parts) {
            val pair = part.trim().split('-', limit = 2)
            if (pair.size != 2) return null
            val first = pair[0]
            val last = pair[1]
            val start: Long
            val end: Long
            if (first.isEmpty()) {
                val suffix = last.toLongOrNull() ?: return null
                if (suffix <= 0) return null
                start = (size - suffix).coerceAtLeast(0)
                end = size - 1
            } else {
                start = first.toLongOrNull() ?: return null
                if (start < 0 || start >= size) return null
                end = if (last.isEmpty()) size - 1 else (last.toLongOrNull() ?: return null).coerceAtMost(size - 1)
                if (end < start) return null
            }
            result.add(DownloadRange(start, end))
        }
        if (result.size > 1 && result.sumOf { it.length } >= size) return null
        return result
    }

    companion object {
        const val WEBSITE_PATH = "/rest/downloadMedia"
        private val LOG = LoggerFactory.getLogger(DownloadRestController::class.java)
        @JvmField val PATHS = arrayOf("/rest/download", "/rest/download.view")
    }
}
