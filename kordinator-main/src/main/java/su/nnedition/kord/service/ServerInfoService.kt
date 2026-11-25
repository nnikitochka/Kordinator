package su.nnedition.kord.service

import org.apache.commons.io.input.ReversedLinesFileReader
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Paths

class ServerInfoService(private val versions: VersionService, private val settings: SettingsService) {
    fun snapshot(serverInfo: String): Map<String, Any?> {
        val latest = when {
            versions.isNewFinalVersionAvailable() -> versions.getLatestFinalVersion()
            versions.isNewBetaVersionAvailable() -> versions.getLatestBetaVersion()
            else -> null
        }
        val runtime = Runtime.getRuntime()
        val total = runtime.totalMemory()
        return linkedMapOf(
            "brand" to settings.getBrand(),
            "localVersion" to versions.getLocalVersion()?.toString(),
            "buildDate" to versions.getLocalBuildDate()?.toString(),
            "buildNumber" to versions.getLocalBuildNumber(),
            "newVersionAvailable" to (latest != null),
            "latestVersion" to latest?.toString(),
            "latestVersionUrl" to latest?.url,
            "serverInfo" to "$serverInfo, java ${System.getProperty("java.version")}, ${System.getProperty("os.name")}",
            "usedMemory" to (total - runtime.freeMemory()),
            "totalMemory" to total
        )
    }

    fun log(): Map<String, Any?> {
        val path = Paths.get(settings.getLogFile())
        val lines = ArrayDeque<String>()
        val available = try {
            ReversedLinesFileReader(path.toFile(), Charset.defaultCharset()).use { reader ->
                repeat(50) {
                    val line = reader.readLine() ?: return@use
                    lines.addFirst(line)
                }
            }
            true
        } catch (e: IOException) {
            LOG.warn("Could not open log file {}", path, e)
            false
        }
        return mapOf("logFile" to path.toString(), "logEntries" to lines.toList(), "available" to available)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ServerInfoService::class.java)
    }
}
