package su.nnedition.kord.api.module.admin.settings

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.command.GeneralSettingsCommand
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.service.SettingsService
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.support.jsonMapper
import su.nnedition.kord.api.support.respondJson
import su.nnedition.kord.api.support.saveTranscodingSettings
import su.nnedition.kord.api.support.toCommand
import su.nnedition.kord.api.support.toMusicFolderSettingsResponse
import su.nnedition.kord.api.support.toResponse
import su.nnedition.kord.api.support.updateAdvancedSettings
import su.nnedition.kord.api.support.validateGeneralSettings

fun Route.adminSettingsRoutes(
    context: ApiContext,
) {
    get("/admin/settings/general") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@get
        }
        val service = context.generalSettingsService
            ?: return@get call.respondJson(
            ApiError(
                "unavailable",
                "General settings are not configured"
            ), HttpStatusCode.ServiceUnavailable)
        call.respondJson(withContext(Dispatchers.IO) { service.settings })
    }
    put("/admin/settings/general") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@put
        }
        val service = context.generalSettingsService
            ?: return@put call.respondJson(
            ApiError(
                "unavailable",
                "General settings are not configured"
            ), HttpStatusCode.ServiceUnavailable)
        val command = try {
            jsonMapper.readValue(call.receiveText(), GeneralSettingsCommand::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected a JSON general settings object"), HttpStatusCode.BadRequest)
            return@put
        }
        try {
            validateGeneralSettings(command, service)
            withContext(Dispatchers.IO) { service.save(command) }
            call.respond(HttpStatusCode.NoContent)
        } catch (failure: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_request", failure.message ?: "Invalid general settings"), HttpStatusCode.BadRequest)
        }
    }
    get("/admin/settings/transcoding") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@get
        }
        val service = context.transcodingService
            ?: return@get call.respondJson(
            ApiError(
                "unavailable",
                "Transcoding settings are not configured"
            ), HttpStatusCode.ServiceUnavailable)
        val settings = context.settingsService
            ?: return@get call.respondJson(ApiError("unavailable", "Settings are not configured"), HttpStatusCode.ServiceUnavailable)
        call.respondJson(withContext(Dispatchers.IO) {
                TranscodingSettingsResponse(
                    settings.brand,
                    SettingsService.getTranscodeDirectory().toString(),
                    settings.downsamplingCommand.orEmpty(), settings.hlsCommand.orEmpty(),
                    settings.jukeboxCommand.orEmpty(), settings.videoImageCommand.orEmpty(),
                    settings.subtitlesExtractionCommand.orEmpty(), settings.transcodeEstimateTimePadding,
                    settings.transcodeEstimateBytePadding,
                    service.allTranscodings.sortedBy { it.id }.map { it.toResponse() },
                )
        })
    }
    put("/admin/settings/transcoding") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@put
        }
        val service = context.transcodingService
            ?: return@put call.respondJson(
            ApiError(
                "unavailable",
                "Transcoding settings are not configured"
            ), HttpStatusCode.ServiceUnavailable)
        val settings = context.settingsService
            ?: return@put call.respondJson(ApiError("unavailable", "Settings are not configured"), HttpStatusCode.ServiceUnavailable)
        val request = try {
            jsonMapper.readValue(call.receiveText(), UpdateTranscodingSettingsRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected a JSON transcoding settings object"), HttpStatusCode.BadRequest)
            return@put
        }
        try {
            saveTranscodingSettings(service, settings, request)
            call.respond(HttpStatusCode.NoContent)
        } catch (failure: IllegalArgumentException) {
            call.respondJson(
                ApiError(
                    "invalid_request",
                    failure.message ?: "Invalid transcoding settings"
                ), HttpStatusCode.BadRequest)
        }
    }
    get("/admin/settings/advanced") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@get
        }
        val settings = context.settingsService
            ?: return@get call.respondJson(ApiError("unavailable", "Settings are not configured"), HttpStatusCode.ServiceUnavailable)
        call.respondJson(AdvancedSettingsResponse(
                settings.downloadBitrateLimit, settings.uploadBitrateLimit,
                settings.ldapUrl.orEmpty(), settings.ldapSearchFilter.orEmpty(), settings.ldapManagerDn.orEmpty(),
                settings.smtpServer.orEmpty(), settings.smtpEncryption.orEmpty(), settings.smtpPort.orEmpty(),
                settings.smtpUser.orEmpty(), settings.smtpFrom.orEmpty(), settings.recaptchaSiteKey.orEmpty(),
                settings.brand, settings.isLdapEnabled, settings.isLdapAutoShadowing, settings.isCaptchaEnabled,
        ))
    }
    put("/admin/settings/advanced") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@put
        }
        val settings = context.settingsService
            ?: return@put call.respondJson(ApiError("unavailable", "Settings are not configured"), HttpStatusCode.ServiceUnavailable)
        val request = try {
            jsonMapper.readValue(call.receiveText(), UpdateAdvancedSettingsRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected a JSON advanced settings object"), HttpStatusCode.BadRequest)
            return@put
        }
        try {
            updateAdvancedSettings(settings, request)
            call.respond(HttpStatusCode.NoContent)
        } catch (failure: IllegalArgumentException) {
            call.respondJson(
                ApiError(
                    "invalid_request",
                    failure.message ?: "Invalid advanced settings"
                ), HttpStatusCode.BadRequest)
        }
    }
    get("/admin/settings/dlna") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@get
        }
        val settings = context.settingsService
            ?: return@get call.respondJson(ApiError("unavailable", "Settings are not configured"), HttpStatusCode.ServiceUnavailable)
        call.respondJson(DlnaSettingsResponse(settings.isDlnaEnabled, settings.dlnaServerName, settings.dlnaBaseLANURL.orEmpty()))
    }
    put("/admin/settings/dlna") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@put
        }
        val settings = context.settingsService
            ?: return@put call.respondJson(ApiError("unavailable", "Settings are not configured"), HttpStatusCode.ServiceUnavailable)
        val service = context.upnpService
            ?: return@put call.respondJson(ApiError("unavailable", "DLNA service is not configured"), HttpStatusCode.ServiceUnavailable)
        val request = try {
            jsonMapper.readValue(call.receiveText(), UpdateDlnaSettingsRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected a JSON DLNA settings object"), HttpStatusCode.BadRequest)
            return@put
        }
        service.setMediaServerEnabled(false)
        settings.isDlnaEnabled = request.enabled
        settings.dlnaServerName = request.serverName.trim().ifEmpty { "Kordinator" }
        settings.dlnaBaseLANURL = request.baseLanUrl.trim().ifEmpty { null }
        settings.save()
        service.setMediaServerEnabled(request.enabled)
        call.respond(HttpStatusCode.NoContent)
    }
    get("/admin/settings/music-folders") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@get
        }
        val service = context.musicFolderAdministrationService
            ?: return@get call.respondJson(
            ApiError(
                "unavailable",
                "Library settings are not configured"
            ), HttpStatusCode.ServiceUnavailable)
        call.respondJson(service.settings.toMusicFolderSettingsResponse())
    }
    put("/admin/settings/music-folders") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@put
        }
        val service = context.musicFolderAdministrationService
            ?: return@put call.respondJson(
            ApiError(
                "unavailable",
                "Library settings are not configured"
            ), HttpStatusCode.ServiceUnavailable)
        val request = try {
            jsonMapper.readValue(call.receiveText(), UpdateMusicFolderSettingsRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(
                ApiError(
                    "invalid_request",
                    "Expected a JSON music folder settings object"
                ), HttpStatusCode.BadRequest)
            return@put
        }
        try {
            service.save(request.toCommand(service.settings))
            call.respond(HttpStatusCode.NoContent)
        } catch (failure: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_request", failure.message ?: "Invalid library settings"), HttpStatusCode.BadRequest)
        }
    }
    post("/admin/settings/music-folders/expunge") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@post
        }
        val service = context.musicFolderAdministrationService
            ?: return@post call.respondJson(
            ApiError(
                "unavailable",
                "Library settings are not configured"
            ), HttpStatusCode.ServiceUnavailable)
        service.expunge()
        call.respond(HttpStatusCode.NoContent)
    }
}
