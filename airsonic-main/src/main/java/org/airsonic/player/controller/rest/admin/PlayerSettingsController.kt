package org.airsonic.player.controller.rest.admin

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.PlayerTechnology
import org.airsonic.player.domain.TranscodeScheme
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.PlayQueueService
import org.airsonic.player.service.TranscodingService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.*
import kotlin.coroutines.cancellation.CancellationException

/** Website extension; standard Subsonic player/queue contracts remain independent. */
class PlayerSettingsController(
    private val players: PlayerService, private val transcoding: TranscodingService,
    private val queues: PlayQueueService, private val writer: JAXBWriter
) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        with(root) {
            get(READ_PATH) { respond(call, request(call), "read") }
            post(SAVE_PATH) { respond(call, request(call), "save") }
            post(CLONE_PATH) { respond(call, request(call), "clone") }
            post(DELETE_PATH) { respond(call, request(call), "delete") }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: UsersRequest, action: String) {
        val response = try {
            if (!request.user.isAdminRole && !request.user.isSettingsRole) throw AccessDenied()
            val p = request.parameters
            val id = p["id"]?.let { it.toIntOrNull()?.takeIf { value -> value > 0 }
                ?: throw IllegalArgumentException("Invalid id") }
            var selected = if (id != null) players.getPlayerById(id)
                ?: throw MissingPlayer() else null
            selected?.let { if (!request.user.isAdminRole && it.username != request.user.username) throw AccessDenied() }
            if (action != "read") {
                val player = selected ?: throw IllegalArgumentException("Missing id")
                when (action) {
                    "save" -> save(player, p)
                    "clone" -> selected = players.clonePlayer(player.id)
                    "delete" -> { players.removePlayerById(player.id); selected = null }
                }
            }
            val visible = players.allPlayers.filter { request.user.isAdminRole || it.username == request.user.username }
            val current = selected ?: visible.firstOrNull()
            writer.createResponse(true).apply {
                playerSettings = PlayerSettings().apply {
                    admin = request.user.isAdminRole
                    transcodingSupported = this@PlayerSettingsController.transcoding.isDownsamplingSupported(null)
                    player = visible.map { entry(it) }.toMutableList()
                    selectedPlayer = current?.let { entry(it).apply {
                        activeTranscodingId = this@PlayerSettingsController.transcoding.getTranscodingsForPlayer(it).map { profile -> profile.id }.toMutableList()
                    } }
                    this.transcoding = this@PlayerSettingsController.transcoding.allTranscodings.map { profile -> PlayerTranscodingOption().apply {
                        this.id = profile.id; name = profile.name
                    } }.toMutableList()
                    transcodeScheme = TranscodeScheme.entries.map { scheme -> PlayerTranscodeOption().apply {
                        name = scheme.name; description = scheme.toString()
                    } }.toMutableList()
                }
            }
        } catch (e: CancellationException) { throw e
        } catch (_: AccessDenied) { writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Player settings access denied")
        } catch (_: MissingPlayer) { writer.createErrorResponse(ErrorCode.NOT_FOUND, "Player not found")
        } catch (e: IllegalArgumentException) { writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Invalid settings")
        } catch (e: Exception) {
            LOG.warn("Player settings failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Player settings failed")
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], response)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun entry(value: Player) = PlayerSettingsEntry().apply {
        id = value.id; name = value.name.orEmpty(); description = value.description
        type = value.type.orEmpty(); lastSeen = value.lastSeen?.toString().orEmpty()
        dynamicIp = value.dynamicIp; autoControlEnabled = value.autoControlEnabled; m3uBomEnabled = value.m3uBomEnabled
        technologyName = value.technology.name; transcodeSchemeName = value.transcodeScheme.name
    }

    private fun save(player: Player, p: Parameters) {
        fun text(key: String) = p[key] ?: throw IllegalArgumentException("Missing $key")
        fun bool(key: String) = when (text(key)) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid boolean: $key")
        }
        // Validate everything before changing the player, queue or active transcoding profiles.
        val name = text("name").trim().ifEmpty { null }
        val dynamic = bool("dynamicIp")
        val auto = bool("autoControlEnabled")
        val bom = bool("m3uBomEnabled")
        val technology = PlayerTechnology.valueOf(text("technologyName"))
        val scheme = TranscodeScheme.valueOf(text("transcodeSchemeName"))
        // An explicit empty parameter means disable all profiles; omission is an incomplete request.
        val profiles = (p.getAll("activeTranscodingId") ?: throw IllegalArgumentException("Missing activeTranscodingId"))
            .filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: throw IllegalArgumentException("Invalid transcoding id") }.distinct()
        val available = transcoding.allTranscodings.map { it.id }.toSet()
        require(profiles.all { it in available }) { "Unknown transcoding id" }
        if (player.technology != technology || player.transcodeScheme != scheme) queues.stop(player)
        player.name = name; player.dynamicIp = dynamic; player.autoControlEnabled = auto; player.m3uBomEnabled = bom
        player.technology = technology; player.transcodeScheme = scheme
        players.updatePlayer(player)
        transcoding.setTranscodingsForPlayer(player, profiles.toIntArray())
    }

    private class AccessDenied : RuntimeException()
    private class MissingPlayer : RuntimeException()
    companion object {
        private val LOG = LoggerFactory.getLogger(PlayerSettingsController::class.java)
        const val READ_PATH = "/rest/getPlayerSettings"
        const val SAVE_PATH = "/rest/savePlayerSettings"
        const val CLONE_PATH = "/rest/clonePlayer"
        const val DELETE_PATH = "/rest/deletePlayer"
        val PATHS = setOf(READ_PATH, SAVE_PATH, CLONE_PATH, DELETE_PATH)
    }
}
