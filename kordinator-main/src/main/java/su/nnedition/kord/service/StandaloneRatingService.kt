package su.nnedition.kord.service

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/** Stores a user's 1–5 rating for a catalog track; rating zero removes it. */
class StandaloneRatingService(
    private val dataSource: DataSource,
    private val mediaCatalog: KtorCoreMediaCatalogService,
) {
    fun get(userId: Long, trackId: Long): Int? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT rating FROM track_rating WHERE user_id = ? AND track_id = ?").use { statement ->
            statement.setLong(1, userId)
            statement.setLong(2, trackId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getInt("rating") else null }
        }
    }

    fun set(userId: Long, trackId: Long, rating: Int, accessibleLibraryIds: Set<Long>? = null): Boolean {
        require(rating in 0..5) { "Rating must be between 0 and 5" }
        if (mediaCatalog.track(trackId, accessibleLibraryIds) == null) return false
        return dataSource.connection.use { connection ->
            if (rating == 0) {
                connection.prepareStatement("DELETE FROM track_rating WHERE user_id = ? AND track_id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.setLong(2, trackId)
                    statement.executeUpdate()
                }
            } else {
                val updated = connection.prepareStatement(
                    "UPDATE track_rating SET rating = ?, updated_at = ? WHERE user_id = ? AND track_id = ?",
                ).use { statement ->
                    statement.setInt(1, rating)
                    statement.setTimestamp(2, Timestamp.from(Instant.now()))
                    statement.setLong(3, userId)
                    statement.setLong(4, trackId)
                    statement.executeUpdate()
                }
                if (updated == 0) {
                    connection.prepareStatement(
                        "INSERT INTO track_rating (user_id, track_id, rating, updated_at) VALUES (?, ?, ?, ?)",
                    ).use { statement ->
                        statement.setLong(1, userId)
                        statement.setLong(2, trackId)
                        statement.setInt(3, rating)
                        statement.setTimestamp(4, Timestamp.from(Instant.now()))
                        statement.executeUpdate()
                    }
                }
            }
            true
        }
    }

    fun getAlbum(userId: Long, libraryId: Long, albumName: String): Int? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT rating FROM album_rating WHERE user_id = ? AND library_id = ? AND album_name = ?",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.setLong(2, libraryId)
            statement.setString(3, albumName)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getInt("rating") else null }
        }
    }

    fun setAlbum(
        userId: Long,
        libraryId: Long,
        albumName: String,
        rating: Int,
        accessibleLibraryIds: Set<Long>? = null,
    ): Boolean {
        require(rating in 0..5) { "Rating must be between 0 and 5" }
        require(libraryId >= 0) { "Library ID must be non-negative" }
        require(albumName.isNotBlank() && albumName.length <= 512) { "Album name must contain 1 to 512 characters" }
        if (accessibleLibraryIds?.contains(libraryId) == false || mediaCatalog.album(libraryId, albumName, accessibleLibraryIds) == null) {
            return false
        }
        return dataSource.connection.use { connection ->
            if (rating == 0) {
                connection.prepareStatement(
                    "DELETE FROM album_rating WHERE user_id = ? AND library_id = ? AND album_name = ?",
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.setLong(2, libraryId)
                    statement.setString(3, albumName)
                    statement.executeUpdate()
                }
            } else {
                val updatedAt = Timestamp.from(Instant.now())
                val updated = connection.prepareStatement(
                    "UPDATE album_rating SET rating = ?, updated_at = ? " +
                        "WHERE user_id = ? AND library_id = ? AND album_name = ?",
                ).use { statement ->
                    statement.setInt(1, rating)
                    statement.setTimestamp(2, updatedAt)
                    statement.setLong(3, userId)
                    statement.setLong(4, libraryId)
                    statement.setString(5, albumName)
                    statement.executeUpdate()
                }
                if (updated == 0) {
                    connection.prepareStatement(
                        "INSERT INTO album_rating (user_id, library_id, album_name, rating, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    ).use { statement ->
                        statement.setLong(1, userId)
                        statement.setLong(2, libraryId)
                        statement.setString(3, albumName)
                        statement.setInt(4, rating)
                        statement.setTimestamp(5, updatedAt)
                        statement.executeUpdate()
                    }
                }
            }
            true
        }
    }
}
