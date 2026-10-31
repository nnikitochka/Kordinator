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
import org.airsonic.player.spring.KnownLengthInputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpRange
import org.springframework.http.ResponseEntity
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class StreamRestController {
    fun routes(root: Route, result: (ApplicationCall) -> ResponseEntity<Resource>) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, result(call), false) }
                post(path) { respond(call, result(call), false) }
                head(path) { respond(call, result(call), true) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, result: ResponseEntity<Resource>, head: Boolean) {
        val resource = requireNotNull(result.body)
        val contentType = ContentType.parse(requireNotNull(result.headers.contentType).toString())
        result.headers.forEach { name, values ->
            if (!name.equals(HttpHeaders.ContentType, true) && !name.equals(HttpHeaders.ContentLength, true))
                values.forEach { call.response.header(name, it) }
        }
        val size = (resource as? KnownLengthInputStreamResource)?.contentLength()
        val rangeHeader = call.request.header(HttpHeaders.Range)
        val ranges = if (size != null && rangeHeader != null) try {
            HttpRange.toResourceRegions(HttpRange.parseRanges(rangeHeader), resource)
        } catch (_: IllegalArgumentException) {
            resource.inputStream.close()
            call.response.header(HttpHeaders.ContentRange, "bytes */$size")
            call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
            return
        } else null
        if (size != null) call.response.header(HttpHeaders.AcceptRanges, "bytes")
        val single = ranges?.singleOrNull()
        if (single != null) call.response.header(HttpHeaders.ContentRange, "bytes ${single.position}-${single.position + single.count - 1}/$size")
        val status = if (!ranges.isNullOrEmpty()) HttpStatusCode.PartialContent else HttpStatusCode.fromValue(result.statusCode.value())
        if (head) {
            resource.inputStream.close()
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
            resource.inputStream.use { input ->
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
        @JvmField val PATHS = arrayOf("/rest/stream", "/rest/stream.view")
    }
}
