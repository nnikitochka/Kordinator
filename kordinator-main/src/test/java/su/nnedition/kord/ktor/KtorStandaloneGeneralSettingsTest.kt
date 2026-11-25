package su.nnedition.kord.ktor

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
import su.nnedition.kord.command.GeneralSettingsCommand
import su.nnedition.kord.domain.Theme
import su.nnedition.kord.runtime.StandaloneDatabase
import su.nnedition.kord.security.LocalAuthService
import su.nnedition.kord.service.GeneralSettingsAdministrationService
import org.mockito.Mockito
import su.nnedition.kord.api.configureApiRouting
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneGeneralSettingsTest {
    @Test
    fun generalSettingsAreAdministratorOnlyAndValidateBeforeSaving() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_general_settings"
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
            val service = Mockito.mock(GeneralSettingsAdministrationService::class.java)
            val current = GeneralSettingsCommand().apply {
                themes = arrayOf(Theme("dark", "Dark"))
                themeIndex = "0"
            }
            Mockito.`when`(service.settings).thenReturn(current)
            Mockito.`when`(service.availableLocales).thenReturn(arrayOf(Locale.US))

            testApplication {
                application { configureApiRouting(authService = auth, generalSettingsService = service) }

                assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/settings/general") { bearerAuth(listenerToken) }.status)
                val read = client.get("/api/v1/admin/settings/general") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, read.status)
                assertTrue(read.bodyAsText().contains("dark"))
                val save = client.put("/api/v1/admin/settings/general") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{
                        "playlistFolder":"/tmp/playlists",
                        "musicFileTypes":"mp3,flac",
                        "videoFileTypes":"mp4",
                        "coverArtFileTypes":"jpg,png",
                        "coverArtSource":"FILE",
                        "coverArtQuality":80,
                        "coverArtConcurrency":2,
                        "index":"A,B,C",
                        "ignoredArticles":"the,a",
                        "genreSeparators":";",
                        "shortcuts":"",
                        "sortAlbumsByYear":true,
                        "gettingStartedEnabled":false,
                        "welcomeTitle":"Welcome",
                        "welcomeSubtitle":"Music",
                        "welcomeMessage":"Hello",
                        "loginMessage":"",
                        "sessionDuration":"30d",
                        "localeIndex":"0",
                        "themeIndex":"0"
                    }""")
                }
                assertEquals(HttpStatusCode.NoContent, save.status)
                val invalid = client.put("/api/v1/admin/settings/general") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
                assertEquals(HttpStatusCode.BadRequest, invalid.status)
            }
        }
    }
}
