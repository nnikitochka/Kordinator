package org.airsonic.player.ktor

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.service.StandaloneLibraryScanService
import org.airsonic.player.service.StandaloneAudioTranscodingService
import org.airsonic.player.service.StandaloneShareService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.KtorCoreMediaCatalogService
import su.nnedition.kord.api.authDispatcher
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.scanDispatcher
import java.nio.file.Files
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.format.DateTimeFormatter

internal suspend fun respondPublicShareTrack(
    call: ApplicationCall,
    shareService: StandaloneShareService?,
    scanService: StandaloneLibraryScanService?,
    catalogService: KtorCoreMediaCatalogService?,
    head: Boolean,
) {
    val service = shareService ?: return call.respondJson(ApiError("unavailable", "Share storage is not configured"), HttpStatusCode.ServiceUnavailable)
    val token = call.parameters["token"] ?: return call.respond(HttpStatusCode.NotFound)
    val trackId = call.parameters["trackId"]?.toLongOrNull()?.takeIf { it >= 0 }
        ?: return call.respondJson(ApiError("invalid_request", "Track ID must be non-negative"), HttpStatusCode.BadRequest)
    val share = withContext(authDispatcher) { service.publicShare(token) }
        ?: return call.respond(HttpStatusCode.NotFound)
    if (trackId !in share.trackIds) return call.respond(HttpStatusCode.NotFound)
    respondTrackFile(call, scanService, catalogService, false, head, trackId, publicAccess = true)
}

internal suspend fun respondTrackFile(
    call: ApplicationCall,
    scanService: StandaloneLibraryScanService?,
    catalogService: KtorCoreMediaCatalogService?,
    download: Boolean,
    head: Boolean,
    trackIdOverride: Long? = null,
    publicAccess: Boolean = false,
    audioTranscoding: StandaloneAudioTranscodingService? = null,
    mediaFiles: MediaFileService? = null,
) {
    if (scanService == null || catalogService == null) {
        call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
        return
    }
    val trackId = trackIdOverride ?: call.parameters["id"]?.toLongOrNull()
    if (trackId == null || trackId < 0) {
        call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
        return
    }
    val catalogTrack = withContext(scanDispatcher) {
        catalogService.track(trackId, if (publicAccess) null else call.catalogLibraryScope())
    }
    if (catalogTrack == null) {
        call.respond(HttpStatusCode.NotFound)
        return
    }
    val track = withContext(scanDispatcher) { scanService.streamFile(catalogTrack.libraryId, catalogTrack.relativePath) }
    if (track == null) {
        call.respond(HttpStatusCode.NotFound)
        return
    }
    if (!download && !publicAccess &&
        (call.request.queryParameters.contains("format") || call.request.queryParameters.contains("bitrateKbps") || call.request.queryParameters.contains("startSeconds"))) {
        respondTranscodedAudio(call, trackId, catalogTrack.libraryId, catalogTrack.relativePath,
            head, audioTranscoding, mediaFiles)
        return
    }
    if (download) {
        val fileName = track.path.fileName.toString()
            .map { if (it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '-' || it == '_') it else '_' }
            .joinToString("").ifBlank { "track" }
        call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"$fileName\"")
    }
    val modifiedAt = runCatching { Files.getLastModifiedTime(track.path).toInstant() }.getOrNull()
    val etag = "W/\"$trackId-${track.sizeBytes}-${modifiedAt?.toEpochMilli() ?: 0}\""
    call.setRepresentationValidators(etag, modifiedAt)
    if (call.isNotModified(etag, modifiedAt)) {
        call.respond(HttpStatusCode.NotModified)
        return
    }
    call.response.headers.append(HttpHeaders.AcceptRanges, "bytes")
    val rangeHeader = call.request.headers[HttpHeaders.Range]?.takeIf {
        val condition = call.request.headers[HttpHeaders.IfRange]
        condition == null || (modifiedAt != null && runCatching {
            val date = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(condition))
            modifiedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS) == date
        }.getOrDefault(false))
    }
    val range = if (rangeHeader == null) ByteRange(0, track.sizeBytes - 1) else parseByteRange(rangeHeader, track.sizeBytes)
    if (range == null) {
        call.response.headers.append(HttpHeaders.ContentRange, "bytes */${track.sizeBytes}")
        call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
        return
    }
    val partial = rangeHeader != null
    if (partial) call.response.headers.append(HttpHeaders.ContentRange, "bytes ${range.start}-${range.end}/${track.sizeBytes}")
    val length = if (track.sizeBytes == 0L && !partial) 0L else range.end - range.start + 1
    val contentType = ContentType.parse(track.contentType)
    val status = if (partial) HttpStatusCode.PartialContent else HttpStatusCode.OK
    if (head) {
        call.respond(object : OutgoingContent.NoContent() {
            override val status = status
            override val contentType = contentType
            override val contentLength = length
        })
        return
    }
    call.respondBytesWriter(contentType = contentType, status = status, contentLength = length) {
        FileChannel.open(track.path, StandardOpenOption.READ).use { file ->
            file.position(range.start)
            val buffer = ByteBuffer.allocate(64 * 1024)
            var remaining = length
            while (remaining > 0) {
                buffer.clear()
                buffer.limit(minOf(buffer.capacity().toLong(), remaining).toInt())
                val read = withContext(Dispatchers.IO) { file.read(buffer) }
                if (read <= 0) break
                writeFully(buffer.array(), 0, read)
                remaining -= read
            }
        }
    }
}

