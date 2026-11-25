package su.nnedition.kord.ktor

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

data class KordinatorSessionRecord(
    val username: String,
    val credentialVersion: Instant?,
)

interface KordinatorSessionStore {
    fun create(username: String, tokenHash: String, credentialVersion: Instant?, createdAt: Instant, expiresAt: Instant)
    fun findActive(tokenHash: String, now: Instant): KordinatorSessionRecord?
    fun revoke(tokenHash: String)
}

class JdbcKordinatorSessionStore(private val dataSource: DataSource) : KordinatorSessionStore {
    override fun create(
        username: String,
        tokenHash: String,
        credentialVersion: Instant?,
        createdAt: Instant,
        expiresAt: Instant,
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO kordinator_api_session (token_hash, username, credential_version, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, tokenHash)
                statement.setString(2, username)
                if (credentialVersion == null) statement.setTimestamp(3, null) else statement.setTimestamp(3, Timestamp.from(credentialVersion))
                statement.setTimestamp(4, Timestamp.from(createdAt))
                statement.setTimestamp(5, Timestamp.from(expiresAt))
                statement.executeUpdate()
            }
        }
    }

    override fun findActive(tokenHash: String, now: Instant): KordinatorSessionRecord? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT username, credential_version FROM kordinator_api_session WHERE token_hash = ? AND expires_at > ?",
            ).use { statement ->
                statement.setString(1, tokenHash)
                statement.setTimestamp(2, Timestamp.from(now))
                statement.executeQuery().use { rows ->
                    if (!rows.next()) {
                        null
                    } else {
                        KordinatorSessionRecord(
                            rows.getString("username"),
                            rows.getTimestamp("credential_version")?.toInstant(),
                        )
                    }
                }
            }
        }

    override fun revoke(tokenHash: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM kordinator_api_session WHERE token_hash = ?").use { statement ->
                statement.setString(1, tokenHash)
                statement.executeUpdate()
            }
        }
    }
}
