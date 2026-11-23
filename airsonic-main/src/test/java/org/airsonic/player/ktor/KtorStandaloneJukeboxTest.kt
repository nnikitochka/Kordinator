package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.JukeboxLegacySubsonicService
import org.airsonic.player.service.KtorCoreMediaCatalogService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.StandaloneJukeboxService
import org.airsonic.player.service.StandaloneTrack
import org.mockito.Mockito
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneJukeboxTest {
    @TempDir
    lateinit var home: Path

    @Test
    fun jukeboxApiUsesBearerOwnerAndAccessibleTrackIds() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_jukebox_${home.fileName}"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })
        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            val listenerId = auth.createUser("listener", "listener password for tests")
            auth.createUser("limited", "limited user password for tests")
            val adminToken = auth.login("admin", "a strong password for tests")!!.token
            val listenerToken = auth.login("listener", "listener password for tests")!!.token
            val limitedToken = auth.login("limited", "limited user password for tests")!!.token
            assertTrue(auth.setAllowedLibraries(listenerId, setOf(1L)))

            val catalog = Mockito.mock(KtorCoreMediaCatalogService::class.java)
            val mediaFiles = Mockito.mock(MediaFileService::class.java)
            val track = StandaloneTrack(101, 1, "album/track.flac", "Track", "Artist", "Album", "Artist", 1, 1, 2024, "Rock", 180, 1_000, null)
            Mockito.`when`(catalog.tracksByCoreIds(listOf(101L), setOf(1L))).thenReturn(listOf(track))
            Mockito.`when`(catalog.tracksByCoreIds(listOf(101L), null)).thenReturn(listOf(track))
            val file = MediaFile().apply {
                id = 5
                folderId = 1
                path = "album/track.flac"
                mediaType = MediaFile.MediaType.MUSIC
                title = "Track"
                artist = "Artist"
                albumName = "Album"
                format = "flac"
                duration = 180.0
                setPresent(true)
            }
            Mockito.`when`(mediaFiles.getMediaFile("album/track.flac", 1)).thenReturn(file)
            val jukebox = StandaloneJukeboxService(
                Mockito.mock(MediaFolderService::class.java),
                Mockito.mock(JukeboxLegacySubsonicService::class.java),
            )

            testApplication {
                application {
                    standaloneModule(
                        authService = auth,
                        mediaCatalogService = catalog,
                        mediaFileService = mediaFiles,
                        standaloneJukeboxService = jukebox,
                    )
                }

                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/jukebox").status)
                val hidden = client.put("/api/v1/jukebox/queue") {
                    bearerAuth(limitedToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"trackIds":[101]}""")
                }
                assertEquals(HttpStatusCode.NotFound, hidden.status)

                val queued = client.put("/api/v1/jukebox/queue") {
                    bearerAuth(listenerToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"trackIds":[101]}""")
                }
                assertEquals(HttpStatusCode.OK, queued.status, queued.bodyAsText())
                assertTrue(queued.bodyAsText().contains("\"trackId\":101"))
                assertEquals(HttpStatusCode.Conflict, client.get("/api/v1/jukebox") { bearerAuth(adminToken) }.status)
                assertEquals(HttpStatusCode.Conflict, client.get("/api/v1/jukebox/queue/export") {
                    bearerAuth(adminToken)
                }.status)
                val export = client.get("/api/v1/jukebox/queue/export") { bearerAuth(listenerToken) }
                assertEquals(HttpStatusCode.OK, export.status)
                assertTrue(export.headers["Content-Disposition"].orEmpty().contains("jukebox.m3u"))
                assertEquals("""#EXTM3U
#EXTINF:180,Artist - Track
/api/v1/tracks/101/stream
""".trimIndent() + "\n", export.bodyAsText())

                val gain = client.post("/api/v1/jukebox/commands") {
                    bearerAuth(listenerToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"action":"gain","gain":0.4}""")
                }
                assertEquals(HttpStatusCode.OK, gain.status, gain.bodyAsText())
                assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/jukebox") { bearerAuth(listenerToken) }.status)
                assertEquals(HttpStatusCode.OK, client.get("/api/v1/jukebox") { bearerAuth(adminToken) }.status)
            }
        }
    }
}
