package org.airsonic.player.http

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import java.util.Collections

class ServletConditionalRequestTest {
    @Test
    fun returnsNotModifiedForMatchingIfNoneMatchAndAddsCachingHeaders() {
        val fixture = fixture("GET", mapOf("If-None-Match" to listOf("\"123\"")))

        assertTrue(ServletConditionalRequest.checkNotModified(fixture.request, fixture.response, "123", 1_700_000_000_000))

        Mockito.verify(fixture.response).status = HttpServletResponse.SC_NOT_MODIFIED
        Mockito.verify(fixture.response).setHeader("ETag", "\"123\"")
        Mockito.verify(fixture.response).setDateHeader("Last-Modified", 1_700_000_000_000)
    }

    @Test
    fun ifNoneMatchTakesPrecedenceOverIfModifiedSince() {
        val fixture = fixture("GET", mapOf("If-None-Match" to listOf("\"other\"")),
            mapOf("If-Modified-Since" to 1_800_000_000_000))

        assertFalse(ServletConditionalRequest.checkNotModified(fixture.request, fixture.response, "123", 1_700_000_000_000))

        Mockito.verify(fixture.response, Mockito.never()).setStatus(HttpServletResponse.SC_NOT_MODIFIED)
    }

    @Test
    fun returnsNotModifiedForFreshIfModifiedSince() {
        val fixture = fixture("HEAD", emptyMap(), mapOf("If-Modified-Since" to 1_700_000_001_000))

        assertTrue(ServletConditionalRequest.checkNotModified(fixture.request, fixture.response, "123", 1_700_000_000_000))

        Mockito.verify(fixture.response).setStatus(HttpServletResponse.SC_NOT_MODIFIED)
    }

    @Test
    fun enforcesIfMatchOnlyForUnsafeMethods() {
        val mismatch = fixture("POST", mapOf("If-Match" to listOf("\"other\"")))
        assertTrue(ServletConditionalRequest.checkNotModified(mismatch.request, mismatch.response, "123", 1_700_000_000_000))
        Mockito.verify(mismatch.response).setStatus(HttpServletResponse.SC_PRECONDITION_FAILED)

        val match = fixture("POST", mapOf("If-Match" to listOf("\"123\"")))
        assertFalse(ServletConditionalRequest.checkNotModified(match.request, match.response, "123", 1_700_000_000_000))
        Mockito.verify(match.response, Mockito.never()).setStatus(HttpServletResponse.SC_PRECONDITION_FAILED)
    }

    private fun fixture(
        method: String,
        headers: Map<String, List<String>>,
        dates: Map<String, Long> = emptyMap(),
    ): Fixture {
        val request = Mockito.mock(HttpServletRequest::class.java)
        Mockito.`when`(request.method).thenReturn(method)
        Mockito.`when`(request.getHeaders(anyString())).thenAnswer { invocation ->
            Collections.enumeration(headers[invocation.getArgument<String>(0)] ?: emptyList())
        }
        Mockito.`when`(request.getDateHeader(anyString())).thenAnswer { invocation ->
            dates[invocation.getArgument<String>(0)] ?: -1L
        }
        val response = Mockito.mock(HttpServletResponse::class.java)
        Mockito.`when`(response.status).thenReturn(HttpServletResponse.SC_OK)
        Mockito.`when`(response.getHeader(anyString())).thenReturn(null)
        return Fixture(request, response)
    }

    private data class Fixture(val request: HttpServletRequest, val response: HttpServletResponse)
}
