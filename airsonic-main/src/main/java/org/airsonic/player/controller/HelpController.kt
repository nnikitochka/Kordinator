package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.VersionService
import org.apache.commons.io.input.ReversedLinesFileReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Controller for the help page.
 */
@Controller
@RequestMapping("/help")
class HelpController(
    private val versionService: VersionService,
    private val settingsService: SettingsService,
    private val securityService: SecurityService
) {
    @GetMapping
    protected fun handleRequestInternal(request: HttpServletRequest, response: HttpServletResponse?): ModelAndView {
        val map = buildMap {
            if (versionService.isNewFinalVersionAvailable()) {
                put("newVersionAvailable", true)
                put("latestVersion", versionService.getLatestFinalVersion())
            } else if (versionService.isNewBetaVersionAvailable()) {
                put("newVersionAvailable", true)
                put("latestVersion", versionService.getLatestBetaVersion())
            }

            val totalMemory = Runtime.getRuntime().totalMemory()
            val freeMemory = Runtime.getRuntime().freeMemory()

            val serverInfo = request.session.servletContext.serverInfo +
                    ", java " + System.getProperty("java.version") +
                    ", " + System.getProperty("os.name")

            put("user", securityService.getCurrentUser(request))
            put("brand", settingsService.brand)
            put("localVersion", versionService.getLocalVersion())
            put("buildDate", versionService.getLocalBuildDate())
            put("buildNumber", versionService.getLocalBuildNumber())
            put("serverInfo", serverInfo)
            put("usedMemory", totalMemory - freeMemory)
            put("totalMemory", totalMemory)
            val logFile = Paths.get(settingsService.logFile)
            val latestLogEntries: MutableList<String>? = getLatestLogEntries(logFile)
            put("logEntries", latestLogEntries)
            put("logFile", logFile)
        }

        return ModelAndView("help", "model", map)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(HelpController::class.java)

        private const val LOG_LINES_TO_SHOW = 50

        private fun getLatestLogEntries(logFile: Path): MutableList<String>? {
            val lines: MutableList<String> = LinkedList()
            try {
                ReversedLinesFileReader(logFile.toFile(), Charset.defaultCharset()).use { reader ->
                    var current: String?
                    while ((reader.readLine().also { current = it }) != null) {
                        if (lines.size >= LOG_LINES_TO_SHOW) {
                            break
                        }
                        lines.addFirst(current!!)
                    }
                    return lines
                }
            } catch (e: IOException) {
                LOG.warn("Could not open log file {}", logFile, e)
                return null
            }
        }
    }
}
