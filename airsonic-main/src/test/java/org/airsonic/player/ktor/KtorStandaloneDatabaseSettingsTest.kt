package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.DatabaseService
import org.airsonic.player.service.SettingsService
import org.mockito.Mockito
import org.junit.jupiter.api.io.TempDir
import su.nnedition.kord.api.configureApiRouting
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

class KtorStandaloneDatabaseSettingsTest {
    @TempDir
    lateinit var home: Path

    @Test
    fun databaseSettingsAreAdminOnlyAndKeepPasswordsOutOfResponses() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_database_settings"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })
        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            auth.createUser("listener", "listener password for tests")
            val adminToken = auth.login("admin", "a strong password for tests")!!.token
            val listenerToken = auth.login("listener", "listener password for tests")!!.token
            val settings = Mockito.mock(SettingsService::class.java)
            Mockito.`when`(settings.databaseDriver).thenReturn("org.hsqldb.jdbc.JDBCDriver")
            Mockito.`when`(settings.databaseUrl).thenReturn("jdbc:hsqldb:file:/srv/kordinator")
            Mockito.`when`(settings.databaseUsername).thenReturn("kordinator")
            Mockito.`when`(settings.databasePassword).thenReturn("stored-secret")
            Mockito.`when`(settings.databaseMysqlVarcharMaxlength).thenReturn(255)
            Mockito.`when`(settings.dbBackupInterval).thenReturn(-1)
            Mockito.`when`(settings.dbBackupRetentionCount).thenReturn(10)
            val database = Mockito.mock(DatabaseService::class.java)
            Mockito.`when`(database.backuppable()).thenReturn(true)
            Mockito.`when`(database.backupStatus).thenReturn(DatabaseService.OperationStatus(1, false, false, "Backup complete"))
            Mockito.`when`(database.importStatus).thenReturn(DatabaseService.OperationStatus(1, false, false, "Import complete"))

            Files.createDirectories(home)
            val exportFile = home.resolve("kordinator-export.zip")
            val exportBytes = "database-export".toByteArray()
            Files.write(exportFile, exportBytes)
            Mockito.`when`(database.exportDB()).thenReturn(exportFile)
            val previousHome = System.getProperty("airsonic.home")
            System.setProperty("airsonic.home", home.toString())
            try {
                testApplication {
                application { configureApiRouting(authService = auth, settingsService = settings, databaseService = database) }

                assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/settings/database") { bearerAuth(listenerToken) }.status)
                val read = client.get("/api/v1/admin/settings/database") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, read.status)
                assertTrue(read.bodyAsText().contains("\"passwordConfigured\":true"))
                assertFalse(read.bodyAsText().contains("stored-secret"))

                val updated = client.put("/api/v1/admin/settings/database") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{
                        "configType":"EXTERNAL","driver":"org.postgresql.Driver","url":"jdbc:postgresql://db/kordinator",
                        "username":"kordinator","password":"rotated-secret","backupIntervalHours":24,
                        "backupRetentionCount":7,"mysqlVarcharMaxlength":255
                    }""")
                }
                assertEquals(HttpStatusCode.OK, updated.status, updated.bodyAsText())
                assertTrue(updated.bodyAsText().contains("\"restartRequired\":true"))
                Mockito.verify(settings).save()
                assertEquals(HttpStatusCode.Forbidden, client.post("/api/v1/admin/database/backup") { bearerAuth(listenerToken) }.status)
                val backup = client.post("/api/v1/admin/database/backup") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, backup.status)
                assertTrue(backup.bodyAsText().contains("Backup complete"))
                Mockito.verify(database).backup()
                assertEquals(HttpStatusCode.Forbidden, client.post("/api/v1/admin/database/export") { bearerAuth(listenerToken) }.status)
                val exported = client.post("/api/v1/admin/database/export") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, exported.status)
                assertContentEquals(exportBytes, exported.bodyAsBytes())
                Mockito.verify(database).cleanup(exportFile)
                assertEquals(HttpStatusCode.Forbidden, client.post("/api/v1/admin/database/import") { bearerAuth(listenerToken) }.status)
                val changelogHeaders = Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"import.xml\"")
                }
                val imported = client.post("/api/v1/admin/database/import") {
                    bearerAuth(adminToken)
                    setBody(MultiPartFormDataContent(formData {
                        append("file", "<databaseChangeLog/>".toByteArray(), changelogHeaders)
                    }))
                }
                assertEquals(HttpStatusCode.Accepted, imported.status, imported.bodyAsText())
                Mockito.verify(database).importDB(anyPath())
                val unsafeHeaders = Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"../outside.xml\"")
                }
                val traversal = client.post("/api/v1/admin/database/import") {
                    bearerAuth(adminToken)
                    setBody(MultiPartFormDataContent(formData {
                        append("file", "<databaseChangeLog/>".toByteArray(), unsafeHeaders)
                    }))
                }
                assertEquals(HttpStatusCode.BadRequest, traversal.status)
                Mockito.verify(database, Mockito.times(1)).importDB(anyPath())
                }
            } finally {
                if (previousHome == null) System.clearProperty("airsonic.home") else System.setProperty("airsonic.home", previousHome)
            }
        }
    }
}

private fun anyPath(): Path = Mockito.any(Path::class.java) ?: Path.of(".")
