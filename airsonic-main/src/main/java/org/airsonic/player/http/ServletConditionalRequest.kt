package org.airsonic.player.http

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/** Servlet equivalent of the conditional request checks used by the former Spring adapter. */
object ServletConditionalRequest {
    fun checkNotModified(
        request: HttpServletRequest,
        response: HttpServletResponse,
        entityTag: String?,
        lastModified: Long,
    ): Boolean {
        if (response.status != HttpServletResponse.SC_OK) return false

        val method = request.method
        val safeMethod = method == "GET" || method == "HEAD"
        val currentTag = entityTag?.takeIf(String::isNotEmpty)

        if (!safeMethod && request.getHeaders("If-Match").hasMoreElements()) {
            val failed = !matches(request.getHeaders("If-Match"), currentTag, weakComparison = false, wildcardMatches = true)
            return finish(response, safeMethod, failed, currentTag, lastModified)
        }

        if (lastModified >= 0) {
            val ifUnmodifiedSince = parseDate(request, "If-Unmodified-Since")
            if (ifUnmodifiedSince >= 0) {
                val failed = ifUnmodifiedSince < lastModified / 1000 * 1000
                return finish(response, safeMethod, failed, currentTag, lastModified)
            }
        }

        val ifNoneMatch = request.getHeaders("If-None-Match")
        val hasIfNoneMatch = ifNoneMatch.hasMoreElements()
        val failed = if (hasIfNoneMatch) {
            matches(ifNoneMatch, currentTag, weakComparison = true, wildcardMatches = true)
        } else if (lastModified >= 0 && safeMethod) {
            val ifModifiedSince = parseDate(request, "If-Modified-Since")
            if (ifModifiedSince >= 0) ifModifiedSince >= lastModified / 1000 * 1000 else false
        } else {
            false
        }
        return finish(response, safeMethod, failed, currentTag, lastModified)
    }

    private fun finish(
        response: HttpServletResponse,
        safeMethod: Boolean,
        failed: Boolean,
        entityTag: String?,
        lastModified: Long,
    ): Boolean {
        if (failed) response.status = if (safeMethod) HttpServletResponse.SC_NOT_MODIFIED else HttpServletResponse.SC_PRECONDITION_FAILED
        if (safeMethod) {
            if (lastModified > 0 && response.getHeader("Last-Modified") == null) {
                response.setDateHeader("Last-Modified", lastModified)
            }
            if (entityTag != null && response.getHeader("ETag") == null) {
                response.setHeader("ETag", quoteEntityTag(entityTag))
            }
        }
        return failed
    }

    private fun parseDate(request: HttpServletRequest, headerName: String): Long =
        try {
            request.getDateHeader(headerName)
        } catch (_: IllegalArgumentException) {
            -1
        }

    private fun matches(
        headers: java.util.Enumeration<String>,
        entityTag: String?,
        weakComparison: Boolean,
        wildcardMatches: Boolean,
    ): Boolean {
        if (entityTag == null) return false
        val current = parseEntityTag(entityTag) ?: return false
        while (headers.hasMoreElements()) {
            for (candidate in splitEntityTags(headers.nextElement())) {
                if (candidate == "*") {
                    if (wildcardMatches) return true
                    continue
                }
                val parsed = parseEntityTag(candidate) ?: continue
                val opaqueTagMatches = parsed.opaque == current.opaque
                val strengthMatches = weakComparison || (!parsed.weak && !current.weak)
                if (opaqueTagMatches && strengthMatches) return true
            }
        }
        return false
    }

    private fun splitEntityTags(header: String): List<String> {
        val values = mutableListOf<String>()
        var inQuotes = false
        var start = 0
        header.forEachIndexed { index, character ->
            when (character) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    values.add(header.substring(start, index).trim())
                    start = index + 1
                }
            }
        }
        values.add(header.substring(start).trim())
        return values.filter(String::isNotEmpty)
    }

    private fun parseEntityTag(value: String): EntityTag? {
        val trimmed = value.trim()
        val weak = trimmed.startsWith("W/")
        val tag = if (weak) trimmed.substring(2) else trimmed
        if (tag.length >= 2 && tag.startsWith('"') && tag.endsWith('"')) {
            return EntityTag(tag.substring(1, tag.length - 1), weak)
        }
        if (tag.isEmpty()) return null
        return EntityTag(tag, weak)
    }

    private fun quoteEntityTag(value: String): String =
        if ((value.startsWith("W/\"") && value.length > 3 && value.endsWith('"')) ||
            (value.startsWith('"') && value.length > 1 && value.endsWith('"'))
        ) value else "\"$value\""

    private data class EntityTag(val opaque: String, val weak: Boolean)
}
