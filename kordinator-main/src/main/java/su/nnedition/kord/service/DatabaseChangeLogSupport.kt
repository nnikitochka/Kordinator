package su.nnedition.kord.service

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.text.MessageFormat
import java.util.function.Consumer
import java.util.function.Function
import liquibase.Contexts
import liquibase.Liquibase
import liquibase.command.CommandScope
import liquibase.command.core.ExecuteSqlCommandStep
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep
import liquibase.database.Database
import liquibase.database.DatabaseConnection
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.diff.output.DiffOutputControl
import liquibase.diff.output.StandardObjectChangeFilter
import liquibase.integration.commandline.CommandLineUtils
import liquibase.resource.FileSystemResourceAccessor
import su.nnedition.kord.util.LambdaUtils
import su.nnedition.kord.util.LambdaUtils.ThrowingBiFunction

/** Liquibase-specific serialization of database contents to and from changelog files. */
internal class DatabaseChangeLogSupport {
    fun exportFunction(): ThrowingBiFunction<Path, Connection, Boolean, Exception> =
        ThrowingBiFunction { path, connection ->
            generateChangeLog(path, connection, "data", "kordinator-data", DiffOutputControl(false, false, false, null))
        }

    fun importFunction(): Function<Path, Consumer<Connection>> = Function { path ->
        LambdaUtils.uncheckConsumer<Connection, Exception> { connection -> runLiquibaseUpdate(connection, path) }
    }

    private fun runLiquibaseUpdate(connection: Connection, path: Path) {
        val database = getDatabase(connection)
        truncateAll(database)
        Files.list(path).use { files ->
            files.sorted().forEach(
                LambdaUtils.uncheckConsumer<Path, Exception> { file ->
                    Liquibase(path.relativize(file).toString(), FileSystemResourceAccessor(path.toFile()), database)
                        .update(Contexts())
                },
            )
        }
    }

    private fun truncateAll(database: Database) {
        val sql = TABLE_ORDER.asSequence().flatten().joinToString("; ") { table -> "delete from $table" }
        val command = CommandScope(*ExecuteSqlCommandStep.COMMAND_NAME)
        command.addArgumentValue(ExecuteSqlCommandStep.SQL_ARG, sql)
        command.addArgumentValue(ExecuteSqlCommandStep.DELIMITER_ARG, ";")
        command.addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
        command.execute()
    }

    private fun generateChangeLog(
        folder: Path,
        connection: Connection,
        snapshotTypes: String,
        author: String,
        diffOutputControl: DiffOutputControl,
    ): Boolean {
        val database = getDatabase(connection)
        Files.createDirectories(folder)
        TABLE_ORDER.forEachIndexed { index, tables ->
            setTableFilter(diffOutputControl, tables)
            CommandLineUtils.doGenerateChangeLog(
                folder.resolve("$index.xml").toString(), database, null, null,
                snapshotTypes, author, null, null, diffOutputControl,
            )
        }
        return true
    }

    private fun getDatabase(connection: Connection): Database {
        val databaseConnection: DatabaseConnection = JdbcConnection(connection)
        return DatabaseFactory.getInstance().findCorrectDatabaseImplementation(databaseConnection)
    }

    private fun setTableFilter(diffOutputControl: DiffOutputControl, tables: List<String>) {
        val filter = StandardObjectChangeFilter(
            StandardObjectChangeFilter.FilterType.INCLUDE,
            tables.joinToString(",") { table -> MessageFormat.format("table:(?i){0}", table) },
        )
        diffOutputControl.setObjectChangeFilter(filter)
    }

    private companion object {
        val TABLE_ORDER = listOf(
            listOf("users", "music_folder", "transcoding", "player"),
            listOf("music_folder_user", "user_credentials", "user_settings", "player_transcoding"),
            listOf("media_file", "music_file_info"),
            listOf("playlist", "play_queue"),
            listOf("playlist_file", "playlist_user", "play_queue_file"),
            listOf("album", "artist", "genre"),
            listOf("share"),
            listOf("cover_art"),
            listOf("bookmark", "share_file", "sonoslink"),
            listOf("starred_album", "starred_artist", "starred_media_file", "user_rating", "custom_avatar"),
        )
    }
}
