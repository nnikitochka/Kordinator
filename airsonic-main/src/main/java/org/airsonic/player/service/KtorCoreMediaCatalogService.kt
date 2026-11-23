package org.airsonic.player.service

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource
import org.apache.commons.lang3.StringUtils

/** Reads catalog metadata and stable track IDs from Airsonic's core media tables. */
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

    init {
        migrateStoredTrackReferences()
        migrateLegacyBookmarks()
        migrateLegacyRatings()
        migrateLegacyFavorites()
        migrateLegacyPlaylists()
        dropLegacyTrackProjection()
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

    /** Copy existing bookmark rows to the bearer-account store when usernames still match. */
    private fun migrateLegacyBookmarks() {
        ktorDataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val migrationId = "legacy-media-bookmarks-1"
                val completed = connection.prepareStatement(
                    "SELECT 1 FROM standalone_data_migration WHERE migration_id = ?",
                ).use { statement ->
                    statement.setString(1, migrationId)
                    statement.executeQuery().use { it.next() }
                }
                if (completed) {
                    connection.commit()
                    return
                }

                val userIds = connection.prepareStatement("SELECT id, username FROM local_user").use { statement ->
                    statement.executeQuery().use { rows ->
                        buildMap { while (rows.next()) put(rows.getString("username"), rows.getLong("id")) }
                    }
                }
                val hasLegacyBookmarkTable = coreDataSource.connection.use { tableExists(it, "bookmark") }
                var pendingAccountMatch = false
                if (hasLegacyBookmarkTable) {
                    val bookmarks = coreDataSource.connection.use { coreConnection ->
                        coreConnection.prepareStatement(
                            "SELECT b.username, b.media_file_id, b.position_millis, b.comment, b.created, b.changed, " +
                                "m.type AS media_type FROM bookmark b JOIN media_file m ON m.id = b.media_file_id " +
                                "WHERE m.present",
                        ).use { statement ->
                            statement.executeQuery().use { rows ->
                                buildList {
                                    while (rows.next()) {
                                        add(
                                            LegacyBookmarkRow(
                                                rows.getString("username"), rows.getInt("media_file_id"),
                                                rows.getLong("position_millis"), rows.getString("comment"),
                                                rows.getTimestamp("created")?.toInstant() ?: Instant.now(),
                                                rows.getTimestamp("changed")?.toInstant() ?: Instant.now(),
                                                rows.getString("media_type"),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    bookmarks.forEach { bookmark ->
                        val userId = userIds[bookmark.username]
                        if (userId == null) {
                            pendingAccountMatch = true
                            return@forEach
                        }
                        when (bookmark.mediaType) {
                            "VIDEO" -> migrateVideoBookmark(connection, userId, bookmark)
                            "MUSIC", "AUDIOBOOK" -> migrateTrackBookmark(connection, userId, bookmark)
                        }
                    }
                }
                if (!pendingAccountMatch) {
                    connection.prepareStatement(
                        "INSERT INTO standalone_data_migration (migration_id, completed_at) VALUES (?, CURRENT_TIMESTAMP)",
                    ).use { statement ->
                        statement.setString(1, migrationId)
                        statement.executeUpdate()
                    }
                }
                connection.commit()
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    private fun migrateTrackBookmark(connection: java.sql.Connection, userId: Long, bookmark: LegacyBookmarkRow) {
        if (bookmarkExists(connection, "track_bookmark", userId, bookmark.mediaFileId.toLong())) return
        connection.prepareStatement(
            "INSERT INTO track_bookmark (user_id, track_id, position_ms, comment, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.setLong(2, bookmark.mediaFileId.toLong())
            statement.setLong(3, bookmark.positionMillis)
            statement.setString(4, bookmark.comment)
            statement.setTimestamp(5, Timestamp.from(bookmark.createdAt))
            statement.setTimestamp(6, Timestamp.from(bookmark.updatedAt))
            statement.executeUpdate()
        }
    }

    private fun migrateVideoBookmark(connection: java.sql.Connection, userId: Long, bookmark: LegacyBookmarkRow) {
        if (bookmarkExists(connection, "video_bookmark", userId, bookmark.mediaFileId.toLong())) return
        connection.prepareStatement(
            "INSERT INTO video_bookmark (user_id, video_id, position_ms, comment, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.setInt(2, bookmark.mediaFileId)
            statement.setLong(3, bookmark.positionMillis)
            statement.setString(4, bookmark.comment)
            statement.setTimestamp(5, Timestamp.from(bookmark.createdAt))
            statement.setTimestamp(6, Timestamp.from(bookmark.updatedAt))
            statement.executeUpdate()
        }
    }

    private fun bookmarkExists(connection: java.sql.Connection, table: String, userId: Long, mediaId: Long): Boolean =
        connection.prepareStatement("SELECT 1 FROM $table WHERE user_id = ? AND ${if (table == "video_bookmark") "video_id" else "track_id"} = ?")
            .use { statement ->
                statement.setLong(1, userId)
                statement.setLong(2, mediaId)
                statement.executeQuery().use { it.next() }
            }

    private data class LegacyBookmarkRow(
        val username: String,
        val mediaFileId: Int,
        val positionMillis: Long,
        val comment: String?,
        val createdAt: Instant,
        val updatedAt: Instant,
        val mediaType: String,
    )

    /** Copy account ratings from the legacy media-file table into Kordinator resources. */
    private fun migrateLegacyRatings() {
        ktorDataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val migrationId = "legacy-media-ratings-1"
                val completed = connection.prepareStatement(
                    "SELECT 1 FROM standalone_data_migration WHERE migration_id = ?",
                ).use { statement ->
                    statement.setString(1, migrationId)
                    statement.executeQuery().use { it.next() }
                }
                if (completed) {
                    connection.commit()
                    return
                }

                val hasLegacyRatings = coreDataSource.connection.use { tableExists(it, "user_rating") }
                var pendingAccountMatch = false
                if (hasLegacyRatings) {
                    val userIds = connection.prepareStatement("SELECT id, username FROM local_user").use { statement ->
                        statement.executeQuery().use { rows ->
                            buildMap { while (rows.next()) put(rows.getString("username"), rows.getLong("id")) }
                        }
                    }
                    val ratings = coreDataSource.connection.use { coreConnection ->
                        coreConnection.prepareStatement(
                            "SELECT r.username, r.media_file_id, r.rating, m.type AS media_type, " +
                                "m.folder_id, m.album FROM user_rating r JOIN media_file m ON m.id = r.media_file_id " +
                                "WHERE m.present",
                        ).use { statement ->
                            statement.executeQuery().use { rows ->
                                buildList {
                                    while (rows.next()) {
                                        add(
                                            LegacyRatingRow(
                                                rows.getString("username"), rows.getLong("media_file_id"),
                                                rows.getInt("rating"), rows.getString("media_type"),
                                                rows.getLong("folder_id"), rows.getString("album"),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    ratings.forEach { rating ->
                        val userId = userIds[rating.username]
                        if (userId == null) {
                            pendingAccountMatch = true
                            return@forEach
                        }
                        if (rating.value !in 1..5) return@forEach
                        when (rating.mediaType) {
                            "MUSIC", "AUDIOBOOK" -> migrateTrackRating(connection, userId, rating)
                            "ALBUM" -> migrateAlbumRating(connection, userId, rating)
                        }
                    }
                }
                if (!pendingAccountMatch) {
                    connection.prepareStatement(
                        "INSERT INTO standalone_data_migration (migration_id, completed_at) VALUES (?, CURRENT_TIMESTAMP)",
                    ).use { statement ->
                        statement.setString(1, migrationId)
                        statement.executeUpdate()
                    }
                }
                connection.commit()
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    private fun migrateTrackRating(connection: java.sql.Connection, userId: Long, rating: LegacyRatingRow) {
        if (ratingExists(connection, "track_rating", userId, rating.mediaFileId)) return
        connection.prepareStatement(
            "INSERT INTO track_rating (user_id, track_id, rating, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.setLong(2, rating.mediaFileId)
            statement.setInt(3, rating.value)
            statement.executeUpdate()
        }
    }

    private fun migrateAlbumRating(connection: java.sql.Connection, userId: Long, rating: LegacyRatingRow) {
        val albumName = rating.albumName?.trim()?.takeIf(String::isNotEmpty) ?: return
        if (ratingExists(connection, "album_rating", userId, rating.libraryId, albumName)) return
        connection.prepareStatement(
            "INSERT INTO album_rating (user_id, library_id, album_name, rating, updated_at) " +
                "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.setLong(2, rating.libraryId)
            statement.setString(3, albumName)
            statement.setInt(4, rating.value)
            statement.executeUpdate()
        }
    }

    private fun ratingExists(connection: java.sql.Connection, table: String, userId: Long, resourceId: Long): Boolean =
        connection.prepareStatement("SELECT 1 FROM $table WHERE user_id = ? AND ${if (table == "track_rating") "track_id" else "library_id"} = ?")
            .use { statement ->
                statement.setLong(1, userId)
                statement.setLong(2, resourceId)
                statement.executeQuery().use { it.next() }
            }

    private fun ratingExists(connection: java.sql.Connection, table: String, userId: Long, libraryId: Long, albumName: String): Boolean =
        connection.prepareStatement(
            "SELECT 1 FROM $table WHERE user_id = ? AND library_id = ? AND album_name = ?",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.setLong(2, libraryId)
            statement.setString(3, albumName)
            statement.executeQuery().use { it.next() }
        }

    private data class LegacyRatingRow(
        val username: String,
        val mediaFileId: Long,
        val value: Int,
        val mediaType: String,
        val libraryId: Long,
        val albumName: String?,
    )

    /** Copy legacy starred songs, albums and artists to account-scoped Kordinator favorites. */
    private fun migrateLegacyFavorites() {
        ktorDataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val migrationId = "legacy-media-favorites-1"
                val completed = connection.prepareStatement(
                    "SELECT 1 FROM standalone_data_migration WHERE migration_id = ?",
                ).use { statement ->
                    statement.setString(1, migrationId)
                    statement.executeQuery().use { it.next() }
                }
                if (completed) {
                    connection.commit()
                    return
                }
                val userIds = connection.prepareStatement("SELECT id, username FROM local_user").use { statement ->
                    statement.executeQuery().use { rows ->
                        buildMap { while (rows.next()) put(rows.getString("username"), rows.getLong("id")) }
                    }
                }
                var pendingAccountMatch = false
                if (coreTableExists("starred_media_file")) {
                    val favorites = coreDataSource.connection.use { coreConnection ->
                        coreConnection.prepareStatement(
                            "SELECT s.username, s.created, m.id AS media_id, m.type AS media_type, " +
                                "m.folder_id, m.album, m.artist, m.title, m.path FROM starred_media_file s " +
                                "JOIN media_file m ON m.id = s.media_file_id WHERE m.present",
                        ).use { statement ->
                            statement.executeQuery().use { rows ->
                                buildList {
                                    while (rows.next()) {
                                        add(
                                            LegacyFavoriteRow(
                                                rows.getString("username"), rows.getTimestamp("created")?.toInstant() ?: Instant.now(),
                                                rows.getLong("media_id"), rows.getString("media_type"), rows.getLong("folder_id"),
                                                rows.getString("album"), rows.getString("artist"), rows.getString("title"), rows.getString("path"),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    favorites.forEach { favorite ->
                        val userId = userIds[favorite.username]
                        if (userId == null) {
                            pendingAccountMatch = true
                            return@forEach
                        }
                        when (favorite.mediaType) {
                            "MUSIC", "AUDIOBOOK" -> insertTrackFavorite(connection, userId, favorite.mediaId, favorite.createdAt)
                            "ALBUM" -> {
                                val name = favorite.albumName?.trim()?.takeIf(String::isNotEmpty)
                                    ?: favorite.path.substringAfterLast('/').takeIf(String::isNotEmpty)
                                if (name != null && album(favorite.libraryId, name) != null) {
                                    insertCatalogFavorite(connection, "favorite_album", "album_name", userId, favorite.libraryId, name, favorite.createdAt)
                                }
                            }
                            "DIRECTORY" -> {
                                val candidates = listOfNotNull(favorite.artistName, favorite.title, favorite.path.substringAfterLast('/'))
                                    .map(String::trim).filter(String::isNotEmpty).distinct()
                                val name = candidates.firstOrNull { artist(favorite.libraryId, it) != null }
                                if (name != null) {
                                    insertCatalogFavorite(connection, "favorite_artist", "artist_name", userId, favorite.libraryId, name, favorite.createdAt)
                                }
                            }
                        }
                    }
                }
                if (coreTableExists("starred_album")) {
                    val favorites = coreDataSource.connection.use { coreConnection ->
                        coreConnection.prepareStatement(
                            "SELECT s.username, s.created, a.folder_id, a.name FROM starred_album s " +
                                "JOIN album a ON a.id = s.album_id WHERE a.present",
                        ).use { statement ->
                            statement.executeQuery().use { rows ->
                                buildList {
                                    while (rows.next()) {
                                        add(
                                            LegacyCatalogFavoriteRow(
                                                rows.getString("username"), rows.getTimestamp("created")?.toInstant() ?: Instant.now(),
                                                rows.getLong("folder_id"), rows.getString("name"),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    favorites.forEach { favorite ->
                        val userId = userIds[favorite.username]
                        if (userId == null) {
                            pendingAccountMatch = true
                            return@forEach
                        }
                        if (album(favorite.libraryId, favorite.name) != null) {
                            insertCatalogFavorite(connection, "favorite_album", "album_name", userId, favorite.libraryId, favorite.name, favorite.createdAt)
                        }
                    }
                }
                if (coreTableExists("starred_artist")) {
                    val favorites = coreDataSource.connection.use { coreConnection ->
                        coreConnection.prepareStatement(
                            "SELECT s.username, s.created, a.folder_id, a.name FROM starred_artist s " +
                                "JOIN artist a ON a.id = s.artist_id WHERE a.present",
                        ).use { statement ->
                            statement.executeQuery().use { rows ->
                                buildList {
                                    while (rows.next()) {
                                        add(
                                            LegacyCatalogFavoriteRow(
                                                rows.getString("username"), rows.getTimestamp("created")?.toInstant() ?: Instant.now(),
                                                rows.getLong("folder_id"), rows.getString("name"),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    favorites.forEach { favorite ->
                        val userId = userIds[favorite.username]
                        if (userId == null) {
                            pendingAccountMatch = true
                            return@forEach
                        }
                        if (artist(favorite.libraryId, favorite.name) != null) {
                            insertCatalogFavorite(connection, "favorite_artist", "artist_name", userId, favorite.libraryId, favorite.name, favorite.createdAt)
                        }
                    }
                }
                if (!pendingAccountMatch) {
                    connection.prepareStatement(
                        "INSERT INTO standalone_data_migration (migration_id, completed_at) VALUES (?, CURRENT_TIMESTAMP)",
                    ).use { statement ->
                        statement.setString(1, migrationId)
                        statement.executeUpdate()
                    }
                }
                connection.commit()
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    private fun coreTableExists(tableName: String): Boolean = coreDataSource.connection.use { tableExists(it, tableName) }

    /** Move legacy user playlists and their read grants into the account-scoped Ktor store. */
    private fun migrateLegacyPlaylists() {
        val migrationId = "legacy-user-playlists-1"
        ktorDataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val completed = connection.prepareStatement(
                    "SELECT 1 FROM standalone_data_migration WHERE migration_id = ?",
                ).use { statement ->
                    statement.setString(1, migrationId)
                    statement.executeQuery().use { it.next() }
                }
                if (completed) {
                    connection.commit()
                    return
                }
                if (!coreTableExists("playlist")) {
                    recordMigration(connection, migrationId)
                    connection.commit()
                    return
                }
                val userIds = connection.prepareStatement("SELECT id, username FROM local_user").use { statement ->
                    statement.executeQuery().use { rows ->
                        buildMap { while (rows.next()) put(rows.getString("username"), rows.getLong("id")) }
                    }
                }
                val oldPlaylists = coreDataSource.connection.use { coreConnection ->
                    coreConnection.prepareStatement(
                        "SELECT id, username, is_public, name, comment, created, changed FROM playlist ORDER BY id",
                    ).use { statement ->
                        statement.executeQuery().use { rows ->
                            buildList {
                                while (rows.next()) add(
                                    LegacyPlaylistRow(
                                        rows.getLong("id"), rows.getString("username"), rows.getBoolean("is_public"),
                                        rows.getString("name"), rows.getString("comment"), rows.getTimestamp("created")?.toInstant() ?: Instant.EPOCH,
                                        rows.getTimestamp("changed")?.toInstant() ?: Instant.EPOCH,
                                    ),
                                )
                            }
                        }
                    }
                }
                var pendingAccountMatch = false
                oldPlaylists.forEach { old ->
                    val ownerId = userIds[old.username]
                    if (ownerId == null) {
                        pendingAccountMatch = true
                        return@forEach
                    }
                    val playlistId = connection.prepareStatement(
                        "SELECT playlist_id FROM legacy_playlist_import WHERE legacy_playlist_id = ?",
                    ).use { statement ->
                        statement.setLong(1, old.id)
                        statement.executeQuery().use { if (it.next()) it.getLong(1) else null }
                    } ?: createLegacyPlaylist(connection, old, ownerId)

                    val legacyTracks = if (coreTableExists("playlist_file")) coreDataSource.connection.use { coreConnection ->
                        coreConnection.prepareStatement(
                            "SELECT pf.media_file_id FROM playlist_file pf JOIN media_file m ON m.id = pf.media_file_id " +
                                "WHERE pf.playlist_id = ? AND m.present AND m.type IN ('MUSIC', 'AUDIOBOOK') ORDER BY pf.id",
                        ).use { statement ->
                            statement.setLong(1, old.id)
                            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } }
                        }
                    } else emptyList()
                    if (legacyTracks.isNotEmpty()) {
                        val existingTrackCount = connection.prepareStatement(
                            "SELECT COUNT(*) FROM user_playlist_track WHERE playlist_id = ?",
                        ).use { statement ->
                            statement.setLong(1, playlistId)
                            statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
                        }
                        if (existingTrackCount == 0) {
                            connection.prepareStatement(
                                "INSERT INTO user_playlist_track (playlist_id, position, track_id) VALUES (?, ?, ?)",
                            ).use { statement ->
                                legacyTracks.forEachIndexed { position, trackId ->
                                    statement.setLong(1, playlistId)
                                    statement.setInt(2, position)
                                    statement.setLong(3, trackId)
                                    statement.addBatch()
                                }
                                statement.executeBatch()
                            }
                        }
                    }
                    if (coreTableExists("playlist_user")) {
                        val sharedUsernames = coreDataSource.connection.use { coreConnection ->
                            coreConnection.prepareStatement("SELECT username FROM playlist_user WHERE playlist_id = ?").use { statement ->
                                statement.setLong(1, old.id)
                                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
                            }
                        }
                        sharedUsernames.forEach { username ->
                            val sharedUserId = userIds[username]
                            if (sharedUserId == null) {
                                pendingAccountMatch = true
                            } else if (sharedUserId != ownerId) {
                                connection.prepareStatement(
                                    "INSERT INTO user_playlist_access (playlist_id, user_id) " +
                                        "SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM user_playlist_access WHERE playlist_id = ? AND user_id = ?)",
                                ).use { statement ->
                                    statement.setLong(1, playlistId)
                                    statement.setLong(2, sharedUserId)
                                    statement.setLong(3, playlistId)
                                    statement.setLong(4, sharedUserId)
                                    statement.executeUpdate()
                                }
                            }
                        }
                    }
                }
                if (!pendingAccountMatch) recordMigration(connection, migrationId)
                connection.commit()
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    private fun createLegacyPlaylist(connection: java.sql.Connection, old: LegacyPlaylistRow, ownerId: Long): Long {
        val playlistId = connection.prepareStatement(
            "INSERT INTO user_playlist (owner_id, name, created_at, updated_at, visibility, description) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf("id"),
        ).use { statement ->
            statement.setLong(1, ownerId)
            statement.setString(2, old.name.trim().ifEmpty { "Imported playlist ${old.id}" })
            statement.setTimestamp(3, Timestamp.from(old.createdAt))
            statement.setTimestamp(4, Timestamp.from(old.changedAt))
            statement.setString(5, if (old.isPublic) "PUBLIC" else "PRIVATE")
            statement.setString(6, old.description)
            statement.executeUpdate()
            statement.generatedKeys.use { keys -> check(keys.next()); keys.getLong(1) }
        }
        connection.prepareStatement(
            "INSERT INTO legacy_playlist_import (legacy_playlist_id, playlist_id) VALUES (?, ?)",
        ).use { statement ->
            statement.setLong(1, old.id)
            statement.setLong(2, playlistId)
            statement.executeUpdate()
        }
        return playlistId
    }

    private fun recordMigration(connection: java.sql.Connection, migrationId: String) {
        connection.prepareStatement(
            "INSERT INTO standalone_data_migration (migration_id, completed_at) VALUES (?, CURRENT_TIMESTAMP)",
        ).use { statement ->
            statement.setString(1, migrationId)
            statement.executeUpdate()
        }
    }

    private data class LegacyPlaylistRow(
        val id: Long,
        val username: String,
        val isPublic: Boolean,
        val name: String,
        val description: String?,
        val createdAt: Instant,
        val changedAt: Instant,
    )

    private fun insertTrackFavorite(connection: java.sql.Connection, userId: Long, trackId: Long, createdAt: Instant) {
        if (connection.prepareStatement("SELECT 1 FROM favorite_track WHERE user_id = ? AND track_id = ?").use { statement ->
                statement.setLong(1, userId)
                statement.setLong(2, trackId)
                statement.executeQuery().use { it.next() }
            }
        ) return
        connection.prepareStatement(
            "INSERT INTO favorite_track (user_id, track_id, created_at) VALUES (?, ?, ?)",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.setLong(2, trackId)
            statement.setTimestamp(3, Timestamp.from(createdAt))
            statement.executeUpdate()
        }
    }

    private fun insertCatalogFavorite(
        connection: java.sql.Connection,
        table: String,
        nameColumn: String,
        userId: Long,
        libraryId: Long,
        name: String,
        createdAt: Instant,
    ) {
        connection.prepareStatement("SELECT 1 FROM $table WHERE user_id = ? AND library_id = ? AND $nameColumn = ?")
            .use { statement ->
                statement.setLong(1, userId)
                statement.setLong(2, libraryId)
                statement.setString(3, name)
                if (statement.executeQuery().use { it.next() }) return
            }
        connection.prepareStatement(
            "INSERT INTO $table (user_id, library_id, $nameColumn, created_at) VALUES (?, ?, ?, ?)",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.setLong(2, libraryId)
            statement.setString(3, name)
            statement.setTimestamp(4, Timestamp.from(createdAt))
            statement.executeUpdate()
        }
    }

    private data class LegacyFavoriteRow(
        val username: String,
        val createdAt: Instant,
        val mediaId: Long,
        val mediaType: String,
        val libraryId: Long,
        val albumName: String?,
        val artistName: String?,
        val title: String?,
        val path: String,
    )

    private data class LegacyCatalogFavoriteRow(
        val username: String,
        val createdAt: Instant,
        val libraryId: Long,
        val name: String,
    )

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

    fun album(libraryId: Long, name: String, accessibleLibraryIds: Set<Long>? = null): StandaloneAlbum? {
        val tracks = tracksForAlbum(libraryId, name, accessibleLibraryIds)
        if (tracks.isEmpty()) return null
        return StandaloneAlbum(
            libraryId,
            name,
            tracks.first().albumArtist ?: tracks.first().artist,
            tracks.mapNotNull { it.year }.maxOrNull(),
            tracks.firstNotNullOfOrNull { it.genre },
            tracks.size.toLong(),
            tracks.sumOf { it.durationSeconds.toLong() },
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
        val sql = "SELECT folder_id, album, MAX(COALESCE(NULLIF(TRIM(album_artist), ''), TRIM(artist))) AS album_artist, " +
            "MAX(year) AS year, MAX(genre) AS genre, COUNT(*) AS track_count, " +
            "CAST(SUM(COALESCE(duration, 0)) AS BIGINT) AS duration_seconds FROM media_file " +
            "WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') AND (? IS NULL OR folder_id = ?) " +
            "AND album IS NOT NULL AND TRIM(album) <> ''" + libraryScope(accessibleLibraryIds) + " " +
            "GROUP BY folder_id, album HAVING (? IS NULL OR MAX(genre) = ?) " +
            "AND (? IS NULL OR MAX(year) >= ?) AND (? IS NULL OR MAX(year) <= ?) " +
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
        val sql = "SELECT COUNT(*) FROM (SELECT folder_id, album FROM media_file " +
            "WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') AND (? IS NULL OR folder_id = ?) " +
            "AND album IS NOT NULL AND TRIM(album) <> ''" + libraryScope(accessibleLibraryIds) + " " +
            "GROUP BY folder_id, album HAVING (? IS NULL OR MAX(genre) = ?) " +
            "AND (? IS NULL OR MAX(year) >= ?) AND (? IS NULL OR MAX(year) <= ?)) catalog_albums"
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
        "year" -> "MAX(year), LOWER(album), album, folder_id"
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
        val prefixPattern = prefix?.trim()?.takeIf(String::isNotEmpty)?.let(::prefixLikePattern)
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
        val prefixPattern = prefix?.trim()?.takeIf(String::isNotEmpty)?.let(::prefixLikePattern)
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

    private fun prefixLikePattern(prefix: String): String =
        prefix.lowercase().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"

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
        val ALBUM_SORTS = setOf(
            "name", "artist", "year", "newest", "most-played", "recently-played", "random", "highest-rated",
        )
        const val ALBUM_PAGE_SIZE = 100
        const val TRACK_REFERENCE_MIGRATION = "core-media-track-references-v1"
        const val TRACK_COLUMNS = "id, folder_id, path, title, artist, album, album_artist, disc_number, " +
            "track_number, year, genre, duration, file_size, changed"
    }
}

data class CatalogGenre(val name: String, val trackCount: Int, val albumCount: Int)
