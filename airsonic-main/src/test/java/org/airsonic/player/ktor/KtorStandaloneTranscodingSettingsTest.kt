package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.TranscodingService
import org.mockito.Mockito
import su.nnedition.kord.api.configureApiRouting
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneTranscodingSettingsTest {
    @Test
    fun transcodingSettingsAreAdministratorOnlyAndValidateProfileSet() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_transcoding_settings"
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
            val transcoding = Mockito.mock(TranscodingService::class.java)
            val settings = Mockito.mock(SettingsService::class.java)
            Mockito.`when`(settings.brand).thenReturn("Kordinator")
            val previousHome = System.getProperty("airsonic.home")
            val temporaryHome = Files.createTempDirectory("kordinator-transcoding-test")
            System.setProperty("airsonic.home", temporaryHome.toString())

            try {
              testApplication {
                application { configureApiRouting(authService = auth, transcodingService = transcoding, settingsService = settings) }

                assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/settings/transcoding") { bearerAuth(listenerToken) }.status)
                val read = client.get("/api/v1/admin/settings/transcoding") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, read.status)
                assertTrue(read.bodyAsText().contains("Kordinator"))

                val save = client.put("/api/v1/admin/settings/transcoding") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{
                        "profiles":[],"deleteIds":[],"downsampleCommand":"ffmpeg",
                        "hlsCommand":"ffmpeg","jukeboxCommand":"ffmpeg","videoImageCommand":"ffmpeg",
                        "subtitlesExtractionCommand":"ffmpeg","estimateTimePadding":100,"estimateBytePadding":200
                    }""")
                }
                assertEquals(HttpStatusCode.NoContent, save.status)

                val invalid = client.put("/api/v1/admin/settings/transcoding") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{
                        "profiles":[{"id":999,"name":"bad","sourceFormats":"flac","targetFormat":"mp3","step1":"ffmpeg"}],
                        "deleteIds":[],"downsampleCommand":"","hlsCommand":"","jukeboxCommand":"",
                        "videoImageCommand":"","subtitlesExtractionCommand":""
                    }""")
                }
                assertEquals(HttpStatusCode.BadRequest, invalid.status)
              }
            } finally {
                if (previousHome == null) System.clearProperty("airsonic.home") else System.setProperty("airsonic.home", previousHome)
                Files.walk(temporaryHome).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
