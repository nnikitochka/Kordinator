package su.nnedition.kord.util

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.Instant
import java.util.Comparator

/** Miscellaneous file utility methods. */
object FileUtil {
    private val LOG = LoggerFactory.getLogger(FileUtil::class.java)

    @JvmStatic
    fun lastModified(file: Path): Instant = try {
        Files.getLastModifiedTime(file).toInstant()
    } catch (error: IOException) {
        LOG.warn("Could not get file modify date for {}", file, error)
        Instant.now()
    }

    @JvmStatic
    fun size(file: Path): Long = try {
        Files.size(file)
    } catch (error: IOException) {
        LOG.warn("Could not get file size for {}", file, error)
        0
    }

    @JvmStatic
    fun delete(fileOrFolder: Path): Boolean {
        try {
            Files.walk(fileOrFolder).use { walk ->
                walk.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
            return true
        } catch (error: NoSuchFileException) {
            LOG.debug("Could not delete file/folder {}", fileOrFolder, error)
            return false
        } catch (error: Exception) {
            LOG.warn("Could not delete file/folder {}", fileOrFolder, error)
            return false
        }
    }

    /** Returns the parent directory name and file name for the given path. */
    @JvmStatic
    fun getShortPath(file: Path?): String? {
        if (file == null) return null
        val parent = file.parent ?: return file.fileName.toString()
        return parent.fileName.toString() + File.separator + file.fileName.toString()
    }

    /** Closes the resource, ignoring any I/O errors. */
    @JvmStatic
    fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (_: IOException) {
            // Ignored.
        }
    }
}
