package su.nnedition.kord.security

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.sql.DataSource

data class LocalUserPrincipal(
    val id: Long,
    val username: String,
    val role: String,
    val allowedLibraryIds: Set<Long> = emptySet(),
)
data class LocalUserAccount(val id: Long, val username: String, val role: String, val enabled: Boolean, val email: String? = null)
data class LocalSessionToken(val token: String, val expiresAt: Instant)
data class PasswordRecoveryDelivery(val username: String, val email: String, val token: String)
class LastEnabledAdministratorException : IllegalStateException("At least one enabled administrator must remain")

/** Owns local password authentication and opaque, revocable bearer sessions. */
class LocalAuthService(
    private val dataSource: DataSource,
    private val clock: Clock = Clock.systemUTC(),
    private val sessionDuration: Duration = Duration.ofDays(30),
) {
    private val dummyPasswordHash: String by lazy { hashPassword("constant-time-login-dummy-password") }

    fun createUser(username: String, password: String, role: String = "USER", email: String? = null): Long {
        require(username.matches(USERNAME_PATTERN)) { "Username must be 3–64 letters, digits, dots, underscores or hyphens" }
        require(password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Password must contain between $MIN_PASSWORD_LENGTH and $MAX_PASSWORD_LENGTH characters"
        }
        require(role in ALLOWED_ROLES) { "Unsupported account role" }
        val normalizedEmail = normalizeEmail(email)

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO local_user (username, password_hash, role, enabled, created_at, email) VALUES (?, ?, ?, TRUE, ?, ?)",
                arrayOf("id"),
            ).use { statement ->
                statement.setString(1, username)
                statement.setString(2, hashPassword(password))
                statement.setString(3, role)
                statement.setTimestamp(4, Timestamp.from(clock.instant()))
                statement.setString(5, normalizedEmail)
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    check(keys.next()) { "Database did not return a user ID" }
                    return keys.getLong(1)
                }
            }
        }
    }

    fun createInitialAdmin(username: String, password: String): Long {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM local_user").use { statement ->
                statement.executeQuery().use { rows ->
                    check(rows.next() && rows.getLong(1) == 0L) { "Initial administrator can only be created before any user account" }
                }
            }
        }
        return createUser(username, password, "ADMIN")
    }

    fun changePassword(userId: Long, currentPassword: String, newPassword: String): Boolean {
        require(newPassword.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Password must contain between $MIN_PASSWORD_LENGTH and $MAX_PASSWORD_LENGTH characters"
        }
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val storedHash = connection.prepareStatement(
                    "SELECT password_hash FROM local_user WHERE id = ? AND enabled = TRUE",
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
                }
                if (storedHash == null || !verifyPassword(currentPassword, storedHash)) {
                    connection.rollback()
                    return false
                }

                connection.prepareStatement("UPDATE local_user SET password_hash = ? WHERE id = ?").use { statement ->
                    statement.setString(1, hashPassword(newPassword))
                    statement.setLong(2, userId)
                    check(statement.executeUpdate() == 1) { "Account disappeared during password update" }
                }
                connection.prepareStatement("DELETE FROM user_session WHERE user_id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.executeUpdate()
                }
                connection.prepareStatement("DELETE FROM password_recovery_token WHERE user_id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.executeUpdate()
                }
                connection.commit()
                return true
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    /** Resets a user's password administratively and revokes every existing session. */
    fun resetPassword(userId: Long, newPassword: String): Boolean {
        require(newPassword.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Password must contain between $MIN_PASSWORD_LENGTH and $MAX_PASSWORD_LENGTH characters"
        }
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val updated = connection.prepareStatement(
                    "UPDATE local_user SET password_hash = ? WHERE id = ?",
                ).use { statement ->
                    statement.setString(1, hashPassword(newPassword))
                    statement.setLong(2, userId)
                    statement.executeUpdate()
                }
                if (updated == 0) {
                    connection.rollback()
                    return false
                }
                connection.prepareStatement("DELETE FROM user_session WHERE user_id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.executeUpdate()
                }
                connection.prepareStatement("DELETE FROM password_recovery_token WHERE user_id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.executeUpdate()
                }
                connection.commit()
                return true
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    fun setUserEmail(userId: Long, email: String?): Boolean {
        val normalized = normalizeEmail(email)
        return dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val updated = connection.prepareStatement("UPDATE local_user SET email = ? WHERE id = ?").use { statement ->
                    statement.setString(1, normalized)
                    statement.setLong(2, userId)
                    statement.executeUpdate() == 1
                }
                if (updated) {
                    connection.prepareStatement("DELETE FROM password_recovery_token WHERE user_id = ?").use { statement ->
                        statement.setLong(1, userId)
                        statement.executeUpdate()
                    }
                }
                connection.commit()
                updated
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Issues a short-lived opaque recovery token. The caller must deliver it out of band. */
    fun createPasswordRecovery(email: String, lifetime: Duration = Duration.ofMinutes(30)): PasswordRecoveryDelivery? {
        val normalized = normalizeEmail(email) ?: return null
        require(!lifetime.isNegative && !lifetime.isZero) { "Recovery token lifetime must be positive" }
        val tokenBytes = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
        val now = clock.instant()
        return dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val account = connection.prepareStatement(
                    "SELECT id, username FROM local_user WHERE LOWER(email) = LOWER(?) AND enabled = TRUE",
                ).use { statement ->
                    statement.setString(1, normalized)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) rows.getLong("id") to rows.getString("username") else null
                    }
                }
                if (account == null) {
                    connection.rollback()
                    return null
                }
                connection.prepareStatement("DELETE FROM password_recovery_token WHERE user_id = ?").use { statement ->
                    statement.setLong(1, account.first)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO password_recovery_token (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, sha256(token))
                    statement.setLong(2, account.first)
                    statement.setTimestamp(3, Timestamp.from(now))
                    statement.setTimestamp(4, Timestamp.from(now.plus(lifetime)))
                    statement.executeUpdate()
                }
                connection.commit()
                PasswordRecoveryDelivery(account.second, normalized, token)
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Consumes a recovery token and changes the password and sessions in one transaction. */
    fun resetPasswordWithRecoveryToken(token: String, newPassword: String): Boolean {
        require(newPassword.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Password must contain between $MIN_PASSWORD_LENGTH and $MAX_PASSWORD_LENGTH characters"
        }
        if (token.isBlank() || token.length > MAX_TOKEN_LENGTH) return false
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val userId = connection.prepareStatement(
                    "SELECT user_id FROM password_recovery_token WHERE token_hash = ? AND expires_at > ? FOR UPDATE",
                ).use { statement ->
                    statement.setString(1, sha256(token))
                    statement.setTimestamp(2, Timestamp.from(clock.instant()))
                    statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
                }
                if (userId == null) {
                    connection.rollback()
                    return false
                }
                connection.prepareStatement("UPDATE local_user SET password_hash = ? WHERE id = ? AND enabled = TRUE").use { statement ->
                    statement.setString(1, hashPassword(newPassword))
                    statement.setLong(2, userId)
                    if (statement.executeUpdate() != 1) {
                        connection.rollback()
                        return false
                    }
                }
                connection.prepareStatement("DELETE FROM password_recovery_token WHERE user_id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.executeUpdate()
                }
                connection.prepareStatement("DELETE FROM user_session WHERE user_id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.executeUpdate()
                }
                connection.commit()
                return true
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun setUserEnabled(userId: Long, enabled: Boolean): Boolean {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val updated = connection.prepareStatement("UPDATE local_user SET enabled = ? WHERE id = ?").use { statement ->
                    statement.setBoolean(1, enabled)
                    statement.setLong(2, userId)
                    statement.executeUpdate()
                }
                if (updated == 0) {
                    connection.rollback()
                    return false
                }
                if (!enabled) {
                    connection.prepareStatement("DELETE FROM user_session WHERE user_id = ?").use { statement ->
                        statement.setLong(1, userId)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM password_recovery_token WHERE user_id = ?").use { statement ->
                        statement.setLong(1, userId)
                        statement.executeUpdate()
                    }
                }
                connection.commit()
                return true
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    fun users(): List<LocalUserAccount> = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT id, username, role, enabled, email FROM local_user ORDER BY username").use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            LocalUserAccount(
                                rows.getLong("id"),
                                rows.getString("username"),
                                rows.getString("role"),
                                rows.getBoolean("enabled"),
                                rows.getString("email"),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Renames an account without revoking its ID-bound sessions. */
    fun renameUser(userId: Long, username: String): Boolean {
        require(username.matches(USERNAME_PATTERN)) { "Username must be 3–64 letters, digits, dots, underscores or hyphens" }
        dataSource.connection.use { connection ->
            val currentUsername = connection.prepareStatement("SELECT username FROM local_user WHERE id = ?").use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            } ?: return false
            if (currentUsername == username) return true
            return connection.prepareStatement("UPDATE local_user SET username = ? WHERE id = ?").use { statement ->
                statement.setString(1, username)
                statement.setLong(2, userId)
                statement.executeUpdate() == 1
            }
        }
    }

    /** Deletes an account and all account-owned rows covered by database cascades. */
    fun deleteUser(userId: Long): Boolean {
        dataSource.connection.use { connection ->
            val previousAutoCommit = connection.autoCommit
            val previousIsolation = connection.transactionIsolation
            connection.transactionIsolation = java.sql.Connection.TRANSACTION_SERIALIZABLE
            connection.autoCommit = false
            try {
                val user = connection.prepareStatement("SELECT role, enabled FROM local_user WHERE id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) rows.getString("role") to rows.getBoolean("enabled") else null
                    }
                }
                if (user == null) {
                    connection.rollback()
                    return false
                }
                if (user.first == "ADMIN" && user.second) {
                    val otherEnabledAdmins = connection.prepareStatement(
                        "SELECT COUNT(*) FROM local_user WHERE role = 'ADMIN' AND enabled = TRUE AND id <> ?",
                    ).use { statement ->
                        statement.setLong(1, userId)
                        statement.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
                    }
                    if (otherEnabledAdmins == 0L) throw LastEnabledAdministratorException()
                }
                connection.prepareStatement("DELETE FROM local_user WHERE id = ?").use { statement ->
                    statement.setLong(1, userId)
                    check(statement.executeUpdate() == 1) { "Account disappeared during deletion" }
                }
                connection.commit()
                return true
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = previousAutoCommit
                connection.transactionIsolation = previousIsolation
            }
        }
    }

    /** Updates a role and revokes sessions so the new authorization applies immediately. */
    fun setUserRole(userId: Long, role: String): Boolean {
        require(role in ALLOWED_ROLES) { "Unsupported account role" }
        dataSource.connection.use { connection ->
            val previousAutoCommit = connection.autoCommit
            val previousIsolation = connection.transactionIsolation
            connection.transactionIsolation = java.sql.Connection.TRANSACTION_SERIALIZABLE
            connection.autoCommit = false
            try {
                val user = connection.prepareStatement(
                    "SELECT role, enabled FROM local_user WHERE id = ?",
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) rows.getString("role") to rows.getBoolean("enabled") else null
                    }
                }
                if (user == null) {
                    connection.rollback()
                    return false
                }
                if (user.first == "ADMIN" && user.second && role != "ADMIN") {
                    val otherEnabledAdmins = connection.prepareStatement(
                        "SELECT COUNT(*) FROM local_user WHERE role = 'ADMIN' AND enabled = TRUE AND id <> ?",
                    ).use { statement ->
                        statement.setLong(1, userId)
                        statement.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
                    }
                    if (otherEnabledAdmins == 0L) throw LastEnabledAdministratorException()
                }
                if (user.first != role) {
                    connection.prepareStatement("UPDATE local_user SET role = ? WHERE id = ?").use { statement ->
                        statement.setString(1, role)
                        statement.setLong(2, userId)
                        check(statement.executeUpdate() == 1) { "Account disappeared during role update" }
                    }
                    connection.prepareStatement("DELETE FROM user_session WHERE user_id = ?").use { statement ->
                        statement.setLong(1, userId)
                        statement.executeUpdate()
                    }
                }
                connection.commit()
                return true
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = previousAutoCommit
                connection.transactionIsolation = previousIsolation
            }
        }
    }

    fun login(username: String, password: String): LocalSessionToken? {
        val user = dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id, username, password_hash, role, enabled FROM local_user WHERE username = ?",
            ).use { statement ->
                statement.setString(1, username)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) {
                        StoredUser(0L, "", dummyPasswordHash, "USER", false)
                    } else {
                        StoredUser(
                            rows.getLong("id"),
                            rows.getString("username"),
                            rows.getString("password_hash"),
                            rows.getString("role"),
                            rows.getBoolean("enabled"),
                        )
                    }
                }
            }
        }
        val passwordMatches = verifyPassword(password, user.passwordHash)
        if (!user.enabled || !passwordMatches) return null

        val tokenBytes = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
        val expiresAt = clock.instant().plus(sessionDuration)
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO user_session (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, sha256(token))
                statement.setLong(2, user.id)
                statement.setTimestamp(3, Timestamp.from(clock.instant()))
                statement.setTimestamp(4, Timestamp.from(expiresAt))
                statement.executeUpdate()
            }
        }
        return LocalSessionToken(token, expiresAt)
    }

    fun authenticate(token: String): LocalUserPrincipal? {
        if (token.isBlank() || token.length > MAX_TOKEN_LENGTH) return null
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT u.id, u.username, u.role FROM user_session s " +
                    "JOIN local_user u ON u.id = s.user_id " +
                    "WHERE s.token_hash = ? AND s.expires_at > ? AND u.enabled = TRUE",
            ).use { statement ->
                statement.setString(1, sha256(token))
                statement.setTimestamp(2, Timestamp.from(clock.instant()))
                statement.executeQuery().use { rows ->
                    if (rows.next()) Triple(rows.getLong("id"), rows.getString("username"), rows.getString("role")) else null
                }?.let { (userId, username, role) ->
                    val allowedLibraries = connection.prepareStatement(
                        "SELECT library_id FROM local_user_library WHERE user_id = ?",
                    ).use { accessStatement ->
                        accessStatement.setLong(1, userId)
                        accessStatement.executeQuery().use { accessRows ->
                            buildSet { while (accessRows.next()) add(accessRows.getLong("library_id")) }
                        }
                    }
                    LocalUserPrincipal(userId, username, role, allowedLibraries)
                }
            }
        }
    }

    fun setAllowedLibraries(userId: Long, libraryIds: Set<Long>): Boolean {
        require(libraryIds.all { it >= 0 }) { "Library IDs must be non-negative" }
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val exists = connection.prepareStatement("SELECT 1 FROM local_user WHERE id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.executeQuery().use { it.next() }
                }
                if (!exists) {
                    connection.rollback()
                    return false
                }
                connection.prepareStatement("DELETE FROM local_user_library WHERE user_id = ?").use { statement ->
                    statement.setLong(1, userId)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO local_user_library (user_id, library_id) VALUES (?, ?)",
                ).use { statement ->
                    libraryIds.forEach { libraryId ->
                        statement.setLong(1, userId)
                        statement.setLong(2, libraryId)
                        statement.addBatch()
                    }
                    if (libraryIds.isNotEmpty()) statement.executeBatch()
                }
                connection.commit()
                return true
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun allowedLibraries(userId: Long): Set<Long>? = dataSource.connection.use { connection ->
        val exists = connection.prepareStatement("SELECT 1 FROM local_user WHERE id = ?").use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { it.next() }
        }
        if (!exists) return@use null
        connection.prepareStatement("SELECT library_id FROM local_user_library WHERE user_id = ?").use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getLong("library_id")) } }
        }
    }

    fun removeLibraryAccess(libraryId: Long): Int {
        require(libraryId >= 0) { "Library ID must be non-negative" }
        return dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM local_user_library WHERE library_id = ?").use { statement ->
                statement.setLong(1, libraryId)
                statement.executeUpdate()
            }
        }
    }

    fun logout(token: String) {
        if (token.isBlank() || token.length > MAX_TOKEN_LENGTH) return
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM user_session WHERE token_hash = ?").use { statement ->
                statement.setString(1, sha256(token))
                statement.executeUpdate()
            }
        }
    }

    private fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val hash = argon2(password, salt)
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return "\$argon2id\$v=$ARGON_VERSION\$m=$MEMORY_KIB,t=$ITERATIONS,p=$PARALLELISM\$" +
            encoder.encodeToString(salt) + "\$" + encoder.encodeToString(hash)
    }

    private fun verifyPassword(password: String, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 6 || parts[0].isNotEmpty() || parts[1] != "argon2id" ||
            parts[2] != "v=$ARGON_VERSION" || parts[3] != "m=$MEMORY_KIB,t=$ITERATIONS,p=$PARALLELISM"
        ) return false
        return try {
            val decoder = Base64.getUrlDecoder()
            MessageDigest.isEqual(argon2(password, decoder.decode(parts[4])), decoder.decode(parts[5]))
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun argon2(password: String, salt: ByteArray): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(ARGON_VERSION)
            .withMemoryAsKB(MEMORY_KIB)
            .withIterations(ITERATIONS)
            .withParallelism(PARALLELISM)
            .withSalt(salt)
            .build()
        return ByteArray(HASH_BYTES).also { output ->
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password.toCharArray(), output)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun normalizeEmail(email: String?): String? {
        val normalized = email?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return null
        require(normalized.length <= 320 && EMAIL_PATTERN.matches(normalized)) { "Email address is invalid" }
        return normalized
    }

    private data class StoredUser(
        val id: Long,
        val username: String,
        val passwordHash: String,
        val role: String,
        val enabled: Boolean,
    )

    private companion object {
        const val MIN_PASSWORD_LENGTH = 12
        const val MAX_PASSWORD_LENGTH = 1_024
        const val TOKEN_BYTES = 32
        const val MAX_TOKEN_LENGTH = 128
        const val SALT_BYTES = 16
        const val HASH_BYTES = 32
        const val ARGON_VERSION = Argon2Parameters.ARGON2_VERSION_13
        const val MEMORY_KIB = 65_536
        const val ITERATIONS = 3
        const val PARALLELISM = 1
        val USERNAME_PATTERN = Regex("[A-Za-z0-9._-]{3,64}")
        val EMAIL_PATTERN = Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        val ALLOWED_ROLES = setOf("ADMIN", "USER")
        val secureRandom = SecureRandom()
    }
}
