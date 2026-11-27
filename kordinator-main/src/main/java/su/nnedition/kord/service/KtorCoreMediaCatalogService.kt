package su.nnedition.kord.service

import su.nnedition.kord.model.ReleaseDate

import su.nnedition.kord.service.library.scan.StandaloneAlbum
import su.nnedition.kord.service.library.scan.StandaloneArtist
import su.nnedition.kord.service.library.scan.StandaloneTrack
import javax.sql.DataSource
import org.apache.commons.lang3.StringUtils

/** Reads catalog metadata and stable track IDs from Kordinator's core media tables. */
class KtorCoreMediaCatalogService(
    private val coreDataSource: DataSource,
    private val ktorDataSource: DataSource,
) {
    fun genres(accessibleLibraryIds: Set<Long>?, separators: String): List<CatalogGenre> {
        val rawGenres = coreDataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT type, genre FROM media_file WHERE present AND genre IS NOT NULL " +
                    "AND type IN ('ALBUM', 'MUSIC', 'AUDIOBOOK')" + libraryScope(accessibleLibraryIds),
            ).use { statement ->
                bindLibraryScope(statement, 1, accessibleLibraryIds)
                statement.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(rows.getString("type") to rows.getString("genre")) }
                }
            }
        }
        data class Counts(var tracks: Int = 0, var albums: Int = 0)
        val counts = linkedMapOf<String, Counts>()
        rawGenres.forEach { (type, name) ->
            val targets = mutableListOf(name)
            val split = StringUtils.split(name, separators)
            if (split.size > 1) targets += split.map(String::trim).filter(String::isNotBlank)
            targets.forEach { genre ->
                val aggregate = counts.getOrPut(genre) { Counts() }
                if (type == "ALBUM") aggregate.albums++ else aggregate.tracks++
            }
        }
        return counts.map { (name, value) -> CatalogGenre(name, value.tracks, value.albums) }
            .sortedBy { it.name.lowercase() }
    }

    fun tracks(
        libraryId: Long?,
        query: String?,
        limit: Int,
        offset: Int,
        accessibleLibraryIds: Set<Long>? = null,
        genre: String? = null,
    ): List<StandaloneTrack> {
        val safeLimit = limit.coerceIn(1, 200)
        val safeOffset = offset.coerceAtLeast(0)
        val folderId = libraryId?.toCoreFolderId()
        val pattern = query?.trim()?.takeIf(String::isNotEmpty)?.lowercase()?.let { "%$it%" }
        val sql = "SELECT $TRACK_COLUMNS FROM media_file WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') " +
            "AND (? IS NULL OR folder_id = ?) AND (? IS NULL OR LOWER(COALESCE(title, '')) LIKE ? " +
            "OR LOWER(COALESCE(artist, '')) LIKE ? OR LOWER(COALESCE(album, '')) LIKE ?) " +
            "AND (? IS NULL OR genre = ?)" +
            libraryScope(accessibleLibraryIds) + " " +
            "ORDER BY LOWER(COALESCE(artist, '')), LOWER(COALESCE(album, '')), LOWER(COALESCE(title, '')), id " +
            "LIMIT ? OFFSET ?"
        val rows = coreDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, folderId)
                statement.setObject(2, folderId)
                statement.setString(3, pattern)
                statement.setString(4, pattern)
                statement.setString(5, pattern)
                statement.setString(6, pattern)
                statement.setString(7, genre)
                statement.setString(8, genre)
                val nextParameter = bindLibraryScope(statement, 9, accessibleLibraryIds)
                statement.setInt(nextParameter, safeLimit)
                statement.setInt(nextParameter + 1, safeOffset)
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toCoreTrack()) } }
            }
        }
        return rows.map { it.toStandalone(it.coreId) }
    }

    fun countTracks(
        libraryId: Long?,
        query: String?,
        accessibleLibraryIds: Set<Long>? = null,
        genre: String? = null,
    ): Long {
        val folderId = libraryId?.toCoreFolderId()
        val pattern = query?.trim()?.takeIf(String::isNotEmpty)?.lowercase()?.let { "%$it%" }
        val sql = "SELECT COUNT(*) FROM media_file WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') " +
            "AND (? IS NULL OR folder_id = ?) AND (? IS NULL OR LOWER(COALESCE(title, '')) LIKE ? " +
            "OR LOWER(COALESCE(artist, '')) LIKE ? OR LOWER(COALESCE(album, '')) LIKE ?) " +
            "AND (? IS NULL OR genre = ?)" +
            libraryScope(accessibleLibraryIds)
        return coreDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, folderId)
                statement.setObject(2, folderId)
                statement.setString(3, pattern)
                statement.setString(4, pattern)
                statement.setString(5, pattern)
                statement.setString(6, pattern)
                statement.setString(7, genre)
                statement.setString(8, genre)
                bindLibraryScope(statement, 9, accessibleLibraryIds)
                statement.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
            }
        }
    }

    fun track(trackId: Long, accessibleLibraryIds: Set<Long>? = null): StandaloneTrack? {
        val coreTrack = coreDataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT $TRACK_COLUMNS FROM media_file WHERE id = ? AND present AND type IN ('MUSIC', 'AUDIOBOOK')" +
                    libraryScope(accessibleLibraryIds),
            ).use { statement ->
                statement.setLong(1, trackId)
                bindLibraryScope(statement, 2, accessibleLibraryIds)
                statement.executeQuery().use { rows -> if (rows.next()) rows.toCoreTrack() else null }
            }
        } ?: return null
        return coreTrack.toStandalone(coreTrack.coreId)
    }

    fun tracksByCoreIds(coreTrackIds: List<Long>, accessibleLibraryIds: Set<Long>? = null): List<StandaloneTrack> {
        if (coreTrackIds.isEmpty()) return emptyList()
        val coreTracks = coreTrackIds.distinct().chunked(500).flatMap { ids ->
            val placeholders = ids.joinToString(",") { "?" }
            coreDataSource.connection.use { connection ->
                connection.prepareStatement(
                    "SELECT $TRACK_COLUMNS FROM media_file WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') " +
                        "AND id IN ($placeholders)" + libraryScope(accessibleLibraryIds),
                ).use { statement ->
                    ids.forEachIndexed { index, id -> statement.setLong(index + 1, id) }
                    bindLibraryScope(statement, ids.size + 1, accessibleLibraryIds)
                    statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.toCoreTrack()) } }
                }
            }
        }.associateBy { it.coreId }
        return coreTrackIds.mapNotNull { id -> coreTracks[id]?.toStandalone(id) }
    }

    fun tracksByRelativePaths(paths: Set<String>, accessibleLibraryIds: Set<Long>? = null): Map<String, List<StandaloneTrack>> {
        if (paths.isEmpty()) return emptyMap()
        return paths.toList().chunked(400).flatMap { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val sql = "SELECT $TRACK_COLUMNS FROM media_file WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') " +
                "AND path IN ($placeholders)" + libraryScope(accessibleLibraryIds)
            coreDataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    chunk.forEachIndexed { index, path -> statement.setString(index + 1, path) }
                    bindLibraryScope(statement, chunk.size + 1, accessibleLibraryIds)
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                val track = rows.toCoreTrack()
                                add(track.toStandalone(track.coreId))
                            }
                        }
                    }
                }
            }
        }.groupBy { it.relativePath }
    }

    fun tracksForAlbum(libraryId: Long, album: String, accessibleLibraryIds: Set<Long>? = null): List<StandaloneTrack> = tracksForCatalogEntry(
        libraryId,
        "album = ?",
        album,
        accessibleLibraryIds,
    )

    fun tracksForArtist(libraryId: Long, artist: String, accessibleLibraryIds: Set<Long>? = null): List<StandaloneTrack> = tracksForCatalogEntry(
        libraryId,
        "COALESCE(NULLIF(TRIM(album_artist), ''), TRIM(artist)) = ?",
        artist,
        accessibleLibraryIds,
    )

    fun album(libraryId: Long, name: String, accessibleLibraryIds: Set<Long>? = null): StandaloneAlbum? {
        val tracks = tracksForAlbum(libraryId, name, accessibleLibraryIds)
        if (tracks.isEmpty()) return null
        val date = ReleaseDate.mostCommon(tracks.map { it.releaseDate })
        return StandaloneAlbum(
            libraryId,
            name,
            tracks.first().albumArtist ?: tracks.first().artist,
            date?.year,
            tracks.firstNotNullOfOrNull { it.genre },
            tracks.size.toLong(),
            tracks.sumOf { it.durationSeconds.toLong() },
            date,
        )
    }

    fun artist(libraryId: Long, name: String, accessibleLibraryIds: Set<Long>? = null): StandaloneArtist? {
        val tracks = tracksForArtist(libraryId, name, accessibleLibraryIds)
        if (tracks.isEmpty()) return null
        return StandaloneArtist(
            libraryId,
            name,
            tracks.mapNotNull { it.album?.takeIf(String::isNotBlank) }.distinct().size.toLong(),
            tracks.size.toLong(),
        )
    }

    fun albums(
        libraryId: Long?,
        limit: Int,
        offset: Int,
        accessibleLibraryIds: Set<Long>? = null,
        sort: String = "name",
        genre: String? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
    ): List<StandaloneAlbum> {
        val folderId = libraryId?.toCoreFolderId()
        require(sort in ALBUM_SORTS) { "Unsupported album sort" }
        require((fromYear == null) == (toYear == null)) { "Both year bounds must be provided" }
        require(fromYear == null || fromYear <= requireNotNull(toYear)) { "Year range is reversed" }
        if (sort == "highest-rated") {
            return albumsByRating(libraryId, limit, offset, accessibleLibraryIds, genre, fromYear, toYear)
        }
        val sql = AlbumReleaseDateSql.ctes + " SELECT folder_id, album, MAX(COALESCE(NULLIF(TRIM(album_artist), ''), TRIM(artist))) AS album_artist, " +
            "MAX(release_year) AS year, MAX(release_month) AS release_month, MAX(release_day) AS release_day, MAX(genre) AS genre, COUNT(*) AS track_count, " +
            "CAST(SUM(COALESCE(duration, 0)) AS BIGINT) AS duration_seconds FROM media_file LEFT JOIN album_release_dates USING (folder_id, album) " +
            "WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') AND (? IS NULL OR folder_id = ?) " +
            "AND album IS NOT NULL AND TRIM(album) <> ''" + libraryScope(accessibleLibraryIds) + " " +
            "GROUP BY folder_id, album HAVING (? IS NULL OR MAX(genre) = ?) " +
            "AND (? IS NULL OR MAX(release_year) >= ?) AND (? IS NULL OR MAX(release_year) <= ?) " +
            "ORDER BY ${albumOrder(sort)} LIMIT ? OFFSET ?"
        return coreDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, folderId)
                statement.setObject(2, folderId)
                var nextParameter = bindLibraryScope(statement, 3, accessibleLibraryIds)
                statement.setString(nextParameter++, genre)
                statement.setString(nextParameter++, genre)
                statement.setObject(nextParameter++, fromYear)
                statement.setObject(nextParameter++, fromYear)
                statement.setObject(nextParameter++, toYear)
                statement.setObject(nextParameter++, toYear)
                statement.setInt(nextParameter, limit.coerceIn(1, 100))
                statement.setInt(nextParameter + 1, offset.coerceAtLeast(0))
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(
                            StandaloneAlbum(
                                rows.getLong("folder_id"), rows.getString("album"), rows.getString("album_artist"),
                                rows.getObject("year")?.let { rows.getInt("year") }, rows.getString("genre"),
                                rows.getLong("track_count"), rows.getLong("duration_seconds"),
                                rows.getObject("year")?.let {
                                    ReleaseDate(
                                        rows.getInt("year"), rows.getObject("release_month")?.let { rows.getInt("release_month") },
                                        rows.getObject("release_day")?.let { rows.getInt("release_day") },
                                    )
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    fun countAlbums(
        libraryId: Long?,
        accessibleLibraryIds: Set<Long>? = null,
        genre: String? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
    ): Long {
        val folderId = libraryId?.toCoreFolderId()
        require((fromYear == null) == (toYear == null)) { "Both year bounds must be provided" }
        require(fromYear == null || fromYear <= requireNotNull(toYear)) { "Year range is reversed" }
        val sql = AlbumReleaseDateSql.ctes + " SELECT COUNT(*) FROM (SELECT folder_id, album FROM media_file LEFT JOIN album_release_dates USING (folder_id, album) " +
            "WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') AND (? IS NULL OR folder_id = ?) " +
            "AND album IS NOT NULL AND TRIM(album) <> ''" + libraryScope(accessibleLibraryIds) + " " +
            "GROUP BY folder_id, album HAVING (? IS NULL OR MAX(genre) = ?) " +
            "AND (? IS NULL OR MAX(release_year) >= ?) AND (? IS NULL OR MAX(release_year) <= ?)) catalog_albums"
        return coreDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, folderId)
                statement.setObject(2, folderId)
                var nextParameter = bindLibraryScope(statement, 3, accessibleLibraryIds)
                statement.setString(nextParameter++, genre)
                statement.setString(nextParameter++, genre)
                statement.setObject(nextParameter++, fromYear)
                statement.setObject(nextParameter++, fromYear)
                statement.setObject(nextParameter++, toYear)
                statement.setObject(nextParameter, toYear)
                statement.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
            }
        }
    }

    private fun albumOrder(sort: String): String = when (sort) {
        "name" -> "LOWER(album), album, folder_id"
        "artist" -> "LOWER(album_artist), album_artist, LOWER(album), album, folder_id"
        "year" -> "MAX(release_year), LOWER(album), album, folder_id"
        "newest" -> "MAX(created) DESC, LOWER(album), album, folder_id"
        "most-played" -> "SUM(COALESCE(play_count, 0)) DESC, LOWER(album), album, folder_id"
        "recently-played" -> "MAX(last_played) DESC, LOWER(album), album, folder_id"
        "random" -> when (coreDataSource.connection.use { it.metaData.databaseProductName.lowercase() }) {
            "hsql database engine", "h2" -> "RAND()"
            "postgresql" -> "RANDOM()"
            "mysql", "mariadb" -> "RAND()"
            else -> throw IllegalArgumentException("Random album ordering is not supported by this database")
        }
        "highest-rated" -> error("Highest-rated albums use user rating data")
        else -> error("Unsupported album sort")
    }

    private fun albumsByRating(
        libraryId: Long?,
        limit: Int,
        offset: Int,
        accessibleLibraryIds: Set<Long>?,
        genre: String?,
        fromYear: Int?,
        toYear: Int?,
    ): List<StandaloneAlbum> {
        val total = countAlbums(libraryId, accessibleLibraryIds, genre, fromYear, toYear)
        val candidates = buildList {
            var currentOffset = 0
            while (currentOffset < total) {
                val pageSize = minOf(ALBUM_PAGE_SIZE, (total - currentOffset).toInt())
                addAll(albums(
                    libraryId, pageSize, currentOffset, accessibleLibraryIds,
                    "name", genre, fromYear, toYear,
                ))
                currentOffset += pageSize
            }
        }
        if (candidates.isEmpty()) return emptyList()

        val libraryFilter = when {
            accessibleLibraryIds == null -> ""
            accessibleLibraryIds.isEmpty() -> " WHERE 1 = 0"
            else -> " WHERE library_id IN (${accessibleLibraryIds.joinToString(",") { "?" }})"
        }
        val ratings = ktorDataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT library_id, album_name, AVG(rating) AS average_rating FROM album_rating" +
                    libraryFilter + " GROUP BY library_id, album_name",
            ).use { statement ->
                accessibleLibraryIds?.sorted()?.forEachIndexed { index, id -> statement.setLong(index + 1, id) }
                statement.executeQuery().use { rows ->
                    buildMap {
                        while (rows.next()) {
                            put(rows.getLong("library_id") to rows.getString("album_name"), rows.getDouble("average_rating"))
                        }
                    }
                }
            }
        }
        return candidates.sortedWith(
            compareByDescending<StandaloneAlbum> { ratings[it.libraryId to it.name] ?: -1.0 }
                .thenBy { it.name.lowercase() }.thenBy { it.libraryId },
        ).drop(offset.coerceAtLeast(0)).take(limit.coerceIn(1, 100))
    }

    fun artists(
        libraryId: Long?,
        limit: Int,
        offset: Int,
        accessibleLibraryIds: Set<Long>? = null,
        prefix: String? = null,
    ): List<StandaloneArtist> {
        val folderId = libraryId?.toCoreFolderId()
        val artistExpression = "COALESCE(NULLIF(TRIM(album_artist), ''), TRIM(artist))"
        val prefixPattern = prefix?.trim()?.takeIf(String::isNotEmpty)?.let(::artistSearchPattern)
        val sql = "SELECT folder_id, $artistExpression AS artist_name, " +
            "COUNT(DISTINCT NULLIF(TRIM(album), '')) AS album_count, COUNT(*) AS track_count " +
            "FROM media_file WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') AND (? IS NULL OR folder_id = ?) " +
            "AND $artistExpression IS NOT NULL AND (? IS NULL OR LOWER($artistExpression) LIKE ? ESCAPE '!')" +
            libraryScope(accessibleLibraryIds) + " " +
            "GROUP BY folder_id, $artistExpression " +
            "ORDER BY LOWER(artist_name), artist_name, folder_id LIMIT ? OFFSET ?"
        return coreDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, folderId)
                statement.setObject(2, folderId)
                statement.setString(3, prefixPattern)
                statement.setString(4, prefixPattern)
                val nextParameter = bindLibraryScope(statement, 5, accessibleLibraryIds)
                statement.setInt(nextParameter, limit.coerceIn(1, 100))
                statement.setInt(nextParameter + 1, offset.coerceAtLeast(0))
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(
                            StandaloneArtist(
                                rows.getLong("folder_id"), rows.getString("artist_name"),
                                rows.getLong("album_count"), rows.getLong("track_count"),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun countArtists(libraryId: Long?, accessibleLibraryIds: Set<Long>? = null, prefix: String? = null): Long {
        val artistExpression = "COALESCE(NULLIF(TRIM(album_artist), ''), TRIM(artist))"
        val folderId = libraryId?.toCoreFolderId()
        val prefixPattern = prefix?.trim()?.takeIf(String::isNotEmpty)?.let(::artistSearchPattern)
        val sql = "SELECT COUNT(*) FROM (SELECT folder_id, $artistExpression AS catalog_entry FROM media_file " +
            "WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') AND (? IS NULL OR folder_id = ?) " +
            "AND $artistExpression IS NOT NULL AND (? IS NULL OR LOWER($artistExpression) LIKE ? ESCAPE '!')" +
            libraryScope(accessibleLibraryIds) + " GROUP BY folder_id, $artistExpression) catalog_entries"
        return coreDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, folderId)
                statement.setObject(2, folderId)
                statement.setString(3, prefixPattern)
                statement.setString(4, prefixPattern)
                bindLibraryScope(statement, 5, accessibleLibraryIds)
                statement.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
            }
        }
    }

    private fun artistSearchPattern(query: String): String =
        "%" + query.lowercase().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"

    private fun countCatalogEntries(
        entryExpression: String,
        entryCondition: String,
        libraryId: Long?,
        accessibleLibraryIds: Set<Long>?,
    ): Long {
        val folderId = libraryId?.toCoreFolderId()
        val sql = "SELECT COUNT(*) FROM (SELECT folder_id, $entryExpression AS catalog_entry FROM media_file " +
            "WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') AND (? IS NULL OR folder_id = ?) " +
            "AND $entryCondition" + libraryScope(accessibleLibraryIds) +
            " GROUP BY folder_id, $entryExpression) catalog_entries"
        return coreDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, folderId)
                statement.setObject(2, folderId)
                bindLibraryScope(statement, 3, accessibleLibraryIds)
                statement.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
            }
        }
    }

    private fun tracksForCatalogEntry(
        libraryId: Long,
        predicate: String,
        value: String,
        accessibleLibraryIds: Set<Long>?,
    ): List<StandaloneTrack> {
        val sql = "SELECT $TRACK_COLUMNS FROM media_file WHERE folder_id = ? AND present " +
            "AND type IN ('MUSIC', 'AUDIOBOOK') AND $predicate" + libraryScope(accessibleLibraryIds) + " " +
            "ORDER BY COALESCE(disc_number, 0), COALESCE(track_number, 0), LOWER(COALESCE(title, '')), id"
        val rows = coreDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, libraryId)
                statement.setString(2, value)
                bindLibraryScope(statement, 3, accessibleLibraryIds)
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toCoreTrack()) } }
            }
        }
        return rows.map { it.toStandalone(it.coreId) }
    }

    private data class CoreTrack(
        val coreId: Long,
        val libraryId: Int,
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
        val modifiedAt: java.time.Instant?,
        val releaseDate: ReleaseDate?,
    ) {
        fun toStandalone(id: Long) = StandaloneTrack(
            id, libraryId.toLong(), relativePath, title, artist, album, albumArtist, discNumber, trackNumber,
            year, genre, durationSeconds, sizeBytes, modifiedAt, releaseDate,
        )
    }

    private fun java.sql.ResultSet.toCoreTrack() = CoreTrack(
        getLong("id"), getInt("folder_id"), getString("path"), getString("title") ?: getString("path").substringAfterLast('/'),
        getString("artist"), getString("album"), getString("album_artist"),
        getObject("disc_number")?.let { getInt("disc_number") }, getObject("track_number")?.let { getInt("track_number") },
        getObject("year")?.let { getInt("year") }, getString("genre"), getDouble("duration").toInt().coerceAtLeast(0),
        getLong("file_size"), getTimestamp("changed")?.toInstant(),
        ReleaseDate.parse(getString("release_date"))
            ?: ReleaseDate.fromYear(getObject("year")?.let { getInt("year") }),
    )

    private fun Long.toCoreFolderId(): Int {
        require(this in 0..Int.MAX_VALUE.toLong()) { "Music folder ID is outside the supported range" }
        return toInt()
    }

    private fun libraryScope(libraryIds: Set<Long>?): String = when {
        libraryIds == null -> ""
        libraryIds.isEmpty() -> " AND 1 = 0"
        else -> " AND folder_id IN (${libraryIds.joinToString(",") { "?" }})"
    }

    private fun bindLibraryScope(statement: java.sql.PreparedStatement, firstIndex: Int, libraryIds: Set<Long>?): Int {
        if (libraryIds == null || libraryIds.isEmpty()) return firstIndex
        libraryIds.sorted().forEachIndexed { index, id -> statement.setLong(firstIndex + index, id) }
        return firstIndex + libraryIds.size
    }

    private companion object {
        val ALBUM_SORTS = setOf(
            "name", "artist", "year", "newest", "most-played", "recently-played", "random", "highest-rated",
        )
        const val ALBUM_PAGE_SIZE = 100
        const val TRACK_COLUMNS = "id, folder_id, path, title, artist, album, album_artist, disc_number, " +
            "track_number, year, genre, duration, file_size, changed, release_date"
    }
}

data class CatalogGenre(val name: String, val trackCount: Int, val albumCount: Int)
