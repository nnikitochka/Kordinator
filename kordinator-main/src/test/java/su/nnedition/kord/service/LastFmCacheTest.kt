package su.nnedition.kord.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path

class LastFmCacheTest {
    @TempDir
    lateinit var cacheDir: Path

    @Test
    fun `stores content with configured ttl and removes entries`() {
        val cache = LastFmCache(cacheDir, 60_000)
        cache.store("entry", ByteArrayInputStream("payload".toByteArray()), 0)

        assertTrue(cache.contains("entry"))
        assertFalse(cache.isExpired("entry"))
        assertEquals("payload", cache.load("entry")!!.bufferedReader().use { it.readText() })

        cache.remove("entry")
        assertFalse(cache.contains("entry"))
        assertFalse(Files.exists(cacheDir.resolve("entry.meta")))
    }

    @Test
    fun `clear removes regular cache files`() {
        val cache = LastFmCache(cacheDir, 60_000)
        cache.store("entry", ByteArrayInputStream("payload".toByteArray()), 0)

        cache.clear()

        assertFalse(cache.contains("entry"))
        assertFalse(Files.exists(cacheDir.resolve("entry.meta")))
    }
}
