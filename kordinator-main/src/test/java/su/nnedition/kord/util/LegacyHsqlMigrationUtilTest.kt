package su.nnedition.kord.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LegacyHsqlMigrationUtilTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun missingDatabasePropertiesHaveNoVersionAndNeedNoBackup() {
        val database = temporaryDirectory.resolve("database")

        assertEquals(null, LegacyHsqlMigrationUtil.getHsqlDbVersion(database.toString()))
        assertFalse(LegacyHsqlMigrationUtil.isHsqlDbBackupNeeded(database.toString(), "jdbc:hsqldb:file:$database"))
    }

    @Test
    fun readsDatabaseVersionFromProperties() {
        val database = temporaryDirectory.resolve("database")
        Files.writeString(temporaryDirectory.resolve("database.properties"), "version=2.7.2\n")

        assertEquals("2.7.2", LegacyHsqlMigrationUtil.getHsqlDbVersion(database.toString()))
    }

    @Test
    fun currentHsqlVersionDoesNotRequireBackupButLegacyAndUnknownVersionsDo() {
        Class.forName("org.hsqldb.jdbc.JDBCDriver")
        val database = temporaryDirectory.resolve("database")
        val jdbcUrl = "jdbc:hsqldb:file:$database"
        val properties = temporaryDirectory.resolve("database.properties")

        Files.writeString(properties, "version=2.7.2\n")
        assertFalse(LegacyHsqlMigrationUtil.isHsqlDbBackupNeeded(database.toString(), jdbcUrl))

        Files.writeString(properties, "version=1.8.0\n")
        assertTrue(LegacyHsqlMigrationUtil.isHsqlDbBackupNeeded(database.toString(), jdbcUrl))

        Files.writeString(properties, "version=unknown\n")
        assertTrue(LegacyHsqlMigrationUtil.isHsqlDbBackupNeeded(database.toString(), jdbcUrl))
    }

    @Test
    fun backupCopiesDatabaseDirectoryToTimestampedSibling() {
        val databaseDirectory = Files.createDirectory(temporaryDirectory.resolve("data"))
        Files.writeString(databaseDirectory.resolve("database.properties"), "version=2.7.2\n")
        Files.writeString(databaseDirectory.resolve("database.script"), "CREATE SCHEMA PUBLIC\n")

        val backup = LegacyHsqlMigrationUtil.performHsqlDbBackup(databaseDirectory.resolve("database").toString())

        assertTrue(Files.isDirectory(backup))
        assertNotNull(backup.fileName.toString().takeIf { it.startsWith("data.backup.") })
        assertTrue(Files.exists(backup.resolve("database.script")))
    }
}
