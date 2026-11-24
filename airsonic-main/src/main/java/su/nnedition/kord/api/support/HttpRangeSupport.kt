package su.nnedition.kord.api.support

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import su.nnedition.kord.api.module.metadata.ByteRange

internal fun ApplicationCall.setRepresentationValidators(etag: String, modifiedAt: Instant?) {
    response.headers.append(HttpHeaders.ETag, etag)
    modifiedAt?.let {
        response.headers.append(
            HttpHeaders.LastModified,
            DateTimeFormatter.RFC_1123_DATE_TIME.format(it.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).atZone(ZoneOffset.UTC)),
        )
    }
}

internal fun ApplicationCall.isNotModified(etag: String, modifiedAt: Instant?): Boolean {
    val entityTags = request.headers[HttpHeaders.IfNoneMatch]
    if (entityTags != null) {
        val current = etag.removePrefix("W/").removeSurrounding("\"")
        return entityTags.split(',').any { candidate ->
            val normalized = candidate.trim()
            normalized == "*" || normalized.removePrefix("W/").removeSurrounding("\"") == current
        }
    }
    if (modifiedAt == null) return false
    val ifModifiedSince = request.headers[HttpHeaders.IfModifiedSince] ?: return false
    val since = runCatching {
        Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(ifModifiedSince))
    }.getOrNull() ?: return false
    return !modifiedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).isAfter(since)
}

internal fun parseByteRange(header: String, size: Long): ByteRange? {
    if (size <= 0 || !header.startsWith("bytes=") || header.contains(',')) return null
    val value = header.removePrefix("bytes=").trim()
    val separator = value.indexOf('-')
    if (separator < 0 || separator != value.lastIndexOf('-')) return null
    val first = value.substring(0, separator)
    val last = value.substring(separator + 1)
    return when {
        first.isEmpty() -> {
            val suffixLength = last.toLongOrNull()?.takeIf { it > 0 } ?: return null
            ByteRange((size - suffixLength).coerceAtLeast(0), size - 1)
        }
        else -> {
            val start = first.toLongOrNull()?.takeIf { it >= 0 && it < size } ?: return null
            val end = if (last.isEmpty()) size - 1 else last.toLongOrNull()?.takeIf { it >= start }?.coerceAtMost(size - 1) ?: return null
            ByteRange(start, end)
        }
    }
}
