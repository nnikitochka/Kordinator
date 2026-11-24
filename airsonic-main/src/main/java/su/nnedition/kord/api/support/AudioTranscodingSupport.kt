package su.nnedition.kord.api.support

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.writeFully
import java.io.InputStream
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.StandaloneAudioTranscodingService
import su.nnedition.kord.api.ApiError

internal suspend fun respondTranscodedAudio(
    call: ApplicationCall,
    trackId: Long,
    libraryId: Long,
    relativePath: String,
    head: Boolean,
    service: StandaloneAudioTranscodingService?,
    mediaFiles: MediaFileService?,
) {
    val query = call.request.queryParameters
    val format = query["format"] ?: "mp3"
    val rawBitrate = query["bitrateKbps"]
    val bitrate = if (rawBitrate == null) 192 else rawBitrate.toIntOrNull()
    val rawStart = query["startSeconds"]
    val start = if (rawStart == null) 0.0 else rawStart.toDoubleOrNull()
    if (format.isBlank() || bitrate == null || bitrate !in 32..320 || start == null || !start.isFinite() || start < 0) {
        call.respondJson(
            ApiError(
                "invalid_request",
                "Expected an audio format, bitrateKbps between 32 and 320, and finite non-negative startSeconds"
            ), HttpStatusCode.BadRequest)
        return
    }
    if (call.request.headers[HttpHeaders.Range] != null) {
        call.respondJson(ApiError("invalid_request", "Byte ranges are unavailable for transcoded audio"), HttpStatusCode.BadRequest)
        return
    }
    if (service == null || mediaFiles == null) {
        call.respondJson(ApiError("unavailable", "Audio transcoding is not configured"), HttpStatusCode.ServiceUnavailable)
        return
    }
    val file = withContext(Dispatchers.IO) { trackId.toInt().let(mediaFiles::getMediaFile) }
    if (file == null || !file.isPresent || file.folderId!!.toLong() != libraryId || file.path != relativePath) {
        call.respond(HttpStatusCode.NotFound)
        return
    }
    val plan = try {
        withContext(Dispatchers.IO) { service.prepare(file, format, bitrate, start) }
    } catch (failure: IllegalArgumentException) {
        call.respondJson(ApiError("invalid_request", failure.message ?: "Invalid audio parameters"), HttpStatusCode.BadRequest)
        return
    }
    if (plan == null) {
        call.respondJson(
            ApiError(
                "unsupported_format",
                "No configured audio profile supports this conversion and start position"
            ), HttpStatusCode.NotAcceptable)
        return
    }
    call.response.headers.append(HttpHeaders.CacheControl, "private, no-store")
    call.response.headers.append(HttpHeaders.AcceptRanges, "none")
    val contentType = ContentType.parse(plan.contentType)
    if (head) {
        call.respond(object : OutgoingContent.NoContent() {
            override val contentType = contentType
            override val status = HttpStatusCode.OK
        })
        return
    }
    var opened: InputStream? = null
    val input = try {
        withContext(Dispatchers.IO) { service.open(plan).also { opened = it } }
    } catch (failure: java.io.IOException) {
        opened?.close()
        call.respondJson(ApiError("unavailable", "Unable to start audio transcoder"), HttpStatusCode.ServiceUnavailable)
        return
    } catch (failure: Throwable) {
        opened?.close()
        throw failure
    }
    val buffer = ByteArray(64 * 1024)
    val firstCount = try {
        readAudioStart(input, buffer)
    } catch (failure: java.io.IOException) {
        input.close()
        call.respondJson(ApiError("unavailable", "Audio transcoder failed before producing audio"), HttpStatusCode.ServiceUnavailable)
        return
    } catch (failure: Throwable) {
        input.close()
        throw failure
    }
    if (firstCount < 1) {
        input.close()
        call.respondJson(ApiError("unavailable", "Audio transcoder produced no audio"), HttpStatusCode.ServiceUnavailable)
        return
    }
    try {
        call.respondBytesWriter(contentType = contentType) {
            coroutineScope {
                // Closing the process stream unblocks a read when the HTTP request is cancelled.
                val closer = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { input.close() }
                }
                try {
                    input.use {
                        writeFully(buffer, 0, firstCount)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val count = withContext(Dispatchers.IO) { it.read(buffer) }
                            if (count < 0) break
                            writeFully(buffer, 0, count)
                        }
                    }
                } finally {
                    closer.cancel()
                }
            }
        }
    } catch (failure: Throwable) {
        input.close()
        throw failure
    }
}

/** Waits for the first audio bytes while retaining ownership until the body writer takes over. */

internal suspend fun readAudioStart(input: InputStream, buffer: ByteArray): Int = coroutineScope {
    var ready = false
    val closer = launch(start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { if (!ready) input.close() }
    }
    val timeout = launch {
        kotlinx.coroutines.delay(30_000)
        input.close()
    }
    try {
        val count = withContext(Dispatchers.IO) { input.read(buffer) }
        ready = count > 0
        count
    } finally {
        timeout.cancel()
        closer.cancel()
    }
}
