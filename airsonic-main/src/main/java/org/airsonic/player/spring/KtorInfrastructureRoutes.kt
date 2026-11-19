package org.airsonic.player.spring

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.http.parseAndSortContentTypeHeader
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.route
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.ServletContext
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.function.Supplier

/** Servlet-backed static files and the shared error endpoint used during the servlet migration. */
internal fun Route.infrastructureRoutes(errorProperties: Supplier<InfrastructureErrorSettings>, objectMapper: ObjectMapper) {
    for (prefix in listOf("script", "style", "icons")) {
        route("/$prefix/{asset...}") {
            get { serveStatic(call, "/$prefix/") }
            head { serveStatic(call, "/$prefix/") }
            handle { call.response.headers.append(HttpHeaders.Allow, "GET, HEAD"); call.respond(HttpStatusCode.MethodNotAllowed) }
        }
    }
    route("/") {
        get { serveStatic(call, "/", "/index.html") }
        head { serveStatic(call, "/", "/index.html") }
        handle { call.response.headers.append(HttpHeaders.Allow, "GET, HEAD"); call.respond(HttpStatusCode.MethodNotAllowed) }
    }
    for (name in listOf("favicon.ico", "browserconfig.xml", "index.html")) {
        route("/$name") {
            get { serveStatic(call, "/") }
            head { serveStatic(call, "/") }
            handle { call.response.headers.append(HttpHeaders.Allow, "GET, HEAD"); call.respond(HttpStatusCode.MethodNotAllowed) }
        }
    }
    route(errorProperties.get().path) { handle { serveError(call, errorProperties.get(), objectMapper) } }
}

enum class ErrorAttributeMode { ALWAYS, ON_PARAM, NEVER }

data class InfrastructureErrorSettings(
    val path: String,
    val includeException: Boolean,
    val includeMessage: ErrorAttributeMode,
    val includeStacktrace: ErrorAttributeMode,
    val includePath: ErrorAttributeMode,
)

internal val INFRASTRUCTURE_REQUEST = AttributeKey<InfrastructureRequestSnapshot>("airsonic.infrastructure.request")

internal data class InfrastructureRequestSnapshot(
    val method: String,
    val rawPath: String,
    val servletContext: ServletContext,
    val headers: Map<String, String>,
    val errorStatus: Int?,
    val errorPath: String?,
    val errorMessage: String?,
    val exception: Throwable?,
    val flags: Map<String, String?>,
) {
    fun header(name: String): String? = headers[name.lowercase()]
}

private fun parseErrorFlags(query: String?): Map<String, String?> {
    val wanted = setOf("trace", "message", "path")
    val values = mutableMapOf<String, String?>()
    query.orEmpty().split('&').forEach { pair ->
        val parts = pair.split('=', limit = 2)
        val key = runCatching { URLDecoder.decode(parts[0].replace("+", "%2B"), StandardCharsets.UTF_8) }.getOrNull()
        if (key in wanted && key !in values) {
            values[key!!] = parts.getOrNull(1)?.let {
                runCatching { URLDecoder.decode(it.replace("+", "%2B"), StandardCharsets.UTF_8) }.getOrDefault("")
            } ?: ""
        }
    }
    return values
}

internal fun infrastructureRequestSnapshot(request: jakarta.servlet.http.HttpServletRequest): InfrastructureRequestSnapshot {
    val headers = listOf(HttpHeaders.IfModifiedSince, HttpHeaders.Range, HttpHeaders.IfRange, HttpHeaders.Accept)
        .mapNotNull { name -> request.getHeader(name)?.let { name.lowercase() to it } }.toMap()
    val contextPath = request.contextPath
    val errorPath = (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) as? String)
        ?.substringBefore('?')
    val errorStatus = (request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as? Number)?.toInt()
    val flags = parseErrorFlags(request.queryString)
    return InfrastructureRequestSnapshot(
        request.method, request.requestURI.removePrefix(contextPath), request.servletContext, headers,
        errorStatus, errorPath, request.getAttribute(RequestDispatcher.ERROR_MESSAGE)?.toString(),
        request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) as? Throwable, flags,
    )
}

private val log = LoggerFactory.getLogger("org.airsonic.player.spring.KtorInfrastructureRoutes")
private val httpDate = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)

