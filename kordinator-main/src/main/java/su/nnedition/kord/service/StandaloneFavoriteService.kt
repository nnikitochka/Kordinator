package su.nnedition.kord.service

import su.nnedition.kord.service.library.scan.StandaloneAlbum
import su.nnedition.kord.service.library.scan.StandaloneArtist
import su.nnedition.kord.service.library.scan.StandaloneTrack
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/** Stores each user's favorite tracks for the standalone API. */
class StandaloneFavoriteService(
    private val dataSource: DataSource,
    private val mediaCatalog: KtorCoreMediaCatalogService,
) {
    fun list(userId: Long, limit: Int, offset: Int, accessibleLibraryIds: Set<Long>? = null): List<StandaloneTrack> {
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)
        val coreTrackIds = dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT track_id FROM favorite_track WHERE user_id = ? ORDER BY created_at DESC, track_id",
            ).use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } }
            }
        }
        return mediaCatalog.tracksByCoreIds(coreTrackIds, accessibleLibraryIds).drop(safeOffset).take(safeLimit)
    }

    fun count(userId: Long, accessibleLibraryIds: Set<Long>? = null): Long {
        val trackIds = dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT track_id FROM favorite_track WHERE user_id = ?").use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } }
            }
        }
        return mediaCatalog.tracksByCoreIds(trackIds, accessibleLibraryIds).size.toLong()
    }

    fun set(userId: Long, trackId: Long, favorite: Boolean, accessibleLibraryIds: Set<Long>? = null): Boolean {
        if (mediaCatalog.track(trackId, accessibleLibraryIds) == null) return false
        return dataSource.connection.use { connection ->
            if (favorite) {
                val alreadyFavorite = connection.prepareStatement(
                    "SELECT 1 FROM favorite_track WHERE user_id = ? AND track_id = ?",
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.setLong(2, trackId)
                    statement.executeQuery().use { it.next() }
                }
                if (!alreadyFavorite) {
                    try {
                        connection.prepareStatement(
                            "INSERT INTO favorite_track (user_id, track_id, created_at) VALUES (?, ?, ?)",
                        ).use { statement ->
                            statement.setLong(1, userId)
                            statement.setLong(2, trackId)
                            statement.setTimestamp(3, Timestamp.from(Instant.now()))
                            statement.executeUpdate()
                        }
                    } catch (failure: java.sql.SQLException) {
                        if (failure.sqlState?.startsWith("23") != true) throw failure
                    }
                }
            } else {
                connection.prepareStatement("DELETE FROM favorite_track WHERE user_id = ? AND track_id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.setLong(2, trackId)
                    statement.executeUpdate()
                }
            }
            true
        }
    }

    fun albums(userId: Long, accessibleLibraryIds: Set<Long>? = null): List<StandaloneAlbum> =
        catalogFavorites(userId, "favorite_album", "album_name", accessibleLibraryIds)
            .mapNotNull { (libraryId, name) -> mediaCatalog.album(libraryId, name, accessibleLibraryIds) }

    fun artists(userId: Long, accessibleLibraryIds: Set<Long>? = null): List<StandaloneArtist> =
        catalogFavorites(userId, "favorite_artist", "artist_name", accessibleLibraryIds)
            .mapNotNull { (libraryId, name) -> mediaCatalog.artist(libraryId, name, accessibleLibraryIds) }

    fun setAlbum(userId: Long, libraryId: Long, name: String, favorite: Boolean, accessibleLibraryIds: Set<Long>? = null): Boolean =
        setCatalogFavorite(userId, libraryId, name, favorite, accessibleLibraryIds, "favorite_album", "album_name") {
            mediaCatalog.album(libraryId, name, accessibleLibraryIds) != null
        }

    fun setArtist(userId: Long, libraryId: Long, name: String, favorite: Boolean, accessibleLibraryIds: Set<Long>? = null): Boolean =
        setCatalogFavorite(userId, libraryId, name, favorite, accessibleLibraryIds, "favorite_artist", "artist_name") {
            mediaCatalog.artist(libraryId, name, accessibleLibraryIds) != null
        }

    private fun catalogFavorites(
        userId: Long,
        table: String,
        nameColumn: String,
        accessibleLibraryIds: Set<Long>?,
    ): List<Pair<Long, String>> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT library_id, $nameColumn FROM $table WHERE user_id = ? ORDER BY created_at DESC, library_id, $nameColumn",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val libraryId = rows.getLong("library_id")
                        if (accessibleLibraryIds == null || libraryId in accessibleLibraryIds) {
                            add(libraryId to rows.getString(nameColumn))
                        }
                    }
                }
            }
        }
    }

    private fun setCatalogFavorite(
        userId: Long,
        libraryId: Long,
        name: String,
        favorite: Boolean,
        accessibleLibraryIds: Set<Long>?,
        table: String,
        nameColumn: String,
        exists: () -> Boolean,
    ): Boolean {
        require(libraryId >= 0) { "Library ID must be non-negative" }
        require(name.isNotBlank() && name.length <= MAX_CATALOG_NAME_LENGTH) { "Catalog name must contain 1 to $MAX_CATALOG_NAME_LENGTH characters" }
        if (!favorite) {
            dataSource.connection.use { connection ->
                connection.prepareStatement("DELETE FROM $table WHERE user_id = ? AND library_id = ? AND $nameColumn = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.setLong(2, libraryId)
                    statement.setString(3, name)
                    statement.executeUpdate()
                }
            }
            return true
        }
        if (accessibleLibraryIds?.contains(libraryId) == false || !exists()) return false
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    "INSERT INTO $table (user_id, library_id, $nameColumn, created_at) VALUES (?, ?, ?, ?)",
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.setLong(2, libraryId)
                    statement.setString(3, name)
                    statement.setTimestamp(4, Timestamp.from(Instant.now()))
                    statement.executeUpdate()
                }
            }
        } catch (failure: java.sql.SQLException) {
            if (failure.sqlState?.startsWith("23") != true) throw failure
        }
        return true
    }

    private companion object {
        const val MAX_CATALOG_NAME_LENGTH = 512
    }
}
