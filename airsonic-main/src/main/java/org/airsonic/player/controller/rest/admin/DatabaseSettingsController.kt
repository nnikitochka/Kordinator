package org.airsonic.player.controller.rest.admin

import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.service.ConfigurationPropertiesService
import org.airsonic.player.service.DatabaseService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.StatusService
import org.airsonic.player.runtime.RuntimeConfiguration
import org.airsonic.player.util.FileUtil
import org.subsonic.restapi.DatabaseSettings
import java.nio.file.Files
import java.nio.file.Path
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/** Admin-only extensions. Upload accepts XML changelogs or one exported ZIP in repeated `file` parts.
 * No client-supplied filesystem paths/callbacks. Settings are validated before any mutation.
 */
class DatabaseSettingsController(
    private val settings: SettingsService, private val database: DatabaseService,
    private val statuses: StatusService, private val security: SecurityService, private val writer: JAXBWriter,
    private val properties: ConfigurationPropertiesService
) {
    private val exportMutex = Mutex()

    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        with(root) {
            get(READ_PATH) { handle(call, request(call), READ_PATH) }
            post(SAVE_PATH) { handle(call, request(call), SAVE_PATH) }
            post(BACKUP_PATH) { handle(call, request(call), BACKUP_PATH) }
            post(IMPORT_PATH) { handle(call, request(call), IMPORT_PATH) }
            // Export is POST because it generates an archive and changes operation status.
            post(EXPORT_PATH) { exportMutex.withLock { handle(call, request(call), EXPORT_PATH) } }
        }
    }

    private suspend fun handle(call: ApplicationCall, request: UsersRequest, action: String) {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        var export: Path? = null
        val response = try {
            if (!request.user.isAdminRole) writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Administrator access required")
            else {
                when (action) {
                    SAVE_PATH -> withContext(Dispatchers.IO) { save(request.parameters) }
                    BACKUP_PATH -> withContext(Dispatchers.IO) {
                        require(database.backuppable()) { "Backup unavailable for this database" }
                        database.backup()
                    }
                    IMPORT_PATH -> upload(call)
                    EXPORT_PATH -> withContext(Dispatchers.IO) {
                        export = database.exportDB() ?: throw IllegalStateException("Export failed")
                    }
                }
                writer.createResponse(true).apply { if (action == READ_PATH) databaseSettings = read() }
            }
        } catch (e: CancellationException) {
            export?.let { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { database.cleanup(it) } }
            throw e
        } catch (e: IllegalArgumentException) {
            writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Invalid database settings")
        } catch (_: Exception) {
            // JDBC URLs and codec exceptions can contain credentials.
            writer.createErrorResponse(ErrorCode.GENERIC, "Database operation failed; check server logs")
        }
        if (export != null) {
            streamExport(call, request, requireNotNull(export))
            return
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], response)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private suspend fun streamExport(call: ApplicationCall, request: UsersRequest, file: Path) {
        // The streaming callback owns the archive until the transfer finishes or disconnects.
        try {
            call.response.header(HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.fileName.toString()).toString())
            call.respondOutputStream(ContentType.Application.Zip, contentLength = Files.size(file)) {
                withContext(Dispatchers.IO) {
                    val status = statuses.createDownloadStatus(requireNotNull(request.transferPlayer))
                    status.externalFile = file; status.bytesTotal = Files.size(file)
                    try {
                        Files.newInputStream(file).use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                settings.downloadBitrateLimiter.acquire(count)
                                write(buffer, 0, count)
                                status.addBytesTransferred(count.toLong())
                            }
                        }
                    } finally {
                        statuses.removeDownloadStatus(status)
                        security.updateUserByteCounts(request.user, 0L, status.bytesTransferred, 0L)
                    }
                }
            }
        } finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { database.cleanup(file) } }
    }

    private fun configured(key: String, fallback: String?): String? = properties.configuration.getString(key, fallback)

    private fun configured(key: String, legacyKey: String, fallback: String?): String? =
        properties.configuration.getString(key, properties.configuration.getString(legacyKey, fallback))

    @Synchronized
    private fun read() = DatabaseSettings().apply {
        // Startup defaults may have higher priority in the runtime configuration. Show pending values
        // from the properties file so the form reflects what will be used after restart.
        driver = configured(RuntimeConfiguration.DATABASE_DRIVER_KEY, "spring.datasource.driver-class-name", settings.databaseDriver).orEmpty()
        url = configured(SettingsService.KEY_DATABASE_URL, "spring.datasource.url", settings.databaseUrl).orEmpty()
        username = configured(SettingsService.KEY_DATABASE_USERNAME, "spring.datasource.username", settings.databaseUsername).orEmpty()
        jndiName = configured(RuntimeConfiguration.DATABASE_JNDI_NAME_KEY, "spring.datasource.jndi-name", settings.databaseJNDIName).orEmpty()
        val password = configured(SettingsService.KEY_DATABASE_PASSWORD, "spring.datasource.password", settings.databasePassword).orEmpty()
        configType = when {
            jndiName.isNotBlank() -> "JNDI"
            url == SettingsService.getDefaultJDBCUrl() && username == SettingsService.getDefaultJDBCUsername() &&
                password == SettingsService.getDefaultJDBCPassword() -> "BUILTIN"
            else -> "EXTERNAL"
        }
        mysqlVarcharMaxlength = properties.configuration.getInt(
            RuntimeConfiguration.DATABASE_MIGRATION_MYSQL_VARCHAR_LIMIT_KEY,
            properties.configuration.getInt("spring.liquibase.parameters.mysqlVarcharLimit", settings.databaseMysqlVarcharMaxlength))
        dbBackupInterval = settings.dbBackupInterval; dbBackupRetentionCount = settings.dbBackupRetentionCount
        backuppable = database.backuppable()
    }

    @Synchronized
    private fun save(p: Parameters) {
        fun text(key: String) = p[key] ?: throw IllegalArgumentException("Missing $key")
        fun positiveOrDisabled(key: String) = text(key).toIntOrNull()?.also {
            require(it == -1 || it > 0) { "Invalid $key" }
        } ?: throw IllegalArgumentException("Invalid $key")
        val type = text("configType")
        require(type in setOf("BUILTIN", "EXTERNAL", "JNDI")) { "Invalid configType" }
        val interval = positiveOrDisabled("dbBackupInterval")
        val retention = positiveOrDisabled("dbBackupRetentionCount")
        val maxlength = if (type == "BUILTIN") null else text("mysqlVarcharMaxlength").toIntOrNull()
            ?.takeIf { it > 0 } ?: if (type == "BUILTIN") null else throw IllegalArgumentException("Invalid mysqlVarcharMaxlength")
        val driver = if (type == "EXTERNAL") text("driver").also { require(it.isNotBlank()) { "Missing driver" } } else null
        val url = if (type == "EXTERNAL") text("url").also { require(it.startsWith("jdbc:")) { "Invalid JDBC URL" } } else null
        val username = if (type == "EXTERNAL") text("username") else null
        val clearPassword = when (p["clearPassword"]) {
            null, "false" -> false
            "true" -> true
            else -> throw IllegalArgumentException("Invalid clearPassword")
        }
        require(!clearPassword || p["password"].isNullOrEmpty()) { "Choose password replacement or clearing" }
        val password = if (clearPassword) "" else p["password"]?.takeIf { it.isNotEmpty() } ?: configured(SettingsService.KEY_DATABASE_PASSWORD, settings.databasePassword)
        val jndi = if (type == "JNDI") text("jndiName").also { require(it.isNotBlank()) { "Missing jndiName" } } else null
        settings.resetDatabaseToDefault()
        when (type) {
            "EXTERNAL" -> {
                settings.databaseDriver = driver; settings.databaseUrl = url
                settings.databaseUsername = username; settings.databasePassword = password
            }
            "JNDI" -> settings.databaseJNDIName = jndi
        }
        if (maxlength != null) settings.databaseMysqlVarcharMaxlength = maxlength
        settings.dbBackupInterval = interval; settings.dbBackupRetentionCount = retention
        settings.save()
    }

    private suspend fun upload(call: ApplicationCall) {
        require(call.request.headers[HttpHeaders.ContentType]?.startsWith("multipart/form-data", true) == true) { "Multipart upload required" }
        require((call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0) <= MAX_UPLOAD + 65536) { "Import exceeds 256 MiB" }
        val parent = SettingsService.getAirsonicHome().resolve("backups")
        Files.createDirectories(parent)
        val staging = Files.createTempDirectory(parent, "airsonic.importDB.")
        try {
            val files = mutableListOf<Pair<String, Path>>()
            var total = 0L
            var invalid = false
            call.receiveMultipart(formFieldLimit = Long.MAX_VALUE).forEachPart { part ->
                try {
                    if (part is PartData.FileItem) {
                        val name = part.originalFileName.orEmpty()
                        val accepted = part.name == "file" && name.matches(Regex("[A-Za-z0-9_.-]+\\.(xml|zip)", RegexOption.IGNORE_CASE)) && files.size < 100
                        if (!accepted) invalid = true
                        val file = if (accepted) Files.createTempFile(staging, "upload-", ".part") else null
                        if (file != null) files.add(name to file)
                        withContext(Dispatchers.IO) {
                            part.provider().toInputStream().use { input ->
                                (if (file == null) java.io.OutputStream.nullOutputStream() else Files.newOutputStream(file)).use { output ->
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        if (total <= MAX_UPLOAD) output.write(buffer, 0, count)
                                    }
                                }
                            }
                        }
                    }
                } finally { part.dispose() }
            }
            require(total <= MAX_UPLOAD) { "Import exceeds 256 MiB" }
            require(!invalid && files.isNotEmpty()) { "Upload XML files or one exported ZIP" }
            withContext(Dispatchers.IO) {
                val content = Files.createDirectory(staging.resolve("content"))
                var expanded = 0L
                fun extract(name: String, input: InputStream) {
                    require(name.matches(Regex("[A-Za-z0-9_.-]+\\.xml", RegexOption.IGNORE_CASE))) { "Only flat XML changelogs are accepted" }
                    Files.newOutputStream(content.resolve(name), java.nio.file.StandardOpenOption.CREATE_NEW).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            expanded += count
                            require(expanded <= MAX_EXPANDED) { "Expanded import exceeds 1 GiB" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (files.size == 1 && files[0].first.endsWith(".zip", true)) {
                    ZipFile(files[0].second.toFile()).use { zip ->
                        val entries = zip.entries().asSequence().take(101).toList()
                        require(entries.size in 1..100) { "Invalid archive entry count" }
                        entries.forEach { entry -> zip.getInputStream(entry).use { extract(entry.name, it) } }
                    }
                } else files.forEach { (name, file) -> Files.newInputStream(file).use { extract(name, it) } }
                // Reject malformed XML/DOCTYPE before the destructive domain operation starts.
                val factory = SAXParserFactory.newInstance().apply {
                    isNamespaceAware = true
                    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                }
                Files.list(content).use { paths -> paths.forEach { file ->
                    val parser = factory.newSAXParser().apply {
                        setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                        setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                    }
                    var rootSeen = false
                    Files.newInputStream(file).use { input -> parser.parse(input, object : DefaultHandler() {
                        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                            if (!rootSeen) require(localName == "databaseChangeLog") { "Expected Liquibase changelog" }
                            rootSeen = true
                        }
                    }) }
                    require(rootSeen) { "Empty changelog" }
                } }
                database.importDB(content)
            }
        } finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { FileUtil.delete(staging) } }
    }

    companion object {
        const val READ_PATH = "/rest/getDatabaseSettings"
        const val SAVE_PATH = "/rest/saveDatabaseSettings"
        const val BACKUP_PATH = "/rest/backupDatabase"
        const val EXPORT_PATH = "/rest/exportDatabase"
        const val IMPORT_PATH = "/rest/importDatabase"
        @JvmField val PATHS = arrayOf(READ_PATH, SAVE_PATH, BACKUP_PATH, EXPORT_PATH, IMPORT_PATH)
        private const val MAX_UPLOAD = 256L * 1024 * 1024
        private const val MAX_EXPANDED = 1024L * 1024 * 1024
    }
}
