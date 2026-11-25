package su.nnedition.kord.service

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import su.nnedition.kord.dao.DatabaseDao
import su.nnedition.kord.util.FileUtil
import su.nnedition.kord.util.LambdaUtils
import su.nnedition.kord.util.LegacyHsqlMigrationUtil
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

class DatabaseService(
    private val settingsService: SettingsService,
    private val databaseDao: DatabaseDao,
    private val taskService: TaskSchedulingService,
) {
    private val changeLogs = DatabaseChangeLogSupport()

    data class OperationStatus(
        val version: Long,
        val running: Boolean,
        val failed: Boolean,
        val message: String,
    ) {
        // Keep the former Java record accessors alongside Kotlin properties.
        fun version(): Long = version
        fun running(): Boolean = running
        fun failed(): Boolean = failed
        fun message(): String = message
    }

    @Volatile
    var backupStatus = OperationStatus(0, false, false, "")
        private set

    @Volatile
    var exportStatus = OperationStatus(0, false, false, "")
        private set

    @Volatile
    var importStatus = OperationStatus(0, false, false, "")
        private set

    private val backupTask = Runnable {
        LOG.info("Starting scheduled DB backup")
        backup()
        LOG.info("Completed scheduled DB backup")
    }

    fun init() {
        try {
            schedule()
        } catch (failure: Throwable) {
            LOG.error("Failed to initialize DatabaseService", failure)
        }
    }

    @Synchronized
    private fun schedule() {
        val hoursBetween = settingsService.getDbBackupInterval()
        if (hoursBetween == -1) {
            LOG.info("Automatic DB backup disabled")
            unschedule()
            return
        }

        val firstTime = Instant.now().plusMillis(INITIAL_BACKUP_DELAY_MILLIS)
        taskService.scheduleAtFixedRate("db-backup", backupTask, firstTime, Duration.ofHours(hoursBetween.toLong()), true)
        LOG.info("Automatic DB backup scheduled to run every {} hour(s), starting at {}", hoursBetween, firstTime)
    }

    fun unschedule() {
        taskService.unscheduleTask("db-backup")
    }

    @Synchronized
    fun backup() {
        backupStatus = OperationStatus(backupStatus.version + 1, true, false, STARTED)
        try {
            if (backuppable()) {
                try {
                    val dbPath = StringUtils.substringBetween(settingsService.getDatabaseUrl(), "jdbc:hsqldb:file:", ";")
                    val backupLocation = LegacyHsqlMigrationUtil.performHsqlDbBackup(dbPath)
                    LOG.info("Backed up DB to location: {}", backupLocation)
                    backupStatus = OperationStatus(backupStatus.version, true, false, "location: $backupLocation")
                    deleteObsoleteBackups(backupLocation)
                } catch (failure: Exception) {
                    throw RuntimeException("Failed to backup HSQLDB database", failure)
                }
            } else {
                LOG.info("DB unable to be backed up via these means")
            }
            backupStatus = OperationStatus(
                backupStatus.version,
                false,
                false,
                if (backupStatus.message == STARTED) "Backup complete" else backupStatus.message,
            )
        } catch (failure: RuntimeException) {
            backupStatus = OperationStatus(backupStatus.version, false, true, "Backup failed; check logs")
            throw failure
        }
    }

    @Synchronized
    private fun deleteObsoleteBackups(backupLocation: Path) {
        val backupCount = java.util.concurrent.atomic.AtomicInteger(settingsService.getDbBackupRetentionCount())
        if (backupCount.get() == -1) return

        val backupNamePattern = StringUtils.substringBeforeLast(backupLocation.fileName.toString(), ".")
        try {
            Files.list(backupLocation.parent).use { backups ->
                backups
                    .filter { path -> path.fileName.toString().startsWith(backupNamePattern) }
                    .sorted(
                        Comparator.comparing(
                            LambdaUtils.uncheckFunction<Path, FileTime, Exception> { path ->
                                Files.readAttributes(path, BasicFileAttributes::class.java).creationTime()
                            },
                            Comparator.reverseOrder(),
                        ),
                    )
                    .forEach { path ->
                        if (backupCount.getAndDecrement() <= 0) FileUtil.delete(path)
                    }
            }
        } catch (failure: Exception) {
            LOG.warn("Could not clean up DB backups", failure)
        }
    }

    fun backuppable(): Boolean = settingsService.getDatabaseJNDIName() == null &&
        StringUtils.startsWith(settingsService.getDatabaseUrl(), "jdbc:hsqldb:file:")

    private val exportFunction = changeLogs.exportFunction()

    private val importFunction = changeLogs.importFunction()

    @Synchronized
    fun exportDB(): Path? {
        exportStatus = OperationStatus(exportStatus.version + 1, true, false, STARTED)
        val folder = getChangeLogFolder()
        var zipPath: Path? = null
        try {
            databaseDao.exportDB(folder, exportFunction)
            exportStatus = OperationStatus(exportStatus.version, true, false, "Local DB extraction complete, compressing...")
            zipPath = zip(folder)
            exportStatus = OperationStatus(exportStatus.version, false, false, "Export complete")
        } catch (failure: Exception) {
            LOG.info("DB Export failed!", failure)
            exportStatus = OperationStatus(exportStatus.version, false, true, "Export failed; check logs")
            cleanup(folder)
        }
        return zipPath
    }

    fun cleanup(path: Path) {
        if (Files.isDirectory(path)) {
            FileUtil.delete(path)
        } else {
            FileUtil.delete(path.parent)
        }
    }

    @Throws(Exception::class)
    private fun zip(folder: Path): Path {
        val zipPath = folder.resolve("${folder.fileName}.zip")
        Files.newOutputStream(zipPath).use { output ->
            ZipOutputStream(output).use { zipOutput ->
                Files.list(folder).use { files ->
                    files.filter { file -> file != zipPath }.forEach(
                        LambdaUtils.uncheckConsumer<Path, Exception> { file ->
                            zipOutput.putNextEntry(ZipEntry(file.fileName.toString()))
                            Files.copy(file, zipOutput)
                            zipOutput.closeEntry()
                        },
                    )
                }
            }
        }
        return zipPath
    }

    @Synchronized
    fun importDB(path: Path) {
        importStatus = OperationStatus(importStatus.version + 1, true, false, STARTED)
        try {
            if (Files.notExists(path) || !Files.isDirectory(path) || requireNotNull(path.toFile().list()).isEmpty()) {
                importStatus = OperationStatus(importStatus.version, true, false, "Nothing imported")
            } else {
                backup()
                importStatus = OperationStatus(importStatus.version, true, false, "Importing XML")
                databaseDao.importDB(importFunction.apply(path))
                importStatus = OperationStatus(importStatus.version, true, false, "Import complete. Cleaning up...")
                cleanup(path)
            }
            importStatus = OperationStatus(
                importStatus.version,
                false,
                false,
                if (importStatus.message == "Import complete. Cleaning up...") "Import complete" else importStatus.message,
            )
        } catch (failure: RuntimeException) {
            importStatus = OperationStatus(importStatus.version, false, true, "Import failed; check logs")
            throw failure
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DatabaseService::class.java)
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        private const val INITIAL_BACKUP_DELAY_MILLIS = 5L * 60L * 1000L
        private const val STARTED = "started"

        @JvmStatic
        fun getImportDBFolder(): Path {
            val timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER)
            return SettingsService.getKordinatorHome().resolve("backups").resolve("kordinator.importDB.$timestamp")
        }

        private fun getChangeLogFolder(): Path {
            val timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER)
            return SettingsService.getKordinatorHome().resolve("backups").resolve("kordinator.exportDB.$timestamp")
        }
    }
}
