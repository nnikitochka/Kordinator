package org.airsonic.player.controller.rest.stream

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import org.airsonic.player.service.MediaStreamService
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.User
import org.springframework.security.core.Authentication
import java.util.Optional
import java.util.function.BiPredicate
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class StreamRestController(private val streams: MediaStreamService) {
    data class Request(val authentication: Authentication, val player: Player, val user: User,
                       val format: String?, val suffix: String?, val bitRate: Int?, val id: Int?,
                       val path: String?, val offset: Double?, val video: MediaStreamService.VideoOptions,
                       val icyMetadata: String?, val notModified: BiPredicate<String?, Long>, val conditionalStatus: () -> Int)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            WEBSITE_PATHS.forEach { path ->
                for (route in listOf(path, "$path/{tail...}")) {
                    get(route) { respond(call, request(call), false) }
                    head(route) { respond(call, request(call), true) }
                }
            }
            PATHS.forEach { path ->
                get(path) { respond(call, request(call), false) }
                post(path) { respond(call, request(call), false) }
                head(path) { respond(call, request(call), true) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request, head: Boolean) {
        val result = try {
            streams.open(request.authentication, request.player, request.user, request.format, request.suffix,
                Optional.ofNullable(request.bitRate), Optional.ofNullable(request.id), Optional.ofNullable(request.path),
                request.offset, request.video, request.icyMetadata, request.notModified)
        } catch (_: NumberFormatException) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        if (result == null) {
            call.respond(HttpStatusCode.fromValue(request.conditionalStatus()))
            return
        }
        val contentType = ContentType.parse(result.contentType)
        result.headers.forEach { name, values ->
            if (!name.equals(HttpHeaders.ContentType, true) && !name.equals(HttpHeaders.ContentLength, true))
                values.forEach { call.response.header(name, it) }
        }
        val size = result.length
        val rangeHeader = call.request.header(HttpHeaders.Range)
        val ranges = if (size != null && rangeHeader != null) try {
            parseRanges(rangeHeader, size)
        } catch (_: IllegalArgumentException) {
            result.stream.close()
            call.response.header(HttpHeaders.ContentRange, "bytes */$size")
            call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
            return
        } else null
        if (size != null) call.response.header(HttpHeaders.AcceptRanges, "bytes")
        val single = ranges?.singleOrNull()
        if (single != null) call.response.header(HttpHeaders.ContentRange, "bytes ${single.position}-${single.position + single.count - 1}/$size")
        val status = if (!ranges.isNullOrEmpty()) HttpStatusCode.PartialContent else HttpStatusCode.OK
        if (head) {
            result.stream.close()
            call.respond(object : OutgoingContent.NoContent() {
                override val status = status
                override val contentType = contentType
                override val contentLength = single?.count ?: if (ranges.isNullOrEmpty()) size else null
            })
            return
        }
        val boundary = if (ranges != null && ranges.size > 1) UUID.randomUUID().toString() else null
        val responseType = if (boundary == null) contentType else ContentType.parse("multipart/byteranges; boundary=$boundary")
        call.respondOutputStream(responseType, status, single?.count ?: if (ranges.isNullOrEmpty()) size else null) {
            result.stream.use { input ->
                if (ranges.isNullOrEmpty()) input.copyTo(this)
                else if (single != null) copyRange(input, this, single.position, single.count)
                else {
                    var position = 0L
                    for (range in ranges) {
                        // The underlying stream can only be read once, as in Spring's resource converter.
                        check(range.position >= position) { "Cannot reopen a streaming resource" }
                        write("\r\n--$boundary\r\nContent-Type: $contentType\r\nContent-Range: bytes ${range.position}-${range.position + range.count - 1}/$size\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        copyRange(input, this, range.position - position, range.count)
                        position = range.position + range.count
                    }
                    write("\r\n--$boundary--\r\n".toByteArray(Charsets.US_ASCII))
                }
            }
        }
    }

    private data class ByteRegion(val position: Long, val count: Long)

    private fun parseRanges(header: String, size: Long): List<ByteRegion> {
        if (header.isEmpty()) return emptyList()
        require(header.startsWith("bytes="))
        val parts = header.substring(6).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        require(parts.size <= 100)
        val regions = parts.map { part ->
            require(size > 0)
            val dash = part.indexOf('-')
            require(dash >= 0)
            val start: Long
            val end: Long
            if (dash == 0) {
                val suffix = part.substring(1).toLong()
                require(suffix >= 0)
                start = if (suffix < size) size - suffix else 0
                end = size - 1
            } else {
                start = part.substring(0, dash).toLong()
                require(start >= 0)
                val requestedEnd = part.substring(dash + 1).takeIf { it.isNotEmpty() }?.toLong()
                require(requestedEnd == null || requestedEnd >= start)
                end = if (requestedEnd != null && requestedEnd < size) requestedEnd else size - 1
            }
            require(start < size)
            ByteRegion(start, end - start + 1)
        }
        if (regions.size > 1) require(regions.sumOf { it.count } < size)
        return regions
    }

    private fun copyRange(input: InputStream, output: OutputStream, skip: Long, count: Long) {
        var remaining = skip
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) remaining -= skipped else if (input.read() < 0) return else remaining--
        }
        remaining = count
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (n < 0) break
            output.write(buffer, 0, n)
            remaining -= n
        }
    }

    companion object {
        @JvmField val WEBSITE_PATHS = arrayOf("/stream", "/ext/stream")
        @JvmField val PATHS = arrayOf("/rest/stream", "/rest/stream.view")
    }
}
