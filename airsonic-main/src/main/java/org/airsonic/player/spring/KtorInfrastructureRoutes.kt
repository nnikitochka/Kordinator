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
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import jakarta.servlet.RequestDispatcher
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.function.Supplier

/** Shared HTTP error endpoint used by the current servlet adapter. */
internal fun Route.infrastructureRoutes(errorProperties: Supplier<InfrastructureErrorSettings>, objectMapper: ObjectMapper) {
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
    val headers = listOf(HttpHeaders.Accept)
        .mapNotNull { name -> request.getHeader(name)?.let { name.lowercase() to it } }.toMap()
    val contextPath = request.contextPath
    val errorPath = (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) as? String)
        ?.substringBefore('?')
    val errorStatus = (request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as? Number)?.toInt()
    val flags = parseErrorFlags(request.queryString)
    return InfrastructureRequestSnapshot(
        request.method, request.requestURI.removePrefix(contextPath), headers,
        errorStatus, errorPath, request.getAttribute(RequestDispatcher.ERROR_MESSAGE)?.toString(),
        request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) as? Throwable, flags,
    )
}

private val log = LoggerFactory.getLogger("org.airsonic.player.spring.KtorInfrastructureRoutes")
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

private fun includeErrorAttribute(policy: ErrorAttributeMode, parameter: String?): Boolean = when (policy) {
    ErrorAttributeMode.ALWAYS -> true
    ErrorAttributeMode.ON_PARAM -> parameter != null && !parameter.equals("false", ignoreCase = true)
    ErrorAttributeMode.NEVER -> false
}

private fun escapeHtml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
