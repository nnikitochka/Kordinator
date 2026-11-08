package org.airsonic.player.controller.rest.admin

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.service.SettingsService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.AdvancedSettings
import kotlin.coroutines.cancellation.CancellationException

/** Admin extension. Save requires all non-secret settings and explicit true/false booleans.
 * Missing or empty credentials preserve their stored values. Read never returns credentials.
 */
class AdvancedSettingsController(private val settings: SettingsService, private val writer: JAXBWriter) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        with(root) {
            get(READ_PATH) { respond(call, request(call), false) }
            post(SAVE_PATH) { respond(call, request(call), true) }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: UsersRequest, save: Boolean) {
        val response = try {
            if (!request.user.isAdminRole) writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Administrator access required")
            else {
                if (save) save(request.parameters)
                writer.createResponse(true).apply { if (!save) advancedSettings = read() }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: IllegalArgumentException) {
            writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Invalid settings")
        } catch (e: Exception) {
            LOG.warn("Advanced settings administration failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Advanced settings administration failed")
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], response)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun read() = AdvancedSettings().apply {
        downloadLimit = settings.downloadBitrateLimit.toString()
        uploadLimit = settings.uploadBitrateLimit.toString()
        ldapUrl = settings.ldapUrl.orEmpty()
        ldapSearchFilter = settings.ldapSearchFilter.orEmpty()
        ldapManagerDn = settings.ldapManagerDn.orEmpty()
        smtpServer = settings.smtpServer.orEmpty()
        smtpEncryption = settings.smtpEncryption.orEmpty()
        smtpPort = settings.smtpPort.orEmpty()
        smtpUser = settings.smtpUser.orEmpty()
        smtpFrom = settings.smtpFrom.orEmpty()
        recaptchaSiteKey = settings.recaptchaSiteKey.orEmpty()
        brand = settings.brand
        ldapEnabled = settings.isLdapEnabled
        ldapAutoShadowing = settings.isLdapAutoShadowing
        captchaEnabled = settings.isCaptchaEnabled
    }

    private fun save(p: Parameters) {
        fun text(key: String) = p[key] ?: throw IllegalArgumentException("Missing $key")
        fun bool(key: String) = when (text(key)) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid boolean: $key")
        }
        fun limit(key: String): String = text(key).trim().also {
            require(it.toLongOrNull()?.let { value -> value >= 0 } == true) { "Invalid non-negative integer: $key" }
        }
        // Validate the complete request before changing settings or rate limiters.
        val value = AdvancedSettings().apply {
            downloadLimit = limit("downloadLimit"); uploadLimit = limit("uploadLimit")
            ldapUrl = text("ldapUrl")
            ldapSearchFilter = text("ldapSearchFilter")
            ldapManagerDn = text("ldapManagerDn")
            smtpServer = text("smtpServer")
            smtpEncryption = text("smtpEncryption")
            smtpPort = text("smtpPort")
            smtpUser = text("smtpUser")
            smtpFrom = text("smtpFrom")
            recaptchaSiteKey = text("recaptchaSiteKey")
            ldapEnabled = bool("ldapEnabled")
            ldapAutoShadowing = bool("ldapAutoShadowing")
            captchaEnabled = bool("captchaEnabled")
        }
        require(value.smtpPort.toIntOrNull()?.let { it in 1..65535 } == true) { "Invalid SMTP port (1..65535)" }
        require(value.smtpEncryption in setOf("None", "STARTTLS", "SSL/TLS")) { "Invalid SMTP encryption" }
        settings.downloadBitrateLimit = value.downloadLimit.toLong()
        settings.uploadBitrateLimit = value.uploadLimit.toLong()
        settings.ldapUrl = value.ldapUrl
        settings.ldapSearchFilter = value.ldapSearchFilter
        settings.ldapManagerDn = value.ldapManagerDn
        settings.smtpServer = value.smtpServer
        settings.smtpEncryption = value.smtpEncryption
        settings.smtpPort = value.smtpPort
        settings.smtpUser = value.smtpUser
        settings.smtpFrom = value.smtpFrom
        settings.recaptchaSiteKey = value.recaptchaSiteKey
        settings.isLdapEnabled = value.ldapEnabled
        settings.isLdapAutoShadowing = value.ldapAutoShadowing
        settings.isCaptchaEnabled = value.captchaEnabled
        p["ldapManagerPassword"]?.takeIf { it.isNotEmpty() }?.let { settings.ldapManagerPassword = it }
        p["smtpPassword"]?.takeIf { it.isNotEmpty() }?.let { settings.smtpPassword = it }
        p["recaptchaSecretKey"]?.takeIf { it.isNotEmpty() }?.let { settings.recaptchaSecretKey = it }
        settings.save()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(AdvancedSettingsController::class.java)
        const val READ_PATH = "/rest/getAdvancedSettings"
        const val SAVE_PATH = "/rest/saveAdvancedSettings"
    }
}