private suspend fun serveStatic(call: ApplicationCall, root: String, resourceOverride: String? = null) {
    val request = call.attributes[INFRASTRUCTURE_REQUEST]
    val method = request.method
    if (method != "GET" && method != "HEAD") {
        call.response.headers.append(HttpHeaders.Allow, "GET, HEAD")
        call.respond(HttpStatusCode.MethodNotAllowed)
        return
    }
    val raw = request.rawPath
    val decoded = if (resourceOverride != null && raw == "/") resourceOverride else try {
        URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8)
    } catch (_: Exception) {
        call.respond(HttpStatusCode.BadRequest); return
    }
    if (decoded.contains('\\') || decoded.any { it == '\u0000' || it.code < 0x20 } || decoded.contains('%') || decoded.split('/').any { it == "." || it == ".." } ||
        decoded.split('/').any { it.equals("WEB-INF", true) || it.equals("META-INF", true) } ||
        !decoded.startsWith(root)) {
        call.respond(HttpStatusCode.BadRequest); return
    }
    val resourcePath = decoded
    if (resourcePath.endsWith('/')) { call.respond(HttpStatusCode.NotFound); return }
    val context = request.servletContext
    val connection: java.net.URLConnection
    val stream: java.io.InputStream
    try {
        if (context.getResourcePaths(resourcePath.trimEnd('/') + "/") != null) {
            call.respond(HttpStatusCode.NotFound); return
        }
        val url = context.getResource(resourcePath)
        if (url == null) { call.respond(HttpStatusCode.NotFound); return }
        connection = url.openConnection().apply { useCaches = false }
        stream = connection.getInputStream()
    } catch (_: Exception) {
        call.respond(HttpStatusCode.NotFound); return
    }
    try {
        val length = connection.contentLengthLong
        val modified = connection.lastModified
        val modifiedHeader = if (modified > 0) httpDate.format(Instant.ofEpochMilli(modified / 1000 * 1000)) else null
        if (modifiedHeader != null) call.response.headers.append(HttpHeaders.LastModified, modifiedHeader)
        val mime = context.getMimeType(resourcePath)?.let { ContentType.parse(it) } ?: ContentType.Application.OctetStream
        val since = request.header(HttpHeaders.IfModifiedSince)?.let { runCatching { Instant.from(httpDate.parse(it)).toEpochMilli() }.getOrNull() }
        if (since != null && modified > 0 && modified / 1000 <= since / 1000) {
            stream.close(); call.respond(HttpStatusCode.NotModified); return
        }
        call.response.headers.append(HttpHeaders.AcceptRanges, "bytes")
        val range = request.header(HttpHeaders.Range)
        val ifRange = request.header(HttpHeaders.IfRange)
        val canRange = range?.startsWith("bytes=") == true && (ifRange == null || ifRange == modifiedHeader ||
            runCatching { Instant.from(httpDate.parse(ifRange)).toEpochMilli() >= modified }.getOrDefault(false))
        var start = 0L
        var end = if (length >= 0) length - 1 else -1L
        var status = HttpStatusCode.OK
        if (method == "GET" && canRange && length >= 0 && range != null) {
            val spec = range.removePrefix("bytes=")
            if (!spec.contains(',')) {
                val parsedRange = parseSingleRange(spec, length)
                if (parsedRange == null) {
                    stream.close()
                    call.response.headers.append(HttpHeaders.ContentRange, "bytes */$length")
                    call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                    return
                }
                start = parsedRange.start
                end = parsedRange.end
                status = HttpStatusCode.PartialContent
                call.response.headers.append(HttpHeaders.ContentRange, "bytes $start-$end/$length")
            }
        }
        val responseLength = if (end >= start) end - start + 1 else -1
        if (method == "HEAD") {
            stream.close()
            val responseStatus = status
            call.respond(object : OutgoingContent.NoContent() {
                override val status: HttpStatusCode = responseStatus
                override val contentType = mime
                override val contentLength = responseLength.takeIf { it >= 0 }
            })
            return
        }
        var remainingSkip = start
        while (remainingSkip > 0) {
            val skipped = stream.skip(remainingSkip)
            if (skipped > 0) remainingSkip -= skipped else if (stream.read() >= 0) remainingSkip-- else break
        }
        call.respondOutputStream(mime, status, responseLength.takeIf { it >= 0 }) {
            stream.use { input ->
                val buffer = ByteArray(16 * 1024)
                var remaining = responseLength
                while (remaining != 0L) {
                    val count = input.read(buffer, 0, if (remaining < 0) buffer.size else minOf(buffer.size.toLong(), remaining).toInt())
                    if (count < 0) break
                    write(buffer, 0, count)
                    if (remaining > 0) remaining -= count
                }
            }
        }
    } catch (e: CancellationException) {
        stream.close()
        throw e
    } catch (e: Exception) {
        stream.close()
        log.debug("Unable to serve web resource {}", resourcePath, e)
        if (!call.response.isCommitted) call.respond(HttpStatusCode.NotFound)
    }
}

