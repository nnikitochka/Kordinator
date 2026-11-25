package su.nnedition.kord.util

import su.nnedition.kord.service.SettingsService
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.function.Function

object LegacyHsqlMigrationUtil {
    private val log = LoggerFactory.getLogger(LegacyHsqlMigrationUtil::class.java)

    /** Returns the HSQLDB version recorded in the database properties file, if present. */
    @JvmStatic
    fun getHsqlDbVersion(dbPath: String): String? {
        val path = Paths.get("$dbPath.properties")
        if (!Files.exists(path)) return null
        return try {
            Files.newInputStream(path).use { input: InputStream ->
                Properties().apply { load(input) }.getProperty("version")
            }
        } catch (exception: Exception) {
            log.warn("Failed to determine HSQLDB database version", exception)
            null
        }
    }

    /** Creates a direct connection for the HSQLDB upgrade, outside the application datasource. */
    @JvmStatic
    @Throws(SQLException::class)
    fun getHsqlDbConnection(url: String, user: String, password: String): Connection =
        DriverManager.getConnection(url, Properties().apply {
            put("user", user)
            put("password", password)
        })

    /** Whether an HSQLDB file should be copied before connecting with the configured driver. */
    @JvmStatic
    fun isHsqlDbBackupNeeded(dbPath: String, jdbcUrl: String): Boolean {
        val currentVersion = getHsqlDbVersion(dbPath)
        if (currentVersion == null) {
            log.debug("HSQLDB database not found, won't back up")
            return false
        }

        try {
            // This check runs before datasource initialization, so load the HSQLDB driver directly.
            Class.forName("org.hsqldb.jdbc.JDBCDriver")
        } catch (exception: ClassNotFoundException) {
            throw RuntimeException("Unable to load HSQLDB driver", exception)
        }

        var driverVersion: String? = null
        try {
            val driver: Driver = DriverManager.getDriver(jdbcUrl)
            driverVersion = "%d.%d".format(driver.majorVersion, driver.minorVersion)
            if (driver.majorVersion != 2) {
                log.warn(
                    "HSQLDB database driver version {} is untested. Will make a copy of the DB prior to connecting. This may upgrade the database from version {}",
                    driverVersion,
                    currentVersion,
                )
                return true
            }
        } catch (exception: SQLException) {
            log.warn(
                "HSQLDB database driver version cannot be determined. Will make a copy of the DB prior to connecting. This may upgrade the database from version {}",
                currentVersion,
                exception,
            )
            return true
        }

        return when {
            currentVersion.startsWith("2.") -> {
                log.debug(
                    "HSQLDB database backup not required for driver version {} connecting (and if needed, upgrading) DB version {}",
                    driverVersion,
                    currentVersion,
                )
                false
            }
            currentVersion.startsWith("1.") -> {
                log.info("HSQLDB database upgrade needed, from version {} to {}", currentVersion, driverVersion)
                true
            }
            else -> {
                log.warn("HSQLDB database upgrade needed, from version {} to {}", currentVersion, driverVersion)
                true
            }
        }
    }

    /** Copies the database directory to a timestamped sibling backup directory. */
    @JvmStatic
    @Throws(IOException::class)
    fun performHsqlDbBackup(dbPath: String): Path {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
        val source = Paths.get(dbPath).parent
        val destination = source.resolveSibling("backups")
            .resolve("${source.fileName}.backup.$timestamp")
        log.debug("Performing HSQLDB database backup...")
        FileUtils.copyDirectory(source.toFile(), destination.toFile())
        log.info("HSQLDB database backed up to {}", destination)
        return destination
    }

    /** Performs an in-place HSQLDB 1.x to 2.x upgrade. */
    @JvmStatic
    @Throws(SQLException::class)
    fun performHsqlDbUpgrade(url: String, user: String, password: String) {
        log.debug("Performing HSQLDB database upgrade...")
        getHsqlDbConnection(url, user, password).use { connection ->
            log.debug("Database connection established. Current version is: {}", connection.metaData.databaseProductVersion)
            log.debug("Shutting down database (SHUTDOWN SCRIPT)...")
            connection.createStatement().use { it.execute("SHUTDOWN SCRIPT") }
        }
        log.info("HSQLDB database has been upgraded")
    }

    /** Backs up and upgrades an existing file database when the HSQLDB version requires it. */
    @JvmStatic
    fun upgradeFileHsqlDbIfNeeded(getProperty: Function<String, String?>) {
        val jdbcUrl = getProperty.apply(SettingsService.KEY_DATABASE_URL)
        val dbPath = StringUtils.substringBetween(jdbcUrl, "jdbc:hsqldb:file:", ";")
        val jndi = getProperty.apply(SettingsService.KEY_DATABASE_JNDI_NAME)

        if (jndi == null && dbPath != null && isHsqlDbBackupNeeded(dbPath, jdbcUrl!!)) {
            try {
                performHsqlDbBackup(dbPath)
            } catch (exception: Exception) {
                throw RuntimeException("Failed to backup HSQLDB database before upgrade", exception)
            }
            try {
                val user = getProperty.apply(SettingsService.KEY_DATABASE_USERNAME)
                val password = getProperty.apply(SettingsService.KEY_DATABASE_PASSWORD)
                performHsqlDbUpgrade(jdbcUrl, user!!, password!!)
                log.info("HSQLDB database version is now {}", getHsqlDbVersion(dbPath))
            } catch (exception: Exception) {
                throw RuntimeException("Failed to upgrade HSQLDB database", exception)
            }
        }
    }
}
