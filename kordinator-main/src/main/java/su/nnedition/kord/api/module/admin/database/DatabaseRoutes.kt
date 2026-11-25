package su.nnedition.kord.api.module.admin.database

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.nnedition.kord.security.LocalUserPrincipal
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.module.admin.settings.DatabaseOperationsResponse
import su.nnedition.kord.api.module.admin.settings.DatabaseSettingsResponse
import su.nnedition.kord.api.module.admin.settings.UpdateDatabaseSettingsRequest
import su.nnedition.kord.api.support.importDatabaseUpload
import su.nnedition.kord.api.support.jsonMapper
import su.nnedition.kord.api.support.respondJson
import su.nnedition.kord.api.support.updateDatabaseSettings

fun Route.databaseRoutes(
    context: ApiContext,
) {
    get("/admin/settings/database") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@get
        }
        val settings = context.settingsService
            ?: return@get call.respondJson(ApiError("unavailable", "Settings are not configured"), HttpStatusCode.ServiceUnavailable)
        val db = context.databaseService
        val type = when {
            settings.getDatabaseJNDIName() != null -> "JNDI"
            settings.getDatabaseUrl() == SettingsService.getDefaultJDBCUrl() &&
            settings.getDatabaseUsername() == SettingsService.getDefaultJDBCUsername() &&
            settings.getDatabasePassword() == SettingsService.getDefaultJDBCPassword() -> "BUILTIN"
            else -> "EXTERNAL"
        }
        call.respondJson(DatabaseSettingsResponse(
                type, settings.getDatabaseDriver().orEmpty(), settings.getDatabaseUrl().orEmpty(),
                settings.getDatabaseUsername().orEmpty(), settings.getDatabaseJNDIName().orEmpty(),
                settings.getDatabaseMysqlVarcharMaxlength(), settings.getDbBackupInterval(),
                settings.getDbBackupRetentionCount(), db?.backuppable() ?: false,
                !settings.getDatabasePassword().isNullOrEmpty(),
        ))
    }
    put("/admin/settings/database") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@put
        }
        val settings = context.settingsService
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
        val db = context.databaseService
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
        val db = context.databaseService
            ?: return@get call.respondJson(ApiError("unavailable", "Database operations are not configured"), HttpStatusCode.ServiceUnavailable)
        call.respondJson(DatabaseOperationsResponse(db.backupStatus, db.exportStatus, db.importStatus))
    }
    post("/admin/database/export") {
        val actor = call.principal<LocalUserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        if (actor.role != "ADMIN") {
            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
            return@post
        }
        val db = context.databaseService
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
        val db = context.databaseService
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
