package org.airsonic.player.dao

import com.fasterxml.jackson.core.type.TypeReference
import org.airsonic.player.domain.User
import org.airsonic.player.domain.UserCredential
import org.airsonic.player.domain.UserCredential.App
import org.airsonic.player.domain.UserSettings
import org.airsonic.player.util.Util
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.function.Predicate

/** Provides user, credential, and user-settings persistence. */
class UserDao(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val userRowMapper = UserRowMapper()
    private val userSettingsRowMapper = UserSettingsRowMapper()
    private val userCredentialRowMapper = UserCredentialRowMapper()

    fun getUserByName(username: String?, caseSensitive: Boolean): User? {
        val condition = if (caseSensitive) "username=?" else "UPPER(username)=UPPER(?)"
        val users = query("select $USER_COLUMNS from users where $condition", userRowMapper, username)
        if (users.size > 1) throw RuntimeException("Too many matching users")
        return users.singleOrNull()
    }

    fun getCredentials(username: String, vararg apps: App): List<UserCredential> {
        if (apps.isEmpty()) return emptyList()
        val args = mapOf("user" to username, "apps" to apps.map(App::name))
        return namedQuery(
            "select $USER_CREDENTIALS_COLUMNS from user_credentials where username=:user and app in (:apps)",
            userCredentialRowMapper,
            args
        )
    }

    fun getCredentialsByEncoder(encoderPatternMatcher: String): List<UserCredential> = query(
        "select $USER_CREDENTIALS_COLUMNS from user_credentials where encoder like ?",
        userCredentialRowMapper,
        encoderPatternMatcher
    )

    fun getCredentialCountByEncoder(encoderPatternMatcher: String): Int? = queryForInt(
        "select count(*) from user_credentials where encoder like ?", 0, encoderPatternMatcher
    )

    fun updateCredential(oldCreds: UserCredential, newCreds: UserCredential): Boolean {
        val sql = "update user_credentials set app_username=?, credential=?, encoder=?, app=?, updated=?, expiration=? " +
            "where username=? and app_username=? and credential=? and encoder=? and app=? and created=? and updated=?"
        return update(
            sql, newCreds.appUsername, newCreds.credential, newCreds.encoder, newCreds.app!!.name,
            newCreds.updated, newCreds.expiration, oldCreds.username, oldCreds.appUsername, oldCreds.credential,
            oldCreds.encoder, oldCreds.app!!.name, oldCreds.created, oldCreds.updated
        ) == 1
    }

    fun createCredential(credential: UserCredential): Boolean {
        val sql = "insert into user_credentials ($USER_CREDENTIALS_COLUMNS) " +
            "values (${questionMarks(USER_CREDENTIALS_COLUMNS)})"
        return update(
            sql, credential.username, credential.appUsername, credential.credential, credential.encoder,
            credential.app!!.name, credential.created, credential.updated, credential.expiration, credential.comment
        ) == 1
    }

    fun deleteCredential(credential: UserCredential, postDeletionCheck: Predicate<UserCredential>): Boolean =
        inTransaction {
            var sql = "delete from user_credentials where username=:username and app_username=:app_username " +
                "and credential=:credential and encoder=:encoder and app=:app and created=:created and updated=:updated"
            val args = mutableMapOf<String, Any?>(
                "username" to credential.username,
                "app_username" to credential.appUsername,
                "credential" to credential.credential,
                "encoder" to credential.encoder,
                "app" to credential.app!!.name,
                "created" to credential.created,
                "updated" to credential.updated
            )
            if (credential.expiration != null) {
                sql += " and expiration=:expiration"
                args["expiration"] = credential.expiration
            }
            val deleteSuccess = namedUpdate(sql, args) == 1
            if (!postDeletionCheck.test(credential)) {
                throw RuntimeException("Cannot delete a credential due to failed post deletion check")
            }
            deleteSuccess
        }

    fun getUserByEmail(email: String): User? = queryOne(
        "select $USER_COLUMNS from users where email=?", userRowMapper, email
    )

    fun getAllUsers(): List<User> = query("select $USER_COLUMNS from users", userRowMapper)

    fun createUser(user: User, credential: UserCredential) {
        inTransaction {
            val sql = "insert into users ($USER_COLUMNS) values (${questionMarks(USER_COLUMNS)})"
            update(
                sql, user.username, user.email, user.isLdapAuthenticated,
                user.bytesStreamed, user.bytesDownloaded, user.bytesUploaded, Util.toJson(user.roles)
            )
            createCredential(credential)
        }
    }

    fun deleteUser(username: String) {
        inTransaction {
            if (User.USERNAME_ADMIN == username) throw IllegalArgumentException("Can't delete admin user.")
            update("delete from player where username=?", username)
            update("delete from user_credentials where username=?", username)
            update("delete from users where username=?", username)
        }
    }

    fun updateUser(user: User) {
        val sql = "update users set email=?, ldap_authenticated=?, bytes_streamed=?, bytes_downloaded=?, " +
            "bytes_uploaded=?, roles=? where username=?"
        update(
            sql, user.email, user.isLdapAuthenticated, user.bytesStreamed, user.bytesDownloaded,
            user.bytesUploaded, Util.toJson(user.roles), user.username
        )
    }

    fun updateUserByteCounts(user: String, bytesStreamedDelta: Long, bytesDownloadedDelta: Long, bytesUploadedDelta: Long) {
        update(
            "update users set bytes_streamed=bytes_streamed+?, bytes_downloaded=bytes_downloaded+?, " +
                "bytes_uploaded=bytes_uploaded+? where username=?",
            bytesStreamedDelta, bytesDownloadedDelta, bytesUploadedDelta, user
        )
    }

    fun getUserSettings(username: String): UserSettings? = queryOne(
        "select $USER_SETTINGS_COLUMNS from user_settings where username=?", userSettingsRowMapper, username
    )

    fun updateUserSettings(settings: UserSettings): Boolean = inTransaction {
        update("delete from user_settings where username=?", settings.username)
        val sql = "insert into user_settings ($USER_SETTINGS_COLUMNS) " +
            "values (${questionMarks(USER_SETTINGS_COLUMNS)})"
        update(sql, settings.username, Util.toJson(settings)) == 1
    }

    private class UserCredentialRowMapper : RowMapper<UserCredential> {
        override fun mapRow(rs: ResultSet, rowNum: Int): UserCredential = UserCredential(
            rs.getString("username"),
            rs.getString("app_username"),
            rs.getString("credential"),
            rs.getString("encoder"),
            App.valueOf(rs.getString("app")),
            rs.getString("comment"),
            rs.getTimestamp("expiration").toInstantOrNull(),
            rs.getTimestamp("created").toInstant(),
            rs.getTimestamp("updated").toInstant()
        )
    }

    private class UserRowMapper : RowMapper<User> {
        override fun mapRow(rs: ResultSet, rowNum: Int): User {
            val roleNames = Util.fromJson(rs.getString("roles"), object : TypeReference<Set<String>>() {})
                ?: emptySet()
            val roles = User.Role.values().filter { it.name in roleNames }.toSet()
            return User(
                rs.getString("username"), rs.getString("email"), rs.getBoolean("ldap_authenticated"),
                rs.getLong("bytes_streamed"), rs.getLong("bytes_downloaded"), rs.getLong("bytes_uploaded"), roles
            )
        }
    }

    private class UserSettingsRowMapper : RowMapper<UserSettings> {
        override fun mapRow(rs: ResultSet, rowNum: Int): UserSettings =
            Util.fromJson(rs.getString("settings"), UserSettings::class.java)
    }

    private companion object {
        private const val USER_COLUMNS =
            "username, email, ldap_authenticated, bytes_streamed, bytes_downloaded, bytes_uploaded, roles"
        private const val USER_SETTINGS_COLUMNS = "username, settings"
        private const val USER_CREDENTIALS_COLUMNS =
            "username, app_username, credential, encoder, app, created, updated, expiration, comment"
    }
}

private fun Timestamp?.toInstantOrNull(): Instant? = this?.toInstant()
