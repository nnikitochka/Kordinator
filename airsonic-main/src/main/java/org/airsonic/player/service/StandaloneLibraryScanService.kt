package org.airsonic.player.service

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
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
    val durationSeconds: Int,
    val sizeBytes: Long,
    val modifiedAt: Instant?,
)

data class StandaloneTrackFile(val path: Path, val sizeBytes: Long, val contentType: String)

/** Scans registered roots and serves indexed track metadata for the standalone API. */
class StandaloneLibraryScanService(
    private val dataSource: DataSource,
) {
    fun enqueue(libraryId: Long): LibraryScan {
        val rootExists = dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT 1 FROM music_library WHERE id = ?").use { statement ->
                statement.setLong(1, libraryId)
                statement.executeQuery().use { it.next() }
            }
        }
        require(rootExists) { "Library does not exist" }

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
            val rootPath = Path.of(root).toRealPath()
            Files.walk(rootPath).use { paths ->
                paths.filter { path ->
                    path != rootPath && !Files.isSymbolicLink(path) && Files.isRegularFile(path) &&
                        path.fileName.toString().substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
                }.forEach { file ->
                    val attributes = Files.readAttributes(file, BasicFileAttributes::class.java)
                    val metadata = readMetadata(file)
                    if (metadata.parseError) errors++
                    val relativePath = rootPath.relativize(file).toString()
                    upsertTrack(scan, relativePath, file, attributes, metadata)
                    count++
                    updateProgress(scanId, count, errors)
                }
            }
            deleteStaleTracks(scan.libraryId, scan.id)
            finish(scanId, "COMPLETED", count, errors)
        } catch (failure: Throwable) {
            LOG.error("Standalone music scan {} failed", scanId, failure)
            finish(scanId, "FAILED", count, errors + 1)
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

    fun tracks(libraryId: Long?, query: String?, limit: Int, offset: Int): List<StandaloneTrack> {
        val safeLimit = limit.coerceIn(1, 200)
        val safeOffset = offset.coerceAtLeast(0)
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()?.let { "%$it%" }
        val sql = "SELECT id, library_id, relative_path, title, artist, album, album_artist, duration_seconds, " +
            "size_bytes, modified_at FROM media_track WHERE (? IS NULL OR library_id = ?) " +
            "AND (? IS NULL OR LOWER(title) LIKE ? OR LOWER(artist) LIKE ? OR LOWER(album) LIKE ?) " +
            "ORDER BY LOWER(COALESCE(artist, '')), LOWER(COALESCE(album, '')), LOWER(title), id LIMIT ? OFFSET ?"
        return dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, libraryId)
                statement.setObject(2, libraryId)
                statement.setString(3, normalizedQuery)
                statement.setString(4, normalizedQuery)
                statement.setString(5, normalizedQuery)
                statement.setString(6, normalizedQuery)
                statement.setInt(7, safeLimit)
                statement.setInt(8, safeOffset)
                statement.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(rows.toTrack()) }
                }
            }
        }
    }

    fun countTracks(libraryId: Long?, query: String?): Long {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()?.let { "%$it%" }
        val sql = "SELECT COUNT(*) FROM media_track WHERE (? IS NULL OR library_id = ?) " +
            "AND (? IS NULL OR LOWER(title) LIKE ? OR LOWER(artist) LIKE ? OR LOWER(album) LIKE ?)"
        return dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, libraryId)
                statement.setObject(2, libraryId)
                statement.setString(3, normalizedQuery)
                statement.setString(4, normalizedQuery)
                statement.setString(5, normalizedQuery)
                statement.setString(6, normalizedQuery)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getLong(1)
                }
            }
        }
    }

    fun streamFile(trackId: Long): StandaloneTrackFile? {
        val locations = dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT l.root_path, t.relative_path FROM media_track t " +
                    "JOIN music_library l ON l.id = t.library_id WHERE t.id = ?",
            ).use { statement ->
                statement.setLong(1, trackId)
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getString(1) to rows.getString(2) else null
                }
            }
        } ?: return null

        return try {
            val root = Path.of(locations.first).toRealPath()
            val candidate = root.resolve(locations.second).normalize()
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

    private fun findScan(scanId: String): LibraryScan? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT id, library_id, state, tracks_scanned, error_count, started_at, finished_at " +
                "FROM library_scan_job WHERE id = ?",
        ).use { statement ->
            statement.setString(1, scanId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toScan() else null }
        }
    }

    private fun findLibraryRoot(libraryId: Long): String? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT root_path FROM music_library WHERE id = ?").use { statement ->
            statement.setLong(1, libraryId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    private fun readMetadata(file: Path): ParsedMetadata = try {
        val audio = AudioFileIO.read(file.toFile())
        val tag = audio.tag
        ParsedMetadata(
            title = tag?.getFirst(FieldKey.TITLE)?.trim()?.takeIf { it.isNotEmpty() } ?: file.fileName.toString().substringBeforeLast('.'),
            artist = tag?.getFirst(FieldKey.ARTIST)?.trim()?.takeIf { it.isNotEmpty() },
            album = tag?.getFirst(FieldKey.ALBUM)?.trim()?.takeIf { it.isNotEmpty() },
            albumArtist = tag?.getFirst(FieldKey.ALBUM_ARTIST)?.trim()?.takeIf { it.isNotEmpty() },
            duration = audio.audioHeader.trackLength.coerceAtLeast(0),
            parseError = false,
        )
    } catch (failure: Exception) {
        LOG.debug("Could not parse audio tags for {}", file, failure)
        ParsedMetadata(file.fileName.toString().substringBeforeLast('.'), null, null, null, 0, true)
    }

    private fun upsertTrack(
        scan: LibraryScan,
        relativePath: String,
        file: Path,
        attributes: BasicFileAttributes,
        metadata: ParsedMetadata,
    ) {
        dataSource.connection.use { connection ->
            val existingId = connection.prepareStatement(
                "SELECT id FROM media_track WHERE library_id = ? AND relative_path = ?",
            ).use { statement ->
                statement.setLong(1, scan.libraryId)
                statement.setString(2, relativePath)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
            }
            val sql = if (existingId == null) {
                "INSERT INTO media_track (library_id, relative_path, title, artist, album, album_artist, duration_seconds, size_bytes, modified_at, scan_id, scanned_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            } else {
                "UPDATE media_track SET title = ?, artist = ?, album = ?, album_artist = ?, duration_seconds = ?, size_bytes = ?, " +
                    "modified_at = ?, scan_id = ?, scanned_at = ? WHERE id = ?"
            }
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                if (existingId == null) statement.setLong(index++, scan.libraryId)
                if (existingId == null) statement.setString(index++, relativePath)
                statement.setString(index++, metadata.title)
                statement.setString(index++, metadata.artist)
                statement.setString(index++, metadata.album)
                statement.setString(index++, metadata.albumArtist)
                statement.setInt(index++, metadata.duration)
                statement.setLong(index++, attributes.size())
                statement.setTimestamp(index++, Timestamp.from(attributes.lastModifiedTime().toInstant()))
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

    private fun java.sql.ResultSet.toTrack() = StandaloneTrack(
        getLong("id"), getLong("library_id"), getString("relative_path"), getString("title"), getString("artist"),
        getString("album"), getString("album_artist"), getInt("duration_seconds"), getLong("size_bytes"),
        getTimestamp("modified_at")?.toInstant(),
    )

    private data class ParsedMetadata(
        val title: String,
        val artist: String?,
        val album: String?,
        val albumArtist: String?,
        val duration: Int,
        val parseError: Boolean,
    )

    private companion object {
        val LOG = LoggerFactory.getLogger(StandaloneLibraryScanService::class.java)
        val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "opus", "m4a", "mp4", "wav", "aif", "aiff", "ape", "wma", "dsf", "dff", "wv")
        val MIME_TYPES = mapOf("mp3" to "audio/mpeg", "flac" to "audio/flac", "ogg" to "audio/ogg", "opus" to "audio/ogg", "m4a" to "audio/mp4")
    }
}
