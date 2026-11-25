package org.airsonic.player.runtime.migrations

import java.nio.file.Paths
import java.sql.SQLException
import java.util.ArrayList
import java.util.HashSet
import liquibase.change.custom.CustomSqlChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.CustomChangeException
import liquibase.exception.DatabaseException
import liquibase.exception.SetupException
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import liquibase.statement.SqlStatement
import liquibase.statement.core.InsertStatement

class AddUnknownMediaFolders : CustomSqlChange {
    override fun getConfirmationMessage() = "Added unknown music folders"

    @Throws(SetupException::class)
    override fun setUp() = Unit

    override fun setFileOpener(resourceAccessor: ResourceAccessor) = Unit

    override fun validate(database: Database) = ValidationErrors()

    @Throws(CustomChangeException::class)
    override fun generateStatements(database: Database): Array<SqlStatement> {
        val unknownFolders = HashSet<String>()
        var maxFolderId: Int? = null
        val connection = database.connection as? JdbcConnection
        if (connection != null) {
            try {
                connection.prepareStatement("SELECT MAX(id) AS maxid FROM music_folder;").use { maxStatement ->
                    maxStatement.executeQuery().use { rows ->
                        while (rows.next()) maxFolderId = rows.getInt("maxid")
                    }
                }
                connection.prepareStatement(
                    "SELECT m.folder FROM media_file m LEFT OUTER JOIN music_folder f ON m.folder = f.path WHERE f.path IS NULL;",
                ).use { folderStatement ->
                    folderStatement.executeQuery().use { rows ->
                        while (rows.next()) unknownFolders.add(rows.getString("folder"))
                    }
                }
            } catch (exception: DatabaseException) {
                throw CustomChangeException(exception)
            } catch (exception: SQLException) {
                throw CustomChangeException(exception)
            }
        }

        val folderList = ArrayList(unknownFolders)
        val firstFolderId = (maxFolderId ?: 0) + 1
        return folderList.indices.flatMap { index ->
            val id = firstFolderId + index
            listOf(
                InsertStatement(database.defaultCatalogName, database.defaultSchemaName, "music_folder")
                    .addColumnValue("id", id)
                    .addColumnValue("path", folderList[index])
                    .addColumnValue("name", Paths.get(folderList[index]).fileName.toString())
                    .addColumnValue("enabled", false),
                InsertStatement(database.defaultCatalogName, database.defaultSchemaName, "music_folder_user")
                    .addColumnValue("music_folder_id", id)
                    .addColumnValue("username", "admin"),
            )
        }.toTypedArray()
    }
}
