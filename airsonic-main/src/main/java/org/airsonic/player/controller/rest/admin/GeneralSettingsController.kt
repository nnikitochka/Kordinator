package org.airsonic.player.controller.rest.admin

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.command.GeneralSettingsCommand
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.service.GeneralSettingsAdministrationService
import org.airsonic.player.domain.CoverArtSource
import org.subsonic.restapi.GeneralSettings
import org.subsonic.restapi.GeneralSettingsOption
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException

/** Admin-only extensions. Save requires every setting, explicit true/false booleans and browser CSRF. */
class GeneralSettingsController(private val service: GeneralSettingsAdministrationService, private val writer: JAXBWriter) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        with(root) {
            get(READ_PATH) { respond(call, request(call), "read") }
            WRITE_PATHS.forEach { path -> post(path) { respond(call, request(call), path.substringAfterLast('/')) } }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: UsersRequest, operation: String) {
        val response = try {
            if (!request.user.isAdminRole) writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Administrator access required")
            else {
                when (operation) {
                    "saveGeneralSettings" -> service.save(parse(request.parameters))
                }
                writer.createResponse(true).apply { if (operation == "read") generalSettings = read() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Invalid settings")
        } catch (e: Exception) {
            LOG.warn("General settings administration failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "General settings administration failed")
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], response)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun read(): GeneralSettings {
        val command = service.settings
        return GeneralSettings().apply {
            playlistFolder = command.playlistFolder.orEmpty()
            musicFileTypes = command.musicFileTypes.orEmpty()
            videoFileTypes = command.videoFileTypes.orEmpty()
            coverArtFileTypes = command.coverArtFileTypes.orEmpty()
            index = command.index.orEmpty()
            ignoredArticles = command.ignoredArticles.orEmpty()
            genreSeparators = command.genreSeparators.orEmpty()
            shortcuts = command.shortcuts.orEmpty()
            welcomeTitle = command.welcomeTitle.orEmpty()
            welcomeSubtitle = command.welcomeSubtitle.orEmpty()
            welcomeMessage = command.welcomeMessage.orEmpty()
            loginMessage = command.loginMessage.orEmpty()
            sessionDuration = command.sessionDuration.orEmpty()
            sortAlbumsByYear = command.isSortAlbumsByYear
            gettingStartedEnabled = command.isGettingStartedEnabled
            coverArtSource = command.coverArtSource.name
            coverArtQuality = command.coverArtQuality; coverArtConcurrency = command.coverArtConcurrency
            themes.theme.addAll(command.themes.map { theme -> GeneralSettingsOption().apply { id = theme.id; name = theme.name } })
            locales.locale.addAll(service.availableLocales.map { locale -> GeneralSettingsOption().apply { id = locale.toLanguageTag(); name = locale.getDisplayName(locale) } })
            themeId = command.themeIndex?.toIntOrNull()?.let { command.themes[it].id }.orEmpty()
            locale = command.localeIndex?.toIntOrNull()?.let { service.availableLocales[it].toLanguageTag() }.orEmpty()
        }
    }

    private fun parse(p: Parameters): GeneralSettingsCommand {
        fun text(name: String) = p[name] ?: throw IllegalArgumentException("Missing $name")
        fun bool(name: String) = when (text(name)) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid boolean: $name")
        }
        val command = GeneralSettingsCommand()
        command.playlistFolder = text("playlistFolder")
        command.musicFileTypes = text("musicFileTypes")
        command.videoFileTypes = text("videoFileTypes")
        command.coverArtFileTypes = text("coverArtFileTypes")
        command.index = text("index")
        command.ignoredArticles = text("ignoredArticles")
        command.genreSeparators = text("genreSeparators")
        command.shortcuts = text("shortcuts")
        command.welcomeTitle = text("welcomeTitle")
        command.welcomeSubtitle = text("welcomeSubtitle")
        command.welcomeMessage = text("welcomeMessage")
        command.loginMessage = text("loginMessage")
        command.sessionDuration = text("sessionDuration")
        command.isSortAlbumsByYear = bool("sortAlbumsByYear")
        command.isGettingStartedEnabled = bool("gettingStartedEnabled")
        command.coverArtSource = CoverArtSource.valueOf(text("coverArtSource"))
        command.coverArtQuality = text("coverArtQuality").toIntOrNull()?.takeIf { it in 0..100 }
            ?: throw IllegalArgumentException("Invalid cover art quality (0..100)")
        command.coverArtConcurrency = text("coverArtConcurrency").toIntOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Invalid cover art concurrency")
        val settings = service.settings
        val theme = settings.themes.indexOfFirst { it.id == text("themeId") }
        val locale = service.availableLocales.indexOfFirst { it.toLanguageTag() == text("locale") }
        require(theme >= 0) { "Unknown theme" }; require(locale >= 0) { "Unknown locale" }
        command.themeIndex = theme.toString(); command.localeIndex = locale.toString()
        java.nio.file.Paths.get(command.playlistFolder)
        val duration = parseDuration(command.sessionDuration)
        require(!duration.isNegative && !duration.isZero) { "Session duration must be positive" }
        return command
    }

    private fun parseDuration(value: String): Duration {
        val normalized = value.trim()
        if (normalized.startsWith("P", ignoreCase = true) || normalized.startsWith("-P", ignoreCase = true)
            || normalized.startsWith("+P", ignoreCase = true)) {
            return Duration.parse(normalized.uppercase())
        }
        val match = SIMPLE_DURATION.matchEntire(normalized)
            ?: throw IllegalArgumentException("Invalid session duration")
        val amount = match.groupValues[1].toLong()
        return when (match.groupValues[2].lowercase()) {
            "", "ms" -> Duration.ofMillis(amount)
            "ns" -> Duration.ofNanos(amount)
            "us" -> Duration.ofNanos(Math.multiplyExact(amount, 1_000))
            "s" -> Duration.ofSeconds(amount)
            "m" -> Duration.ofMinutes(amount)
            "h" -> Duration.ofHours(amount)
            "d" -> Duration.ofDays(amount)
            else -> throw IllegalArgumentException("Invalid session duration")
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GeneralSettingsController::class.java)
        private val SIMPLE_DURATION = Regex("([+-]?\\d+)(ns|us|ms|s|m|h|d)?", RegexOption.IGNORE_CASE)
        const val READ_PATH = "/rest/getGeneralSettings"
        @JvmField val WRITE_PATHS = arrayOf("/rest/saveGeneralSettings")
    }
}
