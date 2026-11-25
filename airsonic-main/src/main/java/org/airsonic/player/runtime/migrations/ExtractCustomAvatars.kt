package org.airsonic.player.runtime.migrations

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.sql.SQLException
import liquibase.change.custom.CustomSqlChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.CustomChangeException
import liquibase.exception.DatabaseException
import liquibase.exception.SetupException
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import liquibase.statement.SqlStatement
import org.apache.commons.lang3.StringUtils
import org.airsonic.player.service.SettingsService
import org.slf4j.LoggerFactory

class ExtractCustomAvatars : CustomSqlChange {
    override fun getConfirmationMessage() = "Extracted avatars from DB"

    @Throws(SetupException::class)
    override fun setUp() = Unit

    override fun setFileOpener(resourceAccessor: ResourceAccessor) = Unit

    override fun validate(database: Database) = ValidationErrors()

    @Throws(CustomChangeException::class)
    override fun generateStatements(database: Database): Array<SqlStatement> {
        val connection = database.connection as? JdbcConnection ?: return emptyArray()
        try {
            connection.prepareStatement("select * from custom_avatar").use { statement ->
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        val username = rows.getString("username")
                        try {
                            val folder = SettingsService.getAirsonicHome().resolve("avatars").resolve(username)
                            Files.createDirectories(folder)
                            val extension = StringUtils.substringAfter(rows.getString("mime_type"), "/")
                            val file = folder.resolve(rows.getString("name") + "." + extension)
                            Files.copy(ByteArrayInputStream(rows.getBytes("data")), file)
                        } catch (exception: Exception) {
                            LOG.warn("Exception while trying to extract avatar for user {}. Will skip this user.", username, exception)
                        }
                    }
                }
            }
        } catch (exception: DatabaseException) {
            throw CustomChangeException(exception)
        } catch (exception: SQLException) {
            throw CustomChangeException(exception)
        }
        return emptyArray()
    }

    private companion object {
        val LOG = LoggerFactory.getLogger(ExtractCustomAvatars::class.java)
    }
}
