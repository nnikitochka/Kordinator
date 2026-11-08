package org.airsonic.player.controller.rest.admin

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.command.MusicFolderSettingsCommand
import org.airsonic.player.command.MusicFolderSettingsCommand.MusicFolderInfo
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.service.MusicFolderAdministrationService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.AdminMusicFolder
import org.subsonic.restapi.MusicFolderSettings
import kotlin.coroutines.cancellation.CancellationException

/**
 * Admin extensions; getMusicFolders keeps its standard Subsonic semantics.
 * Save accepts the scan settings plus repeated folderId and folder.<id>.{name,path,type,enabled,delete}.
 * Omitted folder IDs remain unchanged. newMusicFolder.{name,path,type,enabled} optionally adds a folder.
 * Booleans use true/false; write routes require POST and the normal browser CSRF token.
 */
class MusicFolderSettingsController(private val service: MusicFolderAdministrationService, private val writer: JAXBWriter) {
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
                    "saveMusicFolderSettings" -> service.save(parse(request.parameters))
                    "expungeMusicFolders" -> service.expunge()
                }
                writer.createResponse(true).apply { if (operation == "read") musicFolderSettings = read() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Invalid settings")
        } catch (e: Exception) {
            LOG.warn("Library administration failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Library administration failed")
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], response)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun read(): MusicFolderSettings {
        val command = service.settings
        return MusicFolderSettings().apply {
            interval = command.interval.toInt(); hour = command.hour.toInt(); scanning = command.isScanning
            fastCache = command.isFastCache; organizeByFolderStructure = command.isOrganizeByFolderStructure
            uploadsFolder = command.uploadsFolder.orEmpty(); excludePatternString = command.excludePatternString.orEmpty()
            ignoreSymLinks = command.ignoreSymLinks; fullScan = command.fullScan
            clearFullScanSettingAfterScan = command.clearFullScanSettingAfterScan
            types.type.addAll(MusicFolder.Type.values().map { it.name })
            folders.folder.addAll(command.musicFolders.map(::mapFolder))
            deletedFolders.folder.addAll(command.deletedMusicFolders.map(::mapFolder))
        }
    }

    private fun mapFolder(folder: MusicFolderInfo) = AdminMusicFolder().apply {
        id = folder.id; name = folder.name; path = folder.path; type = folder.type
        enabled = folder.enabled; existing = folder.existing; overlap = folder.overlap; overlapStatus = folder.overlapStatus.orEmpty()
    }

    private fun parse(p: Parameters): MusicFolderSettingsCommand {
        // Validate the entire request before changing folders or persisted settings.
        val command = service.settings
        val interval = p["interval"]?.toIntOrNull() ?: throw IllegalArgumentException("Invalid scan interval")
        val hour = p["hour"]?.toIntOrNull() ?: throw IllegalArgumentException("Invalid scan hour")
        require(interval == -1 || interval > 0) { "Invalid scan interval" }
        require(hour in 0..23) { "Invalid scan hour" }
        command.interval = interval.toString(); command.hour = hour.toString()
        fun bool(name: String): Boolean = when (p[name]) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid boolean: $name")
        }
        command.isFastCache = bool("fastCache")
        command.isOrganizeByFolderStructure = p["organizeByFolderStructure"]?.let { bool("organizeByFolderStructure") } ?: command.isOrganizeByFolderStructure
        command.ignoreSymLinks = bool("ignoreSymLinks"); command.fullScan = bool("fullScan")
        command.clearFullScanSettingAfterScan = bool("clearFullScanSettingAfterScan") && command.fullScan
        command.uploadsFolder = p["uploadsFolder"] ?: throw IllegalArgumentException("Missing uploadsFolder")
        command.excludePatternString = p["excludePatternString"] ?: throw IllegalArgumentException("Missing excludePatternString")
        if (command.excludePatternString.isNotBlank()) java.util.regex.Pattern.compile(command.excludePatternString)
        val ids = p.getAll("folderId").orEmpty().map { it.toIntOrNull() ?: throw IllegalArgumentException("Invalid folder id") }
        require(ids.distinct().size == ids.size) { "Duplicate folder id" }
        val available = command.musicFolders.map { it.id }.toSet()
        require(available.containsAll(ids)) { "Unknown folder id" }
        command.musicFolders = ids.map { id ->
            val prefix = "folder.$id."
            MusicFolderInfo().apply {
                this.id = id; delete = p[prefix + "delete"]?.let { bool(prefix + "delete") } ?: false
                if (!delete) fillFolder(this, p, prefix, bool(prefix + "enabled"))
            }
        }
        command.newMusicFolder = MusicFolderInfo()
        if (!p["newMusicFolder.path"].isNullOrBlank()) {
            fillFolder(command.newMusicFolder, p, "newMusicFolder.", bool("newMusicFolder.enabled"))
        }
        return command
    }

    private fun fillFolder(folder: MusicFolderInfo, p: Parameters, prefix: String, enabled: Boolean) {
        folder.path = p[prefix + "path"]?.trim()?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("Missing folder path")
        folder.name = p[prefix + "name"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: (java.nio.file.Paths.get(folder.path).fileName?.toString() ?: folder.path)
        folder.type = p[prefix + "type"] ?: throw IllegalArgumentException("Missing folder type")
        require(MusicFolder.Type.values().any { it.name == folder.type }) { "Invalid folder type" }
        folder.enabled = enabled
        folder.toMusicFolder() // Validate paths before mutations.
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MusicFolderSettingsController::class.java)
        const val READ_PATH = "/rest/getMusicFolderSettings"
        @JvmField val WRITE_PATHS = arrayOf("/rest/saveMusicFolderSettings", "/rest/expungeMusicFolders")
    }
}
