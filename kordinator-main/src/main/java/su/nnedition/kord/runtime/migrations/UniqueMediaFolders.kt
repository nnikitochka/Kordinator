package su.nnedition.kord.runtime.migrations

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
import liquibase.statement.core.DeleteStatement
import su.nnedition.kord.dao.MusicFolderDao
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.service.media.MediaFolderService
import org.apache.commons.lang3.tuple.Triple
import org.slf4j.LoggerFactory

class UniqueMediaFolders : CustomSqlChange {
    override fun getConfirmationMessage() = "Media folder uniqueness established by path"

    @Throws(SetupException::class)
    override fun setUp() = Unit

    override fun setFileOpener(resourceAccessor: ResourceAccessor) = Unit

    override fun validate(database: Database) = ValidationErrors()

    @Throws(CustomChangeException::class)
    override fun generateStatements(database: Database): Array<SqlStatement> {
        val deletionIds = HashSet<Int>()
        val connection = database.connection as? JdbcConnection
        if (connection != null) {
            try {
                connection.prepareStatement("select * from music_folder order by id").use { statement ->
                    statement.executeQuery().use { rows ->
                        val folders = ArrayList<MusicFolder>()
                        var rowIndex = 0
                        while (rows.next()) folders += MusicFolderDao.MUSICFOLDER_ROW_MAPPER.mapRow(rows, rowIndex++)

                        val paths = HashSet<String>()
                        folders.forEach { folder ->
                            val overlap: Triple<List<MusicFolder>, List<MusicFolder>, List<MusicFolder>> =
                                MediaFolderService.getMusicFolderPathOverlaps(folder, folders)
                            if (overlap.left.isNotEmpty() && !paths.add(folder.path.toString())) {
                                folder.id?.let(deletionIds::add)
                                LOG.info("Duplicate media folder found (id: {}, name: {}) and will be deleted", folder.id, folder.name)
                            }
                        }
                    }
                }
            } catch (exception: DatabaseException) {
                throw CustomChangeException(exception)
            } catch (exception: SQLException) {
                throw CustomChangeException(exception)
            }
        }

        return deletionIds.flatMap { id ->
            listOf(
                DeleteStatement(database.defaultCatalogName, database.defaultSchemaName, "music_folder_user")
                    .setWhere("music_folder_id=?")
                    .addWhereParameter(id),
                DeleteStatement(database.defaultCatalogName, database.defaultSchemaName, "music_folder")
                    .setWhere("id=?")
                    .addWhereParameter(id),
            )
        }.toTypedArray()
    }

    private companion object {
        val LOG = LoggerFactory.getLogger(UniqueMediaFolders::class.java)
    }
}
