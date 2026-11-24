package org.airsonic.player.dao

import org.airsonic.player.domain.MediaFile
import java.time.Instant
import java.util.function.Consumer

/** Writes media-file metadata and applies scanner presence changes. */
class MediaFileDaoMutation(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    fun createOrUpdateMediaFile(file: MediaFile, preInsertionCallback: Consumer<MediaFile>) {
        inTransaction(TransactionManager.ISOLATION_READ_COMMITTED) {
            val hasId = file.id != null
            val fields = listOf(
                "type" to "type", "format" to "format", "title" to "title", "album" to "album",
                "artist" to "artist", "album_artist" to "albumartist", "disc_number" to "dn",
                "track_number" to "tn", "year" to "year", "genre" to "genre", "bit_rate" to "br",
                "variable_bit_rate" to "vbr", "duration" to "dur", "file_size" to "fs", "width" to "w",
                "height" to "h", "parent_path" to "pp", "play_count" to "pc", "last_played" to "lp",
                "comment" to "comment", "changed" to "changed", "last_scanned" to "ls",
                "children_last_updated" to "clu", "present" to "pres", "version" to "ver",
                "mb_release_id" to "mbrelid", "mb_recording_id" to "mbrecid"
            )
            val assignments = buildList {
                if (hasId) {
                    add("path=:path")
                    add("folder_id=:fid")
                }
                fields.forEach { (column, parameter) -> add("$column=:$parameter") }
            }
            val where = if (hasId) "id=:id" else "path=:path and folder_id=:fid"
            val args = mutableMapOf<String, Any?>(
                "id" to file.id,
                "path" to file.path,
                "fid" to file.folderId,
                "type" to file.mediaType!!.name,
                "format" to file.format,
                "title" to file.title,
                "album" to file.albumName,
                "artist" to file.artist,
                "albumartist" to file.albumArtist,
                "dn" to file.discNumber,
                "tn" to file.trackNumber,
                "year" to file.year,
                "genre" to file.genre,
                "br" to file.bitRate,
                "vbr" to file.isVariableBitRate,
                "dur" to file.duration,
                "fs" to file.fileSize,
                "w" to file.width,
                "h" to file.height,
                "pp" to file.parentPath,
                "pc" to file.playCount,
                "lp" to file.lastPlayed,
                "comment" to file.comment,
                "changed" to file.changed,
                "ls" to file.lastScanned,
                "clu" to file.childrenLastUpdated,
                "pres" to file.isPresent,
                "ver" to MediaFileDao.VERSION,
                "mbrelid" to file.musicBrainzReleaseId,
                "mbrecid" to file.musicBrainzRecordingId
            )
            val changed = namedUpdate("update media_file set ${assignments.joinToString(",")} where $where", args)
            if (changed == 0) {
                preInsertionCallback.accept(file)
                update(
                    "insert into media_file (${MediaFileDaoSql.INSERT_COLUMNS}) values " +
                        "(${questionMarks(MediaFileDaoSql.INSERT_COLUMNS)})",
                    *arrayOf<Any?>(
                        file.path, file.folderId, file.mediaType!!.name, file.format, file.title, file.albumName,
                        file.artist, file.albumArtist, file.discNumber, file.trackNumber, file.year, file.genre,
                        file.bitRate, file.isVariableBitRate, file.duration, file.fileSize, file.width, file.height,
                        file.parentPath, file.playCount, file.lastPlayed, file.comment, file.created, file.changed,
                        file.lastScanned, file.childrenLastUpdated, file.isPresent, MediaFileDao.VERSION,
                        file.musicBrainzReleaseId, file.musicBrainzRecordingId
                    )
                )
            }
            if (file.id == null) {
                file.id = queryForInt("select id from media_file where path=? and folder_id=?", null, file.path, file.folderId)
            }
        }
    }

    fun deleteMediaFiles(paths: Collection<String>, folderId: Int?) {
        if (paths.isNotEmpty()) {
            val batch = paths.parallelStream()
                .map { path -> arrayOf<Any?>(Instant.ofEpochMilli(1), path, folderId) }
                .toList()
            batchedUpdate("update media_file set present=false, children_last_updated=? where path=? and folder_id=?", batch)
        }
    }

    fun deleteMediaFiles(folderId: Int?) {
        update("update media_file set present=false, children_last_updated=? where folder_id=?", Instant.ofEpochMilli(1), folderId)
    }

    fun markPresent(paths: Map<Int, Set<String>>, lastScanned: Instant): Boolean {
        if (paths.isEmpty()) return true
        return paths.entries.parallelStream().map { entry ->
            val values = entry.value.toList()
            val batches = (values.size - 1) / 30000
            (0..batches).toList().parallelStream().mapToInt { batchIndex ->
                val batch = values.subList(batchIndex * 30000, minOf(values.size, batchIndex * 30000 + 30000))
                namedUpdate(
                    "update media_file set present=true, last_scanned = :lastScanned where path in (:paths) and folder_id=:folderId",
                    mapOf("lastScanned" to lastScanned, "paths" to batch, "folderId" to entry.key)
                )
            }.sum() == entry.value.size
        }.reduce(true) { first, second -> first && second }
    }

    fun markNonPresent(lastScanned: Instant) {
        update(
            "update media_file set present=false, children_last_updated=? where last_scanned < ? and present",
            Instant.ofEpochMilli(1), lastScanned
        )
    }

    fun expungeCandidates(type: String): List<Int> = queryForInts(
        "select id from media_file where media_file.type = ? and not present", type
    )

    fun expungeSongCandidates(): List<Int> = queryForInts(
        "select id from media_file where media_file.type in (?,?,?) and not present",
        MediaFile.MediaType.MUSIC.name, MediaFile.MediaType.AUDIOBOOK.name, MediaFile.MediaType.VIDEO.name
    )

    fun expunge() {
        update("delete from media_file where not present")
    }
}
