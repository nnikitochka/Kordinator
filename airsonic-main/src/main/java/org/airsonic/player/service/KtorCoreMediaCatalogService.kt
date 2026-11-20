package org.airsonic.player.service

import javax.sql.DataSource

/** Reads catalog metadata and stable track IDs from Airsonic's core media tables. */
class KtorCoreMediaCatalogService(
    private val coreDataSource: DataSource,
    private val ktorDataSource: DataSource,
) {
    init {
        migrateStoredTrackReferences()
        dropLegacyTrackProjection()
    }

    fun tracks(libraryId: Long?, query: String?, limit: Int, offset: Int, accessibleLibraryIds: Set<Long>? = null): List<StandaloneTrack> {
        val safeLimit = limit.coerceIn(1, 200)
        val safeOffset = offset.coerceAtLeast(0)
        val folderId = libraryId?.toCoreFolderId()
        val pattern = query?.trim()?.takeIf(String::isNotEmpty)?.lowercase()?.let { "%$it%" }
        val sql = "SELECT $TRACK_COLUMNS FROM media_file WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') " +
            "AND (? IS NULL OR folder_id = ?) AND (? IS NULL OR LOWER(COALESCE(title, '')) LIKE ? " +
            "OR LOWER(COALESCE(artist, '')) LIKE ? OR LOWER(COALESCE(album, '')) LIKE ?)" +
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
                val nextParameter = bindLibraryScope(statement, 7, accessibleLibraryIds)
                statement.setInt(nextParameter, safeLimit)
                statement.setInt(nextParameter + 1, safeOffset)
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toCoreTrack()) } }
            }
        }
        return rows.map { it.toStandalone(it.coreId) }
    }

    fun countTracks(libraryId: Long?, query: String?, accessibleLibraryIds: Set<Long>? = null): Long {
        val folderId = libraryId?.toCoreFolderId()
        val pattern = query?.trim()?.takeIf(String::isNotEmpty)?.lowercase()?.let { "%$it%" }
        val sql = "SELECT COUNT(*) FROM media_file WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') " +
            "AND (? IS NULL OR folder_id = ?) AND (? IS NULL OR LOWER(COALESCE(title, '')) LIKE ? " +
            "OR LOWER(COALESCE(artist, '')) LIKE ? OR LOWER(COALESCE(album, '')) LIKE ?)" +
            libraryScope(accessibleLibraryIds)
        return coreDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, folderId)
                statement.setObject(2, folderId)
                statement.setString(3, pattern)
                statement.setString(4, pattern)
                statement.setString(5, pattern)
                statement.setString(6, pattern)
                bindLibraryScope(statement, 7, accessibleLibraryIds)
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

    /** Convert persisted references created before the core catalog became authoritative. */
    private fun migrateStoredTrackReferences() {
        ktorDataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val migrated = connection.prepareStatement(
                    "SELECT 1 FROM standalone_data_migration WHERE migration_id = ?",
                ).use { statement ->
                    statement.setString(1, TRACK_REFERENCE_MIGRATION)
                    statement.executeQuery().use { it.next() }
                }
                if (migrated) {
                    connection.commit()
                    return
                }

                val identities = if (tableExists(connection, "media_track")) connection.prepareStatement(
                    "SELECT id, library_id, relative_path FROM media_track",
                ).use { statement ->
                    statement.executeQuery().use { rows ->
                        buildList { while (rows.next()) add(Triple(rows.getLong(1), rows.getInt(2), rows.getString(3))) }
                    }
                } else emptyList()
                val currentCoreIds = coreDataSource.connection.use { coreConnection ->
                    coreConnection.prepareStatement("SELECT id, folder_id, path FROM media_file WHERE present").use { statement ->
                        statement.executeQuery().use { rows ->
                            buildMap {
                                while (rows.next()) put(rows.getInt(2) to rows.getString(3), rows.getLong(1))
                            }
                        }
                    }
                }
                val coreIds = identities.mapNotNull { (legacyId, libraryId, path) ->
                    currentCoreIds[libraryId to path]?.let { legacyId to it }
                }.toMap()
                migrateFavorites(connection, coreIds)
                migratePlaylistTracks(connection, coreIds)
                connection.prepareStatement("INSERT INTO standalone_data_migration (migration_id, completed_at) VALUES (?, CURRENT_TIMESTAMP)")
                    .use { statement ->
                        statement.setString(1, TRACK_REFERENCE_MIGRATION)
                        statement.executeUpdate()
                    }
                connection.commit()
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    private fun migrateFavorites(connection: java.sql.Connection, coreIds: Map<Long, Long>) {
        val oldFavorites = connection.prepareStatement("SELECT user_id, track_id, created_at FROM favorite_track").use { statement ->
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(Triple(rows.getLong(1), rows.getLong(2), rows.getTimestamp(3))) }
            }
        }
        val migrated = oldFavorites.mapNotNull { (userId, legacyId, createdAt) ->
            coreIds[legacyId]?.let { Triple(userId, it, createdAt) }
        }.distinctBy { it.first to it.second }
        connection.prepareStatement("DELETE FROM favorite_track").use { it.executeUpdate() }
        connection.prepareStatement("INSERT INTO favorite_track (user_id, track_id, created_at) VALUES (?, ?, ?)").use { statement ->
            migrated.forEach { (userId, coreId, createdAt) ->
                statement.setLong(1, userId)
                statement.setLong(2, coreId)
                statement.setTimestamp(3, createdAt)
                statement.addBatch()
            }
            if (migrated.isNotEmpty()) statement.executeBatch()
        }
    }

    private fun migratePlaylistTracks(connection: java.sql.Connection, coreIds: Map<Long, Long>) {
        val oldTracks = connection.prepareStatement("SELECT playlist_id, position, track_id FROM user_playlist_track").use { statement ->
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(Triple(rows.getLong(1), rows.getInt(2), rows.getLong(3))) }
            }
        }
        val migratedTracks = oldTracks.mapNotNull { (playlistId, position, legacyId) ->
            coreIds[legacyId]?.let { Triple(playlistId, position, it) }
        }
        connection.prepareStatement("DELETE FROM user_playlist_track").use { it.executeUpdate() }
        connection.prepareStatement("INSERT INTO user_playlist_track (playlist_id, position, track_id) VALUES (?, ?, ?)").use { statement ->
            migratedTracks.forEach { (playlistId, position, coreId) ->
                statement.setLong(1, playlistId)
                statement.setInt(2, position)
                statement.setLong(3, coreId)
                statement.addBatch()
            }
            if (migratedTracks.isNotEmpty()) statement.executeBatch()
        }
    }

    private fun dropLegacyTrackProjection() {
        ktorDataSource.connection.use { connection ->
            if (tableExists(connection, "media_track")) {
                connection.createStatement().use { it.execute("DROP TABLE media_track") }
            }
        }
    }

    private fun tableExists(connection: java.sql.Connection, tableName: String): Boolean =
        connection.metaData.getTables(connection.catalog, null, null, arrayOf("TABLE")).use { tables ->
            var exists = false
            while (tables.next()) {
                if (tables.getString("TABLE_NAME").equals(tableName, ignoreCase = true)) exists = true
            }
            exists
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

    fun albums(libraryId: Long?, limit: Int, offset: Int, accessibleLibraryIds: Set<Long>? = null): List<StandaloneAlbum> {
        val folderId = libraryId?.toCoreFolderId()
        val sql = "SELECT folder_id, album, MAX(COALESCE(NULLIF(TRIM(album_artist), ''), TRIM(artist))) AS album_artist, " +
            "MAX(year) AS year, MAX(genre) AS genre, COUNT(*) AS track_count, " +
            "CAST(SUM(COALESCE(duration, 0)) AS BIGINT) AS duration_seconds FROM media_file " +
            "WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') AND (? IS NULL OR folder_id = ?) " +
            "AND album IS NOT NULL AND TRIM(album) <> ''" + libraryScope(accessibleLibraryIds) + " " +
            "GROUP BY folder_id, album " +
            "ORDER BY LOWER(album), folder_id LIMIT ? OFFSET ?"
        return coreDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, folderId)
                statement.setObject(2, folderId)
                val nextParameter = bindLibraryScope(statement, 3, accessibleLibraryIds)
                statement.setInt(nextParameter, limit.coerceIn(1, 100))
                statement.setInt(nextParameter + 1, offset.coerceAtLeast(0))
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(
                            StandaloneAlbum(
                                rows.getLong("folder_id"), rows.getString("album"), rows.getString("album_artist"),
                                rows.getObject("year")?.let { rows.getInt("year") }, rows.getString("genre"),
                                rows.getLong("track_count"), rows.getLong("duration_seconds"),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun artists(libraryId: Long?, limit: Int, offset: Int, accessibleLibraryIds: Set<Long>? = null): List<StandaloneArtist> {
        val folderId = libraryId?.toCoreFolderId()
        val artistExpression = "COALESCE(NULLIF(TRIM(album_artist), ''), TRIM(artist))"
        val sql = "SELECT folder_id, $artistExpression AS artist_name, " +
            "COUNT(DISTINCT NULLIF(TRIM(album), '')) AS album_count, COUNT(*) AS track_count " +
            "FROM media_file WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') AND (? IS NULL OR folder_id = ?) " +
            "AND $artistExpression IS NOT NULL" + libraryScope(accessibleLibraryIds) + " " +
            "GROUP BY folder_id, $artistExpression " +
            "ORDER BY LOWER(artist_name), folder_id LIMIT ? OFFSET ?"
        return coreDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, folderId)
                statement.setObject(2, folderId)
                val nextParameter = bindLibraryScope(statement, 3, accessibleLibraryIds)
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
    ) {
        fun toStandalone(id: Long) = StandaloneTrack(
            id, libraryId.toLong(), relativePath, title, artist, album, albumArtist, discNumber, trackNumber,
            year, genre, durationSeconds, sizeBytes, modifiedAt,
        )
    }

    private fun java.sql.ResultSet.toCoreTrack() = CoreTrack(
        getLong("id"), getInt("folder_id"), getString("path"), getString("title") ?: getString("path").substringAfterLast('/'),
        getString("artist"), getString("album"), getString("album_artist"),
        getObject("disc_number")?.let { getInt("disc_number") }, getObject("track_number")?.let { getInt("track_number") },
        getObject("year")?.let { getInt("year") }, getString("genre"), getDouble("duration").toInt().coerceAtLeast(0),
        getLong("file_size"), getTimestamp("changed")?.toInstant(),
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
        const val TRACK_REFERENCE_MIGRATION = "core-media-track-references-v1"
        const val TRACK_COLUMNS = "id, folder_id, path, title, artist, album, album_artist, disc_number, " +
            "track_number, year, genre, duration, file_size, changed"
    }
}