private suspend fun serveError(call: ApplicationCall, errorProperties: InfrastructureErrorSettings, objectMapper: ObjectMapper) {
    val request = call.attributes[INFRASTRUCTURE_REQUEST]
    val status = request.errorStatus ?: 500
    val safeStatus = status.takeIf { it in 400..599 } ?: 500
    val path = request.errorPath ?: request.rawPath
    val exception = request.exception
    val includeMessage = includeErrorAttribute(errorProperties.includeMessage, request.flags["message"])
    val includeTrace = includeErrorAttribute(errorProperties.includeStacktrace, request.flags["trace"])
    val includePath = includeErrorAttribute(errorProperties.includePath, request.flags["path"])
    val message = request.errorMessage.orEmpty()
    val wantsHtml = prefersHtml(request.header(HttpHeaders.Accept))
    call.response.headers.append(HttpHeaders.CacheControl, "no-store")
    call.response.headers.append(HttpHeaders.Vary, HttpHeaders.Accept)
    if (wantsHtml) {
        val details = if (includeMessage && message.isNotEmpty()) "<p>Message: ${escapeHtml(message)}</p>" else ""
        val trace = if (includeTrace && exception != null) "<pre>${escapeHtml(exception.stackTraceToString())}</pre>" else ""
        val errorPath = if (includePath) "<p>Path: ${escapeHtml(path)}</p>" else ""
        val exceptionName = if (errorProperties.includeException && exception != null) "<p>Exception: ${escapeHtml(exception.javaClass.name)}</p>" else ""
        val body = """<!doctype html><html><head><meta charset="utf-8"><title>Error $safeStatus</title></head><body><h1>Error $safeStatus</h1><p>${escapeHtml(HttpStatusCode.fromValue(safeStatus).description)}</p>$errorPath$exceptionName$details$trace</body></html>"""
        if (request.method == "HEAD") {
            call.respond(object : OutgoingContent.NoContent() {
                override val status = HttpStatusCode.fromValue(safeStatus)
                override val contentType = ContentType.Text.Html.withCharset(StandardCharsets.UTF_8)
            })
            return
        }
        call.respondText(body, ContentType.Text.Html.withCharset(StandardCharsets.UTF_8), HttpStatusCode.fromValue(safeStatus))
    } else {
        val payload = linkedMapOf<String, Any>("timestamp" to Instant.now().toString(), "status" to safeStatus,
            "error" to HttpStatusCode.fromValue(safeStatus).description)
        if (includePath) payload["path"] = path
        if (errorProperties.includeException && exception != null) payload["exception"] = exception.javaClass.name
        if (includeMessage && message.isNotEmpty()) payload["message"] = message
        if (includeTrace && exception != null) payload["trace"] = exception.stackTraceToString()
        if (request.method == "HEAD") {
            call.respond(object : OutgoingContent.NoContent() {
                override val status = HttpStatusCode.fromValue(safeStatus)
                override val contentType = ContentType.Application.Json.withCharset(StandardCharsets.UTF_8)
            })
            return
        }
        call.respondText(objectMapper.writeValueAsString(payload), ContentType.Application.Json.withCharset(StandardCharsets.UTF_8), HttpStatusCode.fromValue(safeStatus))
    }
}


private fun prefersHtml(accept: String?): Boolean {
    if (accept.isNullOrBlank()) return false
    val accepted = parseAndSortContentTypeHeader(accept)
    fun quality(type: String, subtype: String): Double {
        val matching = accepted.mapNotNull { header ->
            val candidate = runCatching { ContentType.parse(header.value) }.getOrNull() ?: return@mapNotNull null
            val matches = (candidate.contentType == type || candidate.contentType == "*") &&
                (candidate.contentSubtype == subtype || candidate.contentSubtype == "*")
            if (!matches) null else Triple(header.quality, (if (candidate.contentType == type) 1 else 0) + (if (candidate.contentSubtype == subtype) 1 else 0), header.params.size)
        }
        return matching.maxWithOrNull(compareBy<Triple<Double, Int, Int>> { it.second }.thenBy { it.third })?.first ?: 0.0
    }
    val htmlQuality = quality("text", "html")
    val jsonQuality = quality("application", "json")
    return htmlQuality > 0.0 && htmlQuality > jsonQuality
}

private data class ByteRange(val start: Long, val end: Long)

private fun parseSingleRange(spec: String, length: Long): ByteRange? {
    val parts = spec.split('-', limit = 2)
    if (parts.size != 2) return null
    val firstText = parts[0]
    val lastText = parts[1]
    val first = firstText.takeIf(String::isNotEmpty)?.toLongOrNull()
    val last = lastText.takeIf(String::isNotEmpty)?.toLongOrNull()
    val start: Long
    val end: Long
    if (firstText.isEmpty() && last != null && last > 0) {
        start = (length - last).coerceAtLeast(0)
        end = length - 1
    } else if (first != null && first >= 0 && (lastText.isEmpty() || last != null)) {
        start = first
        end = last?.coerceAtMost(length - 1) ?: (length - 1)
    } else return null
    if (start >= length || end < start) return null
    return ByteRange(start, end)
}

private fun includeErrorAttribute(policy: ErrorAttributeMode, parameter: String?): Boolean = when (policy) {
    ErrorAttributeMode.ALWAYS -> true
    ErrorAttributeMode.ON_PARAM -> parameter != null && !parameter.equals("false", ignoreCase = true)
    ErrorAttributeMode.NEVER -> false
}

private fun escapeHtml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
