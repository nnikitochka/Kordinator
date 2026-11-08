package org.airsonic.player.controller.rest.users

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.command.UserSettingsCommand
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.service.UserAdministrationService
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.domain.TranscodeScheme
import org.airsonic.player.domain.User
import org.airsonic.player.util.StringUtil.decodeSubsonicPassword
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.TranscodingService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Response
import org.subsonic.restapi.Users
import org.subsonic.restapi.WebsiteUserAdministration
import org.subsonic.restapi.WebsiteUserDetails
import org.subsonic.restapi.WebsiteUserFolder
import org.subsonic.restapi.WebsiteUserScheme
import kotlin.coroutines.cancellation.CancellationException

class UsersController(
    private val security: SecurityService,
    private val settings: SettingsService,
    private val folders: MediaFolderService,
    private val userSettings: UserAdministrationService,
    private val transcoding: TranscodingService,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        with(root) {
            PATHS.forEach { path ->
                val operation = path.substringAfterLast('/').removeSuffix(".view")
                get(path) { respond(call, request(call), operation) }
                post(path) { respond(call, request(call), operation) }
            }
            WEBSITE_PATHS.forEach { path ->
                post(path) { respond(call, request(call), "saveWebsiteUser") }
            }
            get(WEBSITE_READ_PATH) { respond(call, request(call), "getWebsiteUserAdministration") }
        }
    }
    private suspend fun respond(call: ApplicationCall, request: UsersRequest, operation: String) {
        val result = try {
            execute(request, operation)
        } catch (e: MissingParameter) {
            writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Required param (${e.parameter}) is missing")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in users API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }
    private fun execute(request: UsersRequest, operation: String): Response {
        val p = request.parameters
        val current = request.user
        if (operation == "getUser") {
            val username = p.required("username")
            if (username != current.username && !current.isAdminRole) return denied("${current.username} is not authorized to get details for other users.")
            val user = security.getUserByName(username) ?: return notFound(username)
            return writer.createResponse(true).apply { this.user = mapUser(user) }
        }
        if (!current.isAdminRole) {
            val action = when (operation) {
                "getUsers" -> "get details for other users"
                "createUser" -> "create new users"
                "updateUser" -> "update users"
                else -> "delete users"
            }
            return denied("${current.username} is not authorized to $action.")
        }
        if (operation == "saveWebsiteUser") return saveWebsiteUser(request)
        if (operation == "getWebsiteUserAdministration") return getWebsiteUserAdministration(request)
        when (operation) {
            "getUsers" -> return writer.createResponse(true).apply {
                users = Users().apply { security.allUsers.forEach { user.add(mapUser(it)) } }
            }
            "createUser" -> {
                val command = UserSettingsCommand()
                command.username = p.required("username")
                command.password = decodeSubsonicPassword(p.required("password"))
                command.email = p.required("email")
                fillRoles(command, p, null)
                command.transcodeSchemeName = TranscodeScheme.OFF.name
                command.allowedMusicFolderIds = folderIds(p).let { if (it.isEmpty()) folders.allMusicFolders.map { folder -> folder.id }.toIntArray() else it }
                userSettings.createUser(command)
            }
            "updateUser" -> {
                val username = p.required("username")
                val user = security.getUserByName(username)
                val userSettingsValue = settings.getUserSettings(username)
                if (user == null) return notFound(username)
                if (username == User.USERNAME_ADMIN) return denied("Not allowed to change admin user")
                val command = UserSettingsCommand()
                command.username = username
                command.email = p["email"] ?: user.email
                fillRoles(command, p, user)
                val rate = p["maxBitRate"]?.toIntOrNull() ?: userSettingsValue.transcodeScheme.maxBitRate
                command.transcodeSchemeName = TranscodeScheme.fromMaxBitRate(rate)?.name
                if (p["password"] != null) {
                    command.password = decodeSubsonicPassword(p.required("password"))
                    command.isPasswordChange = true
                }
                command.allowedMusicFolderIds = folderIds(p).let { if (it.isEmpty()) folders.getMusicFoldersForUser(username).map { folder -> folder.id }.toIntArray() else it }
                userSettings.updateUser(command)
            }
            "deleteUser" -> {
                val username = p.required("username")
                if (username == User.USERNAME_ADMIN) return denied("Not allowed to delete admin user")
                security.deleteUser(username)
            }
        }
        return writer.createResponse(true)
    }
    private fun getWebsiteUserAdministration(request: UsersRequest): Response {
        val allUsers = security.allUsers
        val username = request.parameters["username"]
        val index = request.parameters["userIndex"]?.toIntOrNull()
        val selected = if (username != null) security.getUserByName(username) ?: return notFound(username)
            else index?.let { allUsers.getOrNull(it) }
        val allowed = selected?.let { folders.getMusicFoldersForUser(it.username).map { folder -> folder.id }.toSet() }.orEmpty()
        val data = WebsiteUserAdministration().apply {
            ldapEnabled = settings.isLdapEnabled
            transcodingSupported = transcoding.isDownsamplingSupported(null)
            users.user.addAll(allUsers.map { it.username })
            folders.folder.addAll(this@UsersController.folders.allMusicFolders.map { folder ->
                WebsiteUserFolder().apply {
                    id = folder.id; name = folder.name; path = folder.path.toString(); this.allowed = folder.id in allowed
                }
            })
            schemes.scheme.addAll(TranscodeScheme.values().map { scheme ->
                WebsiteUserScheme().apply { name = scheme.name; description = scheme.toString() }
            })
            if (selected != null) this.user = WebsiteUserDetails().apply {
                this.username = selected.username; email = selected.email ?: ""; newUser = false
                currentUser = selected.username == request.user.username
                ldapAuthenticated = selected.isLdapAuthenticated
                adminRole = selected.isAdminRole; settingsRole = selected.isSettingsRole; streamRole = selected.isStreamRole
                jukeboxRole = selected.isJukeboxRole; downloadRole = selected.isDownloadRole; uploadRole = selected.isUploadRole
                shareRole = selected.isShareRole; coverArtRole = selected.isCoverArtRole; commentRole = selected.isCommentRole
                transcodeSchemeName = settings.getUserSettings(selected.username).transcodeScheme.name
            }
        }
        return writer.createResponse(true).apply { websiteUserAdministration = data }
    }
    private fun saveWebsiteUser(request: UsersRequest): Response {
        val p = request.parameters
        val username = p.required("username").trim()
        if (username.isEmpty()) return invalid("Username is required")
        val existing = security.getUserByName(username)
        val creating = p.bool("newUser", false)
        if (creating && existing != null) return invalid("User already exists")
        if (!creating && existing == null) return notFound(username)
        val deleting = p.bool("deleteUser", false)
        if (deleting) {
            if (username == User.USERNAME_ADMIN || username == request.user.username) return denied("Cannot delete this user")
            security.deleteUser(username)
            return writer.createResponse(true)
        }
        val ldap = p.bool("ldapAuthenticated", false)
        val changePassword = p.bool("passwordChange", false)
        val password = p["password"]?.trim().orEmpty()
        if (creating && p["email"].isNullOrBlank()) return invalid("Email is required")
        if (ldap && creating && !settings.isLdapEnabled) return invalid("LDAP is disabled")
        if (ldap && (changePassword || creating && password.isNotEmpty())) return invalid("Password is not supported for LDAP users")
        if (!ldap && (creating || changePassword) && (password.isEmpty() || password != p["confirmPassword"])) return invalid("Passwords must match and cannot be empty")
        val command = UserSettingsCommand()
        command.username = username
        command.email = p["email"]
        command.password = password
        command.isPasswordChange = changePassword
        fillRoles(command, p, null)
        if (username == request.user.username && !command.isAdminRole) return denied("Cannot remove admin from the current user")
        command.transcodeSchemeName = p["transcodeSchemeName"]?.takeIf { name -> TranscodeScheme.values().any { it.name == name } }
            ?: return invalid("Invalid transcoding scheme")
        val rawFolders = p.getAll("allowedMusicFolderIds").orEmpty()
        val ids = rawFolders.map { it.toIntOrNull() ?: return invalid("Invalid music folder") }
        val available = folders.allMusicFolders.map { it.id }.toSet()
        if (!available.containsAll(ids)) return invalid("Invalid music folder")
        command.allowedMusicFolderIds = ids.toIntArray()
        if (creating) userSettings.createUser(command) else userSettings.updateUser(command)
        return writer.createResponse(true)
    }
    private fun invalid(message: String) = writer.createErrorResponse(ErrorCode.GENERIC, message)
    private fun fillRoles(command: UserSettingsCommand, p: Parameters, user: User?) {
        command.isLdapAuthenticated = p.bool("ldapAuthenticated", user?.isLdapAuthenticated ?: false)
        command.isAdminRole = p.bool("adminRole", user?.isAdminRole ?: false)
        command.isCommentRole = p.bool("commentRole", user?.isCommentRole ?: false)
        command.isCoverArtRole = p.bool("coverArtRole", user?.isCoverArtRole ?: false)
        command.isDownloadRole = p.bool("downloadRole", user?.isDownloadRole ?: false)
        command.isStreamRole = p.bool("streamRole", user?.isDownloadRole ?: true)
        command.isUploadRole = p.bool("uploadRole", user?.isUploadRole ?: false)
        command.isJukeboxRole = p.bool("jukeboxRole", user?.isJukeboxRole ?: false)
        command.isSettingsRole = p.bool("settingsRole", user?.isSettingsRole ?: true)
        command.isShareRole = p.bool("shareRole", user?.isShareRole ?: false)
    }
    private fun mapUser(user: User): org.subsonic.restapi.User {
        val s = settings.getUserSettings(user.username)
        return org.subsonic.restapi.User().apply {
            username = user.username; email = user.email; scrobblingEnabled = s.lastFmEnabled
            adminRole = user.isAdminRole; settingsRole = user.isSettingsRole; downloadRole = user.isDownloadRole
            uploadRole = user.isUploadRole; playlistRole = true; coverArtRole = user.isCoverArtRole
            commentRole = user.isCommentRole; podcastRole = false; streamRole = user.isStreamRole
            jukeboxRole = user.isJukeboxRole; shareRole = user.isShareRole; videoConversionRole = false
            s.transcodeScheme?.takeIf { it != TranscodeScheme.OFF }?.let { maxBitRate = it.maxBitRate }
            folders.getMusicFoldersForUser(user.username).forEach { folder.add(it.id) }
        }
    }
    private fun folderIds(p: Parameters): IntArray = p.getAll("musicFolderId")?.map { it.toIntOrNull() ?: return intArrayOf() }?.toIntArray() ?: intArrayOf()
    private fun Parameters.bool(name: String, default: Boolean): Boolean = get(name)?.let { it.equals("true", true) || it.equals("on", true) || it.equals("yes", true) || it == "1" } ?: default
    private fun Parameters.required(name: String): String = get(name) ?: throw MissingParameter(name)
    private fun denied(message: String) = writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, message)
    private fun notFound(username: String) = writer.createErrorResponse(ErrorCode.NOT_FOUND, "No such user: $username")
    private class MissingParameter(val parameter: String) : RuntimeException()
    companion object {
        private val LOG = LoggerFactory.getLogger(UsersController::class.java)
        @JvmField val PATHS = listOf("getUser", "getUsers", "createUser", "updateUser", "deleteUser").flatMap { listOf("/rest/$it", "/rest/$it.view") }.toTypedArray()
        @JvmField val WEBSITE_PATHS = arrayOf("/rest/saveWebsiteUser")
        const val WEBSITE_READ_PATH = "/rest/getWebsiteUserAdministration"
    }
}
