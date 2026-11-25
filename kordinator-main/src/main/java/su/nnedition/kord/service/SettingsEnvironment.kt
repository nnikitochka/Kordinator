package su.nnedition.kord.service

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import su.nnedition.kord.util.Util
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/** Filesystem locations and executable discovery used by server configuration. */
object SettingsEnvironment {
    private val log = LoggerFactory.getLogger(SettingsEnvironment::class.java)
    private val transcodeExecutableCache: LoadingCache<String, String> = CacheBuilder.newBuilder()
        .expireAfterAccess(8, TimeUnit.HOURS)
        .build(object : CacheLoader<String, String>() {
            override fun load(executable: String): String = sequenceOf(
                getTranscodeDirectory().resolve(executable).toString(),
                executable,
            ).firstOrNull(::isExecutableInstalled) ?: throw NoSuchElementException(executable)
        })

    @JvmStatic
    fun getKordinatorHome(): Path {
        val overrideHome = System.getProperty("kordinator.home")
        val oldHome = System.getProperty("libresonic.home")
        val home = when {
            overrideHome != null -> Paths.get(overrideHome)
            oldHome != null -> Paths.get(oldHome)
            Util.isWindows() -> Paths.get("c:/kordinator")
            else -> Paths.get("/var/kordinator")
        }
        ensureDirectoryPresent(home)
        return home
    }

    /** Returns the directory in which all transcoders are installed. */
    @JvmStatic
    fun getTranscodeDirectory(): Path {
        var directory = getKordinatorHome().resolve("transcode")
        if (!Files.exists(directory)) {
            try {
                directory = Files.createDirectory(directory)
                log.info("Created directory {}", directory)
            } catch (failure: Exception) {
                log.warn("Failed to create directory {}", directory)
            }
        }
        return directory
    }

    @JvmStatic
    fun resolveTranscodeExecutable(executable: String, defaultValue: String?): String? = try {
        transcodeExecutableCache.get(executable)
    } catch (_: Exception) {
        defaultValue
    }

    @JvmStatic
    fun getDefaultJDBCUrl(): String =
        "jdbc:hsqldb:file:${getKordinatorHome().resolve("db").resolve(fileSystemAppName())}" +
            ";hsqldb.tx=mvcc;sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false;" +
            "sql.pad_space=false;hsqldb.defrag_limit=50;shutdown=true"

    @JvmStatic
    fun getDefaultJDBCUsername(): String = "sa"

    @JvmStatic
    fun getDefaultJDBCPassword(): String = ""

    @JvmStatic
    fun getDefaultLogFile(): String = getKordinatorHome().resolve("${fileSystemAppName()}.log").toString()

    @JvmStatic
    fun getPropertyFile(): Path = getKordinatorHome().resolve("${fileSystemAppName()}.properties")

    private fun fileSystemAppName(): String =
        if (getKordinatorHome().toString().contains("libresonic")) "libresonic" else "kordinator"

    private fun isExecutableInstalled(executable: String): Boolean {
        var process: Process? = null
        return try {
            process = ProcessBuilder(executable).start()
            log.debug("{} exists and can be executed. Last executed {}", executable, process.info().startInstant())
            true
        } catch (failure: IOException) {
            log.debug("{} cannot be executed", executable, failure)
            false
        } finally {
            process?.destroy()
        }
    }

    private fun ensureDirectoryPresent(home: Path) {
        if (!Files.exists(home) || !Files.isDirectory(home)) {
            try {
                Files.createDirectories(home)
            } catch (failure: Exception) {
                log.error("Could not create or see home directory {}", home, failure)
                val message = "The directory $home does not exist. Please create it and make it writable. " +
                    "(Set -Dkordinator.home=... or --kordinator.home=... when starting the server.)"
                throw RuntimeException(message)
            }
        }
    }
}
