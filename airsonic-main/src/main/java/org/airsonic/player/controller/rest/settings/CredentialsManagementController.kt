package org.airsonic.player.controller.rest.settings

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.domain.User
import org.airsonic.player.domain.UserCredential
import org.airsonic.player.domain.UserCredential.App
import org.airsonic.player.security.GlobalSecurityConfig as Encoders
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.util.Util
import org.slf4j.LoggerFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.subsonic.restapi.*
import java.time.Instant
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.cancellation.CancellationException

/** Website extension. Reads never expose stored credentials. IDs identify a credential snapshot;
 * stale IDs must be refreshed. Write routes are POST-only and retain normal CSRF protection.
 * Expiration is an ISO-8601 instant; empty means no expiration. Account identity comes from auth.
 */
class CredentialsManagementController(
    private val security: SecurityService, private val settings: SettingsService, private val writer: JAXBWriter,
    transactionManager: PlatformTransactionManager
) {
    private val transaction = TransactionTemplate(transactionManager)
    private val mutationLock = Any()

    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        with(root) {
            get(READ_PATH) { respond(call, request(call), "read") }
            get(KEYS_PATH) { respond(call, request(call), "keys") }
            post(CREATE_PATH) { respond(call, request(call), "create") }
            post(UPDATE_PATH) { respond(call, request(call), "update") }
            post(ADMIN_PATH) { respond(call, request(call), "admin") }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: UsersRequest, operation: String) {
        call.response.headers.append("Cache-Control", "no-store")
        val result = try {
            val user = request.user
            if (!user.isAdminRole && !user.isSettingsRole) throw Denied()
            if ((operation == "keys" || operation == "admin") && !user.isAdminRole) throw Denied()
            if (operation == "keys") writer.createResponse(true).apply {
                credentialKeys = CredentialKeys().apply {
                    jwtKey = settings.getJWTKey(); encryptionKey = settings.encryptionPassword.orEmpty()
                    encryptionKeySalt = settings.encryptionSalt.orEmpty()
                }
            } else {
                if (operation != "read") synchronized(mutationLock) {
                    when (operation) {
                        "create" -> create(user.username, request.parameters)
                        "update" -> update(user.username, request.parameters)
                        "admin" -> admin(request.parameters)
                    }
                }
                writer.createResponse(true).apply { credentialManagement = read(user) }
            }
        } catch (e: CancellationException) { throw e
        } catch (_: Denied) { writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Credentials access denied")
        } catch (_: MissingCredential) { writer.createErrorResponse(ErrorCode.NOT_FOUND, "Credential changed or no longer exists; reload the list")
        } catch (e: IllegalArgumentException) { writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Invalid credential settings")
        } catch (_: MutationFailed) { writer.createErrorResponse(ErrorCode.GENERIC, "Could not change credentials")
        } catch (_: Exception) {
            // Encoder/DAO exceptions may contain credential values or SQL arguments.
            LOG.warn("Credential management failed")
            writer.createErrorResponse(ErrorCode.GENERIC, "Credential management failed")
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun id(value: UserCredential): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(settings.getJWTKey().toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return HexFormat.of().formatHex(mac.doFinal(Util.toJson(value).toByteArray(Charsets.UTF_8)))
    }

    private fun read(user: User) = CredentialManagement().apply {
        username = user.username; ldapAuthEnabled = user.isLdapAuthenticated
        preferredEncoder = security.getPreferredPasswordEncoder(true)
        preferredDecodableEncoder = security.getPreferredPasswordEncoder(false)
        credential = security.getCredentials(user.username, *App.entries.toTypedArray()).sortedBy { it.created }.map { value ->
            ManagedCredential().apply {
                id = this@CredentialsManagementController.id(value); app = value.app.name; username = value.appUsername; encoder = value.encoder
                comment = value.comment.orEmpty(); created = value.created.toString(); updated = value.updated.toString()
                expires = value.expiration?.toString().orEmpty()
                decodable = value.encoder in Encoders.DECODABLE_ENCODERS
                openText = value.encoder in Encoders.OPENTEXT_ENCODERS
                legacy = value.encoder.startsWith("legacy")
            }
        }.toMutableList()
        app = App.entries.map { value -> CredentialApplication().apply {
            id = value.name; name = value.getName(); usernameRequired = value.usernameRequired
            nonDecodableEncodersAllowed = value.nonDecodableEncodersAllowed
        } }.toMutableList()
        encoder = Encoders.NONLEGACY_ENCODERS.sorted().map { value -> CredentialEncoder().apply {
            id = value; name = if (value == "noop") "plaintext" else value
            decodable = value in Encoders.DECODABLE_ENCODERS
        } }.toMutableList()
        if (user.isAdminRole) adminControls = CredentialAdminControls().apply {
            legacyCredsPresent = security.checkLegacyCredsPresent(); openCredsPresent = security.checkOpenCredsPresent()
            defaultAdminCredsPresent = security.checkDefaultAdminCredsPresent()
            nonDecodableEncoder = settings.nonDecodablePasswordEncoder
            decodableEncoder = settings.decodablePasswordEncoder; preferNonDecodable = settings.preferNonDecodablePasswords
        }
    }

    private fun text(p: Parameters, key: String) = p[key] ?: throw IllegalArgumentException("Missing $key")
    private fun bool(p: Parameters, key: String) = when (text(p, key)) {
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException("Invalid boolean: $key")
    }
    private fun expiration(value: String): Instant? {
        if (value.isEmpty()) return null
        return try { Instant.parse(value) } catch (_: java.time.DateTimeException) { throw IllegalArgumentException("Invalid expiration") }
    }
    private fun encoder(value: String, app: App) {
        require(value in Encoders.NONLEGACY_ENCODERS) { "Invalid encoder" }
        require(app.nonDecodableEncodersAllowed || value in Encoders.NONLEGACY_DECODABLE_ENCODERS) { "Application requires a decodable encoder" }
    }

    private fun create(username: String, p: Parameters) {
        val app = try { App.valueOf(text(p, "app")) } catch (_: IllegalArgumentException) { throw IllegalArgumentException("Invalid app") }
        val encoder = text(p, "encoder"); encoder(encoder, app)
        val password = text(p, "credential")
        require(password.isNotBlank()) { "Credential must not be blank" }
        require(password == text(p, "confirmCredential")) { "Credentials do not match" }
        val appUsername = if (app.usernameRequired) text(p, "appUsername").also { require(it.isNotBlank()) { "Application username is required" } } else username
        val expires = expiration(p["expires"].orEmpty())
        if (!security.createCredential(UserCredential(username, appUsername, password, encoder, app, "Created by user", expires))) throw MutationFailed()
    }

    private data class Change(val old: UserCredential, val value: UserCredential, val delete: Boolean)
    private fun update(username: String, p: Parameters) {
        val ids = p.getAll("id") ?: throw IllegalArgumentException("Missing id")
        require(ids.isNotEmpty() && ids.distinct().size == ids.size) { "Duplicate or empty credential IDs" }
        transaction.executeWithoutResult {
            val current = security.getCredentials(username, *App.entries.toTypedArray())
            val lookup = current.associateBy { id(it) }
            val changes = ids.map { key ->
                val old = lookup[key] ?: throw MissingCredential()
                val delete = bool(p, "delete[$key]")
                val value = UserCredential(old)
                if (!delete) {
                    value.encoder = text(p, "encoder[$key]")
                    if (value.encoder != old.encoder) {
                        encoder(value.encoder, old.app)
                        require(old.encoder in Encoders.DECODABLE_ENCODERS) { "A non-decodable credential cannot be re-encoded" }
                    }
                    value.expiration = expiration(text(p, "expires[$key]"))
                }
                Change(old, value, delete)
            }
            require(current.count { it.app == App.AIRSONIC } == 0 ||
                current.count { it.app == App.AIRSONIC } > changes.count { it.delete && it.old.app == App.AIRSONIC }) {
                "Cannot delete the last Airsonic credential"
            }
            changes.forEach { change ->
                val success = if (change.delete) security.deleteCredential(change.old)
                    else security.updateCredentials(change.old, change.value, "User updated", false)
                if (!success) throw MutationFailed()
            }
        }
    }

    private fun admin(p: Parameters) {
        when (text(p, "action")) {
            "encoders" -> {
                val nonDecodable = text(p, "nonDecodableEncoder")
                val decodable = text(p, "decodableEncoder")
                val prefer = bool(p, "preferNonDecodable")
                require(nonDecodable in Encoders.NONLEGACY_NONDECODABLE_ENCODERS) { "Invalid non-decodable encoder" }
                require(decodable in Encoders.NONLEGACY_DECODABLE_ENCODERS) { "Invalid decodable encoder" }
                settings.nonDecodablePasswordEncoder = nonDecodable; settings.decodablePasswordEncoder = decodable
                settings.preferNonDecodablePasswords = prefer; settings.save()
            }
            "jwtKey" -> {
                val key = text(p, "jwtKey"); require(key.isNotBlank()) { "JWT key must not be blank" }
                settings.setJWTKey(key); settings.save()
            }
            "encryptionKey" -> {
                val key = text(p, "encryptionKey"); require(key.isNotBlank()) { "Encryption key must not be blank" }
                // Preserve the existing stored-key operation and its restart behavior.
                settings.encryptionPassword = key; settings.save()
            }
            "migrateDefault", "migrateDecodable" -> transaction.executeWithoutResult {
                if (!security.migrateLegacyCredsToNonLegacy(p["action"] == "migrateDecodable")) throw MutationFailed()
            }
            else -> throw IllegalArgumentException("Unknown credential settings action")
        }
    }

    private class Denied : RuntimeException()
    private class MissingCredential : RuntimeException()
    private class MutationFailed : RuntimeException()
    companion object {
        private val LOG = LoggerFactory.getLogger(CredentialsManagementController::class.java)
        const val READ_PATH = "/rest/getCredentials"
        const val KEYS_PATH = "/rest/getCredentialKeys"
        const val CREATE_PATH = "/rest/createCredential"
        const val UPDATE_PATH = "/rest/updateCredentials"
        const val ADMIN_PATH = "/rest/updateCredentialSettings"
        val PATHS = setOf(READ_PATH, KEYS_PATH, CREATE_PATH, UPDATE_PATH, ADMIN_PATH)
    }
}
