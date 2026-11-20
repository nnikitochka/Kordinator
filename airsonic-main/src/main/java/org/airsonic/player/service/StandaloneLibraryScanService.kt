package org.airsonic.player.service

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaScannerService
import org.airsonic.player.service.metadata.JaudiotaggerParser
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

data class LibraryScan(
    val id: String,
    val libraryId: Long,
    val state: String,
    val tracksScanned: Long,
    val errorCount: Long,
    val startedAt: Instant,
    val finishedAt: Instant?,
)

data class StandaloneTrack(
    val id: Long,
    val libraryId: Long,
    val relativePath: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val year: Int?,
    val genre: String?,
    val durationSeconds: Int,
    val sizeBytes: Long,
    val modifiedAt: Instant?,
)

data class StandaloneTrackFile(val path: Path, val sizeBytes: Long, val contentType: String)
data class StandaloneArtwork(val bytes: ByteArray, val contentType: String)
data class StandaloneAlbum(
    val libraryId: Long,
    val name: String,
    val artist: String?,
    val year: Int?,
    val genre: String?,
    val trackCount: Long,
    val durationSeconds: Long,
)
data class StandaloneArtist(val libraryId: Long, val name: String, val albumCount: Long, val trackCount: Long)

/** Coordinates the core media scan and projects its catalog entries into the Ktor API store. */
class StandaloneLibraryScanService(
    private val dataSource: DataSource,
    private val mediaFolderService: MediaFolderService,
    private val mediaFileService: MediaFileService,
    private val mediaScannerService: MediaScannerService,
) {
    fun enqueue(libraryId: Long): LibraryScan {
        val musicFolder = libraryId.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()?.let {
            mediaFolderService.getMusicFolderById(it, true, true)
        }
        require(musicFolder != null && musicFolder.id?.toLong() == libraryId) { "Library does not exist" }

        val id = UUID.randomUUID().toString()
        val now = Instant.now()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT 1 FROM library_scan_job WHERE library_id = ? AND state IN ('QUEUED', 'RUNNING')",
            ).use { statement ->
                statement.setLong(1, libraryId)
                statement.executeQuery().use { check(!it.next()) { "Library scan is already running" } }
            }
            connection.prepareStatement(
                "INSERT INTO library_scan_job (id, library_id, state, tracks_scanned, error_count, started_at) " +
                    "VALUES (?, ?, 'QUEUED', 0, 0, ?)",
            ).use { statement ->
                statement.setString(1, id)
                statement.setLong(2, libraryId)
                statement.setTimestamp(3, Timestamp.from(now))
                statement.executeUpdate()
            }
        }
        return LibraryScan(id, libraryId, "QUEUED", 0, 0, now, null)
    }

    fun run(scanId: String) {
        val scan = findScan(scanId) ?: return
        val root = findLibraryRoot(scan.libraryId) ?: run {
            finish(scanId, "FAILED", scan.tracksScanned, scan.errorCount + 1)
            return
        }
        updateState(scanId, "RUNNING")
        var count = 0L
        var errors = 0L

        try {
            runCoreScan()
            val musicFolder = requireNotNull(mediaFolderService.getMusicFolderById(scan.libraryId.toInt(), true, true))
            val rootPath = root.toRealPath()
            val rootFile = mediaFileService.getMediaFile(Paths.get(""), musicFolder, true)
                ?: throw IllegalStateException("Music folder root is unavailable after scanning")
            val pending = ArrayDeque<MediaFile>()
            pending.add(rootFile)
            while (pending.isNotEmpty()) {
                val parent = pending.removeLast()
                mediaFileService.getChildrenOf(parent, true, true, true, true).forEach { mediaFile ->
                    if (mediaFile.isDirectory) {
                        pending.add(mediaFile)
                    } else if (mediaFile.isAudio) {
                        val relativePath = Paths.get(mediaFile.path).normalize()
                        val file = rootPath.resolve(relativePath).normalize()
                        if (!file.startsWith(rootPath) || Files.isSymbolicLink(file) || !Files.isRegularFile(file)) {
                            errors++
                        } else {
                            upsertTrack(scan, relativePath.toString(), mediaFile)
                            count++
                        }
                        updateProgress(scanId, count, errors)
                    }
                }
            }
            deleteStaleTracks(scan.libraryId, scan.id)
            finish(scanId, "COMPLETED", count, errors)
        } catch (failure: Throwable) {
            LOG.error("Standalone music scan {} failed", scanId, failure)
            finish(scanId, "FAILED", count, errors + 1)
        }
    }

    private fun runCoreScan() {
        if (!mediaScannerService.isScanning) {
            mediaScannerService.scanLibrary()
        }
        val deadline = System.nanoTime() + CORE_SCAN_TIMEOUT_NANOS
        while (mediaScannerService.isScanning) {
            check(System.nanoTime() < deadline) { "Media library scan did not finish before timeout" }
            Thread.sleep(CORE_SCAN_POLL_MILLIS)
        }
    }

    fun latestScan(libraryId: Long): LibraryScan? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT id, library_id, state, tracks_scanned, error_count, started_at, finished_at " +
                "FROM library_scan_job WHERE library_id = ? ORDER BY started_at DESC",
        ).use { statement ->
            statement.setLong(1, libraryId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toScan() else null }
        }
    }

    fun streamFile(libraryId: Long, relativePath: String): StandaloneTrackFile? {
        return try {
            if (libraryId !in 0..Int.MAX_VALUE.toLong()) return null
            val folderId = libraryId.toInt()
            val musicFolder = mediaFolderService.getMusicFolderById(folderId, true, true) ?: return null
            val root = musicFolder.path.toRealPath()
            val candidate = root.resolve(relativePath).normalize()
            if (!candidate.startsWith(root) || Files.isSymbolicLink(candidate)) return null
            val file = candidate.toRealPath()
            if (!file.startsWith(root) || !Files.isRegularFile(file)) return null
            val contentType = Files.probeContentType(file) ?: MIME_TYPES[file.fileName.toString().substringAfterLast('.', "").lowercase()]
                ?: "application/octet-stream"
            StandaloneTrackFile(file, Files.size(file), contentType)
        } catch (_: Exception) {
            null
        }
    }

    fun artwork(libraryId: Long, relativePath: String): StandaloneArtwork? {
        val track = streamFile(libraryId, relativePath) ?: return null
        val directory = track.path.parent
        for (candidateName in COVER_FILE_NAMES) {
            val candidate = directory.resolve(candidateName)
            if (!Files.isRegularFile(candidate) || Files.isSymbolicLink(candidate)) continue
            if (Files.size(candidate) > MAX_ARTWORK_BYTES) continue
            val bytes = Files.readAllBytes(candidate)
            val contentType = imageContentType(bytes) ?: continue
            return StandaloneArtwork(bytes, contentType)
        }

        return try {
            val bytes = JaudiotaggerParser.getArtwork(track.path)?.binaryData ?: return null
            if (bytes.isEmpty() || bytes.size > MAX_ARTWORK_BYTES) return null
            val contentType = imageContentType(bytes) ?: return null
            StandaloneArtwork(bytes, contentType)
        } catch (failure: Exception) {
            LOG.debug("Could not read embedded cover art for {}", relativePath, failure)
            null
        }
    }

    private fun imageContentType(bytes: ByteArray): String? = try {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { imageInput ->
            val reader = ImageIO.getImageReaders(imageInput).asSequence().firstOrNull() ?: return null
            when (reader.formatName.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "bmp" -> "image/bmp"
                else -> null
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun findScan(scanId: String): LibraryScan? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT id, library_id, state, tracks_scanned, error_count, started_at, finished_at " +
                "FROM library_scan_job WHERE id = ?",
        ).use { statement ->
            statement.setString(1, scanId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toScan() else null }
        }
    }

    private fun findLibraryRoot(libraryId: Long): Path? = libraryId
        .takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }
        ?.toInt()
        ?.let { mediaFolderService.getMusicFolderById(it, true, true)?.path }

    private fun upsertTrack(
        scan: LibraryScan,
        relativePath: String,
        mediaFile: MediaFile,
    ) {
        val title = mediaFile.title?.takeIf { it.isNotBlank() } ?: Paths.get(relativePath).fileName.toString().substringBeforeLast('.')
        val modifiedAt = mediaFile.changed ?: Instant.now()
        dataSource.connection.use { connection ->
            val existingId = connection.prepareStatement(
                "SELECT id FROM media_track WHERE library_id = ? AND relative_path = ?",
            ).use { statement ->
                statement.setLong(1, scan.libraryId)
                statement.setString(2, relativePath)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
            }
            val sql = if (existingId == null) {
                "INSERT INTO media_track (library_id, relative_path, title, artist, album, album_artist, disc_number, track_number, year, genre, duration_seconds, size_bytes, modified_at, scan_id, scanned_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            } else {
                "UPDATE media_track SET title = ?, artist = ?, album = ?, album_artist = ?, disc_number = ?, track_number = ?, year = ?, genre = ?, duration_seconds = ?, size_bytes = ?, " +
                    "modified_at = ?, scan_id = ?, scanned_at = ? WHERE id = ?"
            }
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                if (existingId == null) statement.setLong(index++, scan.libraryId)
                if (existingId == null) statement.setString(index++, relativePath)
                statement.setString(index++, title)
                statement.setString(index++, mediaFile.artist)
                statement.setString(index++, mediaFile.albumName)
                statement.setString(index++, mediaFile.albumArtist)
                statement.setObject(index++, mediaFile.discNumber)
                statement.setObject(index++, mediaFile.trackNumber)
                statement.setObject(index++, mediaFile.year)
                statement.setString(index++, mediaFile.genre)
                statement.setInt(index++, mediaFile.duration?.toInt()?.coerceAtLeast(0) ?: 0)
                statement.setLong(index++, mediaFile.fileSize ?: 0L)
                statement.setTimestamp(index++, Timestamp.from(modifiedAt))
                statement.setString(index++, scan.id)
                statement.setTimestamp(index++, Timestamp.from(Instant.now()))
                if (existingId != null) statement.setLong(index, existingId)
                statement.executeUpdate()
            }
        }
    }

    private fun updateProgress(scanId: String, count: Long, errors: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE library_scan_job SET tracks_scanned = ?, error_count = ? WHERE id = ?",
            ).use { statement ->
                statement.setLong(1, count)
                statement.setLong(2, errors)
                statement.setString(3, scanId)
                statement.executeUpdate()
            }
        }
    }

    private fun updateState(scanId: String, state: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE library_scan_job SET state = ?, started_at = ? WHERE id = ?").use { statement ->
                statement.setString(1, state)
                statement.setTimestamp(2, Timestamp.from(Instant.now()))
                statement.setString(3, scanId)
                statement.executeUpdate()
            }
        }
    }

    private fun deleteStaleTracks(libraryId: Long, scanId: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM media_track WHERE library_id = ? AND scan_id <> ?").use { statement ->
                statement.setLong(1, libraryId)
                statement.setString(2, scanId)
                statement.executeUpdate()
            }
        }
    }

    private fun finish(scanId: String, state: String, count: Long, errors: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE library_scan_job SET state = ?, tracks_scanned = ?, error_count = ?, finished_at = ? WHERE id = ?",
            ).use { statement ->
                statement.setString(1, state)
                statement.setLong(2, count)
                statement.setLong(3, errors)
                statement.setTimestamp(4, Timestamp.from(Instant.now()))
                statement.setString(5, scanId)
                statement.executeUpdate()
            }
        }
    }

    private fun java.sql.ResultSet.toScan() = LibraryScan(
        getString("id"), getLong("library_id"), getString("state"), getLong("tracks_scanned"), getLong("error_count"),
        getTimestamp("started_at").toInstant(), getTimestamp("finished_at")?.toInstant(),
    )

    private companion object {
        val LOG = LoggerFactory.getLogger(StandaloneLibraryScanService::class.java)
        const val CORE_SCAN_TIMEOUT_NANOS = 21_600_000_000_000L
        const val CORE_SCAN_POLL_MILLIS = 100L
        val MIME_TYPES = mapOf("mp3" to "audio/mpeg", "flac" to "audio/flac", "ogg" to "audio/ogg", "opus" to "audio/ogg", "m4a" to "audio/mp4")
        val COVER_FILE_NAMES = listOf(
            "cover.jpg", "cover.jpeg", "cover.png", "folder.jpg", "folder.jpeg", "folder.png", "front.jpg", "front.jpeg", "front.png",
        )
        const val MAX_ARTWORK_BYTES = 20L * 1024 * 1024
    }
}
