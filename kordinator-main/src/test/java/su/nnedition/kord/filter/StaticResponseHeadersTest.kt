package su.nnedition.kord.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicInteger

class StaticResponseHeadersTest {
    @Test
    fun appliesConfiguredHeaderNamesAndValuesInOrder() {
        val configured = linkedMapOf(
            "Cache-Control" to "no-cache, post-check=0, pre-check=0",
            "Pragma" to "no-cache",
            "Expires" to "Thu, 01 Dec 1994 16:00:00 GMT",
        )
        val headers = StaticResponseHeaders(configured)
        val applied = LinkedHashMap<String, String>()

        headers.apply(applied::put)

        assertEquals(configured, applied)
        assertEquals(configured, headers.values())
    }

    @Test
    fun copiesInputAndDoesNotExposeMutableHeaderMap() {
        val configured = linkedMapOf("Cache-Control" to "max-age=36000")
        val headers = StaticResponseHeaders(configured)
        configured["Cache-Control"] = "no-store"

        assertEquals("max-age=36000", headers.values()["Cache-Control"])
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (headers.values() as MutableMap<String, String>)["Pragma"] = "no-cache"
        }
    }

    @Test
    fun writesEachConfiguredHeaderOnce() {
        val headers = StaticResponseHeaders(mapOf("Cache-Control" to "max-age=36000"))
        val writes = AtomicInteger()

        headers.apply { _, _ -> writes.incrementAndGet() }

        assertEquals(1, writes.get())
    }
}
