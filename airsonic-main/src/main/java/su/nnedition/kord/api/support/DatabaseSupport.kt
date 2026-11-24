package su.nnedition.kord.api.support

import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.service.DatabaseService
import org.airsonic.player.service.SettingsService
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import su.nnedition.kord.api.MAX_DATABASE_IMPORT_BYTES
import su.nnedition.kord.api.MAX_DATABASE_IMPORT_EXPANDED_BYTES
import su.nnedition.kord.api.module.admin.settings.UpdateDatabaseSettingsRequest

internal fun updateDatabaseSettings(settings: SettingsService, request: UpdateDatabaseSettingsRequest) {
    require(request.configType in setOf("BUILTIN", "EXTERNAL", "JNDI")) { "Invalid database configuration type" }
    require(request.backupIntervalHours == -1 || request.backupIntervalHours > 0) { "Backup interval must be -1 or positive" }
    require(request.backupRetentionCount == -1 || request.backupRetentionCount > 0) { "Backup retention must be -1 or positive" }
    require(!request.clearPassword || request.password.isNullOrEmpty()) { "Choose password replacement or clearing" }
    val driver = request.driver.trim()
    val url = request.url.trim()
    val username = request.username.trim()
    val jndiName = request.jndiName.trim()
    if (request.configType == "EXTERNAL") {
        require(driver.isNotEmpty()) { "Database driver is required" }
        require(url.startsWith("jdbc:")) { "Database URL must start with jdbc:" }
        require(request.mysqlVarcharMaxlength > 0) { "MySQL varchar length must be positive" }
    }
    if (request.configType == "JNDI") {
        require(jndiName.isNotEmpty()) { "JNDI name is required" }
        require(request.mysqlVarcharMaxlength > 0) { "MySQL varchar length must be positive" }
    }
    val password = when {
        request.clearPassword -> ""
        !request.password.isNullOrEmpty() -> request.password
        else -> settings.databasePassword
    }
    settings.resetDatabaseToDefault()
    when (request.configType) {
        "EXTERNAL" -> {
            settings.databaseDriver = driver
            settings.databaseUrl = url
            settings.databaseUsername = username
            settings.databasePassword = password
            settings.databaseMysqlVarcharMaxlength = request.mysqlVarcharMaxlength
        }
        "JNDI" -> {
            settings.databaseJNDIName = jndiName
            settings.databaseMysqlVarcharMaxlength = request.mysqlVarcharMaxlength
        }
    }
    settings.dbBackupInterval = request.backupIntervalHours
    settings.dbBackupRetentionCount = request.backupRetentionCount
    settings.save()
}

internal suspend fun importDatabaseUpload(call: ApplicationCall, database: DatabaseService) {
    require(call.request.headers[HttpHeaders.ContentType]?.startsWith("multipart/form-data", true) == true) {
        "Multipart upload required"
    }
    require((call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L) <= MAX_DATABASE_IMPORT_BYTES + 65_536L) {
        "Import exceeds 256 MiB"
    }
    val parent = SettingsService.getAirsonicHome().resolve("backups")
    Files.createDirectories(parent)
    val staging = Files.createTempDirectory(parent, "kordinator.import.")
    try {
        val files = mutableListOf<Pair<String, Path>>()
        var total = 0L
        var invalidPart = false
        call.receiveMultipart(formFieldLimit = Long.MAX_VALUE).forEachPart { part ->
            try {
                if (part is PartData.FileItem) {
                    val name = part.originalFileName.orEmpty()
                    val accepted = part.name == "file" && name.matches(Regex("[A-Za-z0-9_.-]+\\.(xml|zip)", RegexOption.IGNORE_CASE)) && files.size < 100
                    if (!accepted) invalidPart = true
                    val target = if (accepted) Files.createTempFile(staging, "upload-", ".part") else null
                    if (target != null) files.add(name to target)
                    withContext(Dispatchers.IO) {
                        part.provider().toInputStream().use { input ->
                            (target?.let(Files::newOutputStream) ?: java.io.OutputStream.nullOutputStream()).use { output ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= MAX_DATABASE_IMPORT_BYTES) { "Import exceeds 256 MiB" }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                }
            } finally {
                part.dispose()
            }
        }
        require(!invalidPart && files.isNotEmpty()) { "Upload XML files or one exported ZIP" }
        withContext(Dispatchers.IO) {
            val content = Files.createDirectory(staging.resolve("content"))
            var expanded = 0L
            fun extract(name: String, input: InputStream) {
                require(name.matches(Regex("[A-Za-z0-9_.-]+\\.xml", RegexOption.IGNORE_CASE))) {
                    "Only flat XML changelogs are accepted"
                }
                Files.newOutputStream(content.resolve(name), StandardOpenOption.CREATE_NEW).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        expanded += count
                        require(expanded <= MAX_DATABASE_IMPORT_EXPANDED_BYTES) { "Expanded import exceeds 1 GiB" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (files.size == 1 && files.single().first.endsWith(".zip", true)) {
                ZipFile(files.single().second.toFile()).use { zip ->
                    val entries = zip.entries().asSequence().take(101).toList()
                    require(entries.size in 1..100) { "Invalid archive entry count" }
                    entries.forEach { entry -> zip.getInputStream(entry).use { extract(entry.name, it) } }
                }
            } else {
                files.forEach { (name, file) -> Files.newInputStream(file).use { extract(name, it) } }
            }
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            Files.list(content).use { paths ->
                paths.forEach { file ->
                    val parser = factory.newSAXParser().apply {
                        setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                        setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                    }
                    var rootSeen = false
                    Files.newInputStream(file).use { input ->
                        parser.parse(input, object : DefaultHandler() {
                            override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                                if (!rootSeen) require(localName == "databaseChangeLog") { "Expected Liquibase changelog" }
                                rootSeen = true
                            }
                        })
                    }
                    require(rootSeen) { "Empty changelog" }
                }
            }
            database.importDB(content)
        }
    } finally {
        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
            Files.walk(staging).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
