package org.airsonic.player.service

import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import javax.sql.DataSource

data class StandaloneShare(
    val id: Long,
    val ownerId: Long,
    val description: String?,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val visitCount: Long,
    val lastVisitedAt: Instant?,
    val trackIds: List<Long>,
    val token: String? = null,
)

/** Stores revocable, expiring public links to tracks in the Kordinator catalog. */
class StandaloneShareService(
    private val dataSource: DataSource,
    private val catalog: KtorCoreMediaCatalogService,
) {
    fun create(
        ownerId: Long,
        trackIds: List<Long>,
        description: String?,
        expiresAt: Instant?,
        accessibleLibraryIds: Set<Long>?,
    ): StandaloneShare {
        require(trackIds.isNotEmpty() && trackIds.size <= MAX_TRACKS) { "A share must contain between 1 and $MAX_TRACKS tracks" }
        require(trackIds.all { it >= 0 }) { "Track IDs must be non-negative" }
        require(description == null || description.length <= MAX_DESCRIPTION_LENGTH) { "Description is too long" }
        require(expiresAt == null || expiresAt.isAfter(Instant.now())) { "Expiration must be in the future" }
        require(catalog.tracksByCoreIds(trackIds, accessibleLibraryIds).size == trackIds.size) {
            "Track does not exist or is not accessible"
        }
        val token = newToken()
        val now = Instant.now()
        return transaction { connection ->
            val id = connection.prepareStatement(
                "INSERT INTO shared_link (owner_id, token_hash, description, created_at, expires_at, visit_count) " +
                    "VALUES (?, ?, ?, ?, ?, 0)",
                arrayOf("id"),
            ).use { statement ->
                statement.setLong(1, ownerId)
                statement.setString(2, hashToken(token))
                statement.setString(3, description?.trim()?.takeIf(String::isNotEmpty))
                statement.setTimestamp(4, Timestamp.from(now))
                statement.setTimestamp(5, expiresAt?.let(Timestamp::from))
                statement.executeUpdate()
                statement.generatedKeys.use { keys -> check(keys.next()); keys.getLong(1) }
            }
            connection.prepareStatement(
                "INSERT INTO shared_link_track (share_id, position, track_id) VALUES (?, ?, ?)",
            ).use { statement ->
                trackIds.forEachIndexed { position, trackId ->
                    statement.setLong(1, id)
                    statement.setInt(2, position)
                    statement.setLong(3, trackId)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
            get(connection, id, ownerId)!!.copy(token = token)
        }
    }

    fun list(ownerId: Long): List<StandaloneShare> = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT id FROM shared_link WHERE owner_id = ? ORDER BY created_at DESC, id DESC").use { statement ->
            statement.setLong(1, ownerId)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) get(connection, rows.getLong(1), ownerId)?.let(::add) }
            }
        }
    }

    fun get(ownerId: Long, shareId: Long): StandaloneShare? = dataSource.connection.use { get(it, shareId, ownerId) }

    fun update(ownerId: Long, shareId: Long, description: String?, expiresAt: Instant?): Boolean {
        require(description == null || description.length <= MAX_DESCRIPTION_LENGTH) { "Description is too long" }
        require(expiresAt == null || expiresAt.isAfter(Instant.now())) { "Expiration must be in the future" }
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE shared_link SET description = ?, expires_at = ? WHERE id = ? AND owner_id = ?",
            ).use { statement ->
                statement.setString(1, description?.trim()?.takeIf(String::isNotEmpty))
                statement.setTimestamp(2, expiresAt?.let(Timestamp::from))
                statement.setLong(3, shareId)
                statement.setLong(4, ownerId)
                statement.executeUpdate() == 1
            }
        }
    }

    fun delete(ownerId: Long, shareId: Long): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM shared_link WHERE id = ? AND owner_id = ?").use { statement ->
            statement.setLong(1, shareId)
            statement.setLong(2, ownerId)
            statement.executeUpdate() == 1
        }
    }

    fun publicShare(token: String): StandaloneShare? = dataSource.connection.use { connection ->
        val now = Timestamp.from(Instant.now())
        val id = connection.prepareStatement(
            "UPDATE shared_link SET visit_count = visit_count + 1, last_visited_at = ? " +
                "WHERE token_hash = ? AND (expires_at IS NULL OR expires_at > ?)",
        ).use { statement ->
            statement.setTimestamp(1, now)
            statement.setString(2, hashToken(token))
            statement.setTimestamp(3, now)
            if (statement.executeUpdate() == 0) null else {
                connection.prepareStatement("SELECT id FROM shared_link WHERE token_hash = ?").use { lookup ->
                    lookup.setString(1, hashToken(token))
                    lookup.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
                }
            }
        } ?: return@use null
        get(connection, id, null)
    }

    private fun get(connection: Connection, shareId: Long, ownerId: Long?): StandaloneShare? {
        val owner = connection.prepareStatement(
            "SELECT owner_id, description, created_at, expires_at, visit_count, last_visited_at " +
                "FROM shared_link WHERE id = ? AND (? IS NULL OR owner_id = ?)",
        ).use { statement ->
            statement.setLong(1, shareId)
            if (ownerId == null) {
                statement.setNull(2, java.sql.Types.BIGINT)
                statement.setNull(3, java.sql.Types.BIGINT)
            } else {
                statement.setLong(2, ownerId)
                statement.setLong(3, ownerId)
            }
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else listOf(
                    rows.getLong("owner_id"),
                    rows.getString("description"),
                    rows.getTimestamp("created_at"),
                    rows.getTimestamp("expires_at"),
                    rows.getLong("visit_count"),
                    rows.getTimestamp("last_visited_at"),
                )
            }
        } ?: return null
        val tracks = connection.prepareStatement(
            "SELECT track_id FROM shared_link_track WHERE share_id = ? ORDER BY position",
        ).use { statement ->
            statement.setLong(1, shareId)
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } }
        }
        return StandaloneShare(
            shareId,
            owner[0] as Long,
            owner[1] as String?,
            (owner[2] as Timestamp).toInstant(),
            (owner[3] as Timestamp?)?.toInstant(),
            owner[4] as Long,
            (owner[5] as Timestamp?)?.toInstant(),
            tracks,
        )
    }

    private fun <T> transaction(action: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            action(connection).also { connection.commit() }
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        }
    }

    private fun newToken(): String = ByteArray(32).also(SECURE_RANDOM::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun hashToken(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_TRACKS = 500
        const val MAX_DESCRIPTION_LENGTH = 1024
        val SECURE_RANDOM = SecureRandom()
    }
}
