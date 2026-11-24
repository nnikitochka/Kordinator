package org.airsonic.player.ktor

import io.ktor.server.routing.Route

import io.ktor.http.ContentType
import io.ktor.server.auth.principal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.request.receiveText
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.service.SettingsService
import org.airsonic.player.domain.TranscodeScheme
import org.airsonic.player.domain.PlayerTechnology
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import java.nio.file.Files


internal class KordinatorAdminPlayerDatabaseRoutes(private val api: ApiContext) {
    fun routes(route: Route) = route.apply {
        with(api) {
        get("/admin/players") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@get
            }
            val players = playerService
                ?: return@get call.respondJson(ApiError("unavailable", "Player settings are not configured"), HttpStatusCode.ServiceUnavailable)
            val transcoding = transcodingService
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
            val players = playerService
                ?: return@put call.respondJson(ApiError("unavailable", "Player settings are not configured"), HttpStatusCode.ServiceUnavailable)
            val jukebox = jukeboxService
                ?: return@put call.respondJson(ApiError("unavailable", "Player audio control is not configured"), HttpStatusCode.ServiceUnavailable)
            val transcoding = transcodingService
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
            val players = playerService
                ?: return@post call.respondJson(ApiError("unavailable", "Player settings are not configured"), HttpStatusCode.ServiceUnavailable)
            val transcoding = transcodingService
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
            val players = playerService
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
        get("/admin/settings/database") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@get
            }
            val settings = settingsService
                ?: return@get call.respondJson(ApiError("unavailable", "Settings are not configured"), HttpStatusCode.ServiceUnavailable)
            val db = databaseService
            val type = when {
                settings.databaseJNDIName != null -> "JNDI"
                settings.databaseUrl == SettingsService.getDefaultJDBCUrl() &&
                    settings.databaseUsername == SettingsService.getDefaultJDBCUsername() &&
                    settings.databasePassword == SettingsService.getDefaultJDBCPassword() -> "BUILTIN"
                else -> "EXTERNAL"
            }
            call.respondJson(DatabaseSettingsResponse(
                type, settings.databaseDriver.orEmpty(), settings.databaseUrl.orEmpty(),
                settings.databaseUsername.orEmpty(), settings.databaseJNDIName.orEmpty(),
                settings.databaseMysqlVarcharMaxlength, settings.dbBackupInterval,
                settings.dbBackupRetentionCount, db?.backuppable() ?: false,
                !settings.databasePassword.isNullOrEmpty(),
            ))
        }
        put("/admin/settings/database") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@put
            }
            val settings = settingsService
                ?: return@put call.respondJson(ApiError("unavailable", "Settings are not configured"), HttpStatusCode.ServiceUnavailable)
            val request = try {
                jsonMapper.readValue(call.receiveText(), UpdateDatabaseSettingsRequest::class.java)
            } catch (_: Exception) {
                call.respondJson(ApiError("invalid_request", "Expected a JSON database settings object"), HttpStatusCode.BadRequest)
                return@put
            }
            try {
                updateDatabaseSettings(settings, request)
                call.respondJson(mapOf("saved" to true, "restartRequired" to true), HttpStatusCode.OK)
            } catch (failure: IllegalArgumentException) {
                call.respondJson(ApiError("invalid_request", failure.message ?: "Invalid database settings"), HttpStatusCode.BadRequest)
            }
        }
        post("/admin/database/backup") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@post
            }
            val db = databaseService
                ?: return@post call.respondJson(ApiError("unavailable", "Database operations are not configured"), HttpStatusCode.ServiceUnavailable)
            if (!db.backuppable()) {
                call.respondJson(ApiError("backup_unavailable", "Automatic backup is unavailable for this database"), HttpStatusCode.Conflict)
                return@post
            }
            try {
                withContext(Dispatchers.IO) { db.backup() }
                call.respondJson(db.backupStatus)
            } catch (_: Exception) {
                call.respondJson(ApiError("backup_failed", "Database backup failed; check server logs"), HttpStatusCode.InternalServerError)
            }
        }
        get("/admin/database/operations") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@get
            }
            val db = databaseService
                ?: return@get call.respondJson(ApiError("unavailable", "Database operations are not configured"), HttpStatusCode.ServiceUnavailable)
            call.respondJson(DatabaseOperationsResponse(db.backupStatus, db.exportStatus, db.importStatus))
        }
        post("/admin/database/export") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@post
            }
            val db = databaseService
                ?: return@post call.respondJson(ApiError("unavailable", "Database operations are not configured"), HttpStatusCode.ServiceUnavailable)
            val file = try {
                withContext(Dispatchers.IO) { db.exportDB() }
            } catch (_: Exception) {
                null
            }
            if (file == null || !Files.isRegularFile(file)) {
                call.respondJson(ApiError("export_failed", "Database export failed; check server logs"), HttpStatusCode.InternalServerError)
                return@post
            }
            try {
                call.response.headers.append(
                    HttpHeaders.ContentDisposition,
                    io.ktor.http.ContentDisposition.Attachment.withParameter(
                        io.ktor.http.ContentDisposition.Parameters.FileName,
                        file.fileName.toString(),
                    ).toString(),
                )
                call.respondOutputStream(ContentType.Application.Zip, contentLength = Files.size(file)) {
                    val output = this
                    withContext(Dispatchers.IO) { Files.newInputStream(file).use { it.copyTo(output) } }
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { db.cleanup(file) }
            }
        }
        post("/admin/database/import") {
            val actor = call.principal<LocalUserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            if (actor.role != "ADMIN") {
                call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                return@post
            }
            val db = databaseService
                ?: return@post call.respondJson(ApiError("unavailable", "Database operations are not configured"), HttpStatusCode.ServiceUnavailable)
            try {
                importDatabaseUpload(call, db)
                call.respondJson(db.importStatus, HttpStatusCode.Accepted)
            } catch (failure: IllegalArgumentException) {
                call.respondJson(ApiError("invalid_import", failure.message ?: "Invalid database import"), HttpStatusCode.BadRequest)
            } catch (failure: org.xml.sax.SAXException) {
                call.respondJson(ApiError("invalid_import", "Invalid Liquibase changelog XML"), HttpStatusCode.BadRequest)
            } catch (failure: java.util.zip.ZipException) {
                call.respondJson(ApiError("invalid_import", "Invalid database import archive"), HttpStatusCode.BadRequest)
            } catch (_: Exception) {
                call.respondJson(ApiError("import_failed", "Database import failed; check server logs"), HttpStatusCode.InternalServerError)
            }
        }
        }
    }
}
