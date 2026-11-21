package org.airsonic.player.service

import de.umass.lastfm.cache.Cache
import org.airsonic.player.util.FileUtil
import org.apache.commons.io.IOUtils
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Based on Last.fm's cache, with closed streams and a fixed time-to-live. */
class LastFmCache(private val cacheDir: Path, private val ttl: Long) : Cache() {
    init {
        setExpirationPolicy { _, _ -> ttl }
    }

    override fun contains(cacheEntryName: String): Boolean = Files.exists(xmlFile(cacheEntryName))

    override fun load(cacheEntryName: String): InputStream? = try {
        Files.newInputStream(xmlFile(cacheEntryName)).use { input ->
            ByteArrayInputStream(IOUtils.toByteArray(input))
        }
    } catch (_: Exception) {
        null
    }

    override fun remove(cacheEntryName: String) {
        FileUtil.delete(xmlFile(cacheEntryName))
        FileUtil.delete(metaFile(cacheEntryName))
    }

    override fun store(cacheEntryName: String, inputStream: InputStream, expirationDate: Long) {
        createCache()
        val xmlFile = xmlFile(cacheEntryName)
        val metaFile = metaFile(cacheEntryName)

        try {
            inputStream.use { Files.copy(it, xmlFile, StandardCopyOption.REPLACE_EXISTING) }
            Files.newBufferedWriter(metaFile).use { writer ->
                val properties = Properties()
                // Ignore Last.fm's expiration date, which is only one day in the future.
                properties.setProperty("expiration-date", getExpirationDate().toString())
                properties.store(writer, null)
            }
        } catch (_: Exception) {
            // A failed cache write should not fail the Last.fm request.
        }
    }

    override fun isExpired(cacheEntryName: String): Boolean {
        val file = metaFile(cacheEntryName)
        if (!Files.exists(file)) return false

        return try {
            Files.newBufferedReader(file).use { reader ->
                val properties = Properties()
                properties.load(reader)
                properties.getProperty("expiration-date").toLong() < System.currentTimeMillis()
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun clear() {
        try {
            Files.list(cacheDir).use { paths ->
                paths.filter(Files::isRegularFile).forEach { Files.deleteIfExists(it) }
            }
        } catch (_: Exception) {
            // Clearing the cache is best-effort.
        }
    }

    private fun getExpirationDate(): Long = System.currentTimeMillis() + ttl

    private fun createCache() {
        if (!Files.exists(cacheDir)) {
            try {
                Files.createDirectories(cacheDir)
            } catch (_: java.io.IOException) {
                // The subsequent write will be ignored if the directory cannot be created.
            }
        }
    }

    private fun xmlFile(cacheEntryName: String): Path = cacheDir.resolve("$cacheEntryName.xml")

    private fun metaFile(cacheEntryName: String): Path = cacheDir.resolve("$cacheEntryName.meta")
}
