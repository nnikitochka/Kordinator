package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.airsonic.player.command.MusicFolderSettingsCommand
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.MusicFolderAdministrationService
import org.mockito.Mockito
import su.nnedition.kord.api.configureApiRouting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneMusicFolderSettingsTest {
    @Test
    fun librarySettingsAreAdminOnlyAndSupportSnapshotUpdateAndExpunge() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_music_folder_settings"
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
            val settings = MusicFolderSettingsCommand().apply {
                interval = "24"
                hour = "3"
                uploadsFolder = "Uploads"
                excludePatternString = ""
                musicFolders = emptyList()
                deletedMusicFolders = emptyList()
            }
            val service = Mockito.mock(MusicFolderAdministrationService::class.java)
            Mockito.`when`(service.settings).thenReturn(settings)

            testApplication {
                application { configureApiRouting(authService = auth, musicFolderAdministrationService = service) }

                assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/settings/music-folders") { bearerAuth(listenerToken) }.status)
                val read = client.get("/api/v1/admin/settings/music-folders") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, read.status)
                assertTrue(read.bodyAsText().contains("\"interval\":24"))

                val saved = client.put("/api/v1/admin/settings/music-folders") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{
                        "interval":48,"hour":4,"fastCache":true,"organizeByFolderStructure":false,
                        "uploadsFolder":"Uploads","excludePattern":".*\\.tmp$","ignoreSymbolicLinks":true,
                        "fullScan":false,"clearFullScanAfterScan":false,"folders":[]
                    }""")
                }
                assertEquals(HttpStatusCode.NoContent, saved.status, saved.bodyAsText())
                assertTrue(Mockito.mockingDetails(service).invocations.any { it.method.name == "save" })

                assertEquals(HttpStatusCode.Forbidden, client.post("/api/v1/admin/settings/music-folders/expunge") { bearerAuth(listenerToken) }.status)
                assertEquals(HttpStatusCode.NoContent, client.post("/api/v1/admin/settings/music-folders/expunge") { bearerAuth(adminToken) }.status)
                Mockito.verify(service).expunge()
            }
        }
    }
}
