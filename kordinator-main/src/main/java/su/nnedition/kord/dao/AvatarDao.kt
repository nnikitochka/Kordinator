package su.nnedition.kord.dao

import su.nnedition.kord.domain.Avatar
import su.nnedition.kord.service.SettingsService
import org.apache.commons.lang3.StringUtils
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/** Provides database services for system and custom avatars. */
class AvatarDao(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val rowMapper = AvatarRowMapper()

    fun getAllSystemAvatars(): List<Avatar> = query(
        "select $QUERY_COLUMNS from system_avatar", rowMapper
    )

    fun getSystemAvatar(id: Int): Avatar? = queryOne(
        "select $QUERY_COLUMNS from system_avatar where id=?", rowMapper, id
    )

    fun getCustomAvatar(username: String): Avatar? = queryOne(
        "select $QUERY_COLUMNS from custom_avatar where username=?", rowMapper, username
    )

    fun setCustomAvatar(avatar: Avatar?, username: String) {
        inTransaction(TransactionManager.ISOLATION_READ_COMMITTED) {
            update("delete from custom_avatar where username=?", username)
            if (avatar != null) {
                val storedPath = StringUtils.replace(
                    avatar.path.toString(),
                    SettingsService.getKordinatorHome().toString(),
                    KORDINATOR_HOME_MARKER
                )
                update(
                    "insert into custom_avatar($INSERT_COLUMNS, username) " +
                        "values (${questionMarks(INSERT_COLUMNS)}, ?)",
                    avatar.name, avatar.createdDate, avatar.mimeType, avatar.width, avatar.height, storedPath, username
                )
            }
        }
    }

    private class AvatarRowMapper : RowMapper<Avatar> {
        override fun mapRow(rs: ResultSet, rowNum: Int): Avatar = Avatar(
            rs.getInt(1),
            rs.getString(2),
            rs.getTimestamp(3).toInstantOrNull(),
            rs.getString(4),
            rs.getInt(5),
            rs.getInt(6),
            StringUtils.replace(
                rs.getString(7), KORDINATOR_HOME_MARKER, SettingsService.getKordinatorHome().toString()
            )
        )
    }

    private companion object {
        private const val INSERT_COLUMNS = "name, created_date, mime_type, width, height, path"
        private const val QUERY_COLUMNS = "id, $INSERT_COLUMNS"
        private const val KORDINATOR_HOME_MARKER = "\$[KORDINATOR_HOME]"
    }
}

private fun Timestamp?.toInstantOrNull(): Instant? = this?.toInstant()
