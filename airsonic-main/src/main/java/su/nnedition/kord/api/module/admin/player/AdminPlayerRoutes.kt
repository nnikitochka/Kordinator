package su.nnedition.kord.api.module.admin.player

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.airsonic.player.domain.PlayerTechnology
import org.airsonic.player.domain.TranscodeScheme
import org.airsonic.player.security.LocalUserPrincipal
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.module.admin.settings.AdminPlayersResponse
import su.nnedition.kord.api.module.admin.settings.AdminTranscodingOption
import su.nnedition.kord.api.module.admin.settings.UpdatePlayerSettingsRequest
import su.nnedition.kord.api.support.jsonMapper
import su.nnedition.kord.api.support.respondJson
import su.nnedition.kord.api.support.toAdminPlayerResponse

fun Route.adminPlayerRoutes(
    context: ApiContext,
) {
    get("/admin/players") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@get
        }
        val players = context.playerService
            ?: return@get call.respondJson(ApiError("unavailable", "Player settings are not configured"), HttpStatusCode.ServiceUnavailable)
        val transcoding = context.transcodingService
            ?: return@get call.respondJson(ApiError("unavailable", "Transcoding settings are not configured"), HttpStatusCode.ServiceUnavailable)
        call.respondJson(AdminPlayersResponse(
                items = players.getAllPlayers().map { it.toAdminPlayerResponse(transcoding) },
                transcoding = transcoding.allTranscodings.map {
                    AdminTranscodingOption(requireNotNull(it.id), requireNotNull(it.name))
                },
                schemes = TranscodeScheme.entries.map { it.name },
                technologies = PlayerTechnology.entries.map { it.name },
        ))
    }
    put("/admin/players/{id}") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@put
        }
        val players = context.playerService
            ?: return@put call.respondJson(ApiError("unavailable", "Player settings are not configured"), HttpStatusCode.ServiceUnavailable)
        val jukebox = context.jukeboxService
            ?: return@put call.respondJson(ApiError("unavailable", "Player audio control is not configured"), HttpStatusCode.ServiceUnavailable)
        val transcoding = context.transcodingService
            ?: return@put call.respondJson(ApiError("unavailable", "Transcoding settings are not configured"), HttpStatusCode.ServiceUnavailable)
        val id = call.parameters["id"]?.toIntOrNull()?.takeIf { it > 0 }
            ?: return@put call.respondJson(ApiError("invalid_request", "Player ID must be a positive number"), HttpStatusCode.BadRequest)
        val player = players.getPlayerById(id)
            ?: return@put call.respondJson(ApiError("player_not_found", "Player does not exist"), HttpStatusCode.NotFound)
        val request = try {
            jsonMapper.readValue(call.receiveText(), UpdatePlayerSettingsRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected a complete player settings object"), HttpStatusCode.BadRequest)
            return@put
        }
        val technology = try { PlayerTechnology.valueOf(request.technology) } catch (_: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_player", "Unknown player technology"), HttpStatusCode.BadRequest)
            return@put
        }
        val scheme = try { TranscodeScheme.valueOf(request.transcodeScheme) } catch (_: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_player", "Unknown transcoding scheme"), HttpStatusCode.BadRequest)
            return@put
        }
        val availableIds = transcoding.allTranscodings.map { it.id }.toSet()
        if (request.transcodingIds.distinct().size != request.transcodingIds.size || !availableIds.containsAll(request.transcodingIds)) {
            call.respondJson(ApiError("invalid_player", "Transcoding profile IDs must be unique and exist"), HttpStatusCode.BadRequest)
            return@put
        }
        val normalizedName = request.name.trim().ifEmpty { null }
        if (player.technology != technology || player.transcodeScheme != scheme) jukebox.stop(player)
        player.name = normalizedName
        player.dynamicIp = request.dynamicIp
        player.autoControlEnabled = request.autoControlEnabled
        player.m3uBomEnabled = request.m3uBomEnabled
        player.technology = technology
        player.transcodeScheme = scheme
        players.updatePlayer(player)
        transcoding.setTranscodingsForPlayer(player, request.transcodingIds.toIntArray())
        call.respondJson(player.toAdminPlayerResponse(transcoding))
    }
    post("/admin/players/{id}/clone") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@post
        }
        val players = context.playerService
            ?: return@post call.respondJson(ApiError("unavailable", "Player settings are not configured"), HttpStatusCode.ServiceUnavailable)
        val transcoding = context.transcodingService
            ?: return@post call.respondJson(ApiError("unavailable", "Transcoding settings are not configured"), HttpStatusCode.ServiceUnavailable)
        val id = call.parameters["id"]?.toIntOrNull()?.takeIf { it > 0 }
            ?: return@post call.respondJson(ApiError("invalid_request", "Player ID must be a positive number"), HttpStatusCode.BadRequest)
        if (players.getPlayerById(id) == null) {
            call.respondJson(ApiError("player_not_found", "Player does not exist"), HttpStatusCode.NotFound)
            return@post
        }
        call.respondJson(players.clonePlayer(id).toAdminPlayerResponse(transcoding), HttpStatusCode.Created)
    }
    delete("/admin/players/{id}") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@delete
        }
        val players = context.playerService
            ?: return@delete call.respondJson(ApiError("unavailable", "Player settings are not configured"), HttpStatusCode.ServiceUnavailable)
        val id = call.parameters["id"]?.toIntOrNull()?.takeIf { it > 0 }
            ?: return@delete call.respondJson(ApiError("invalid_request", "Player ID must be a positive number"), HttpStatusCode.BadRequest)
        if (players.getPlayerById(id) == null) {
            call.respondJson(ApiError("player_not_found", "Player does not exist"), HttpStatusCode.NotFound)
            return@delete
        }
        players.removePlayerById(id)
        call.respond(HttpStatusCode.NoContent)
    }
}
