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
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.PlayerTechnology
import org.airsonic.player.domain.Transcoding
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.JukeboxService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.TranscodingService
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandalonePlayerSettingsTest {
    @Test
    fun administratorCanReadUpdateCloneAndDeletePlayers() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_player_settings"
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
            val player = Player().apply {
                id = 7
                name = "Living room"
                username = "admin"
            }
            val cloned = Player().apply {
                id = 8
                name = "Living room (copy)"
                username = "admin"
            }
            val players = Mockito.mock(PlayerService::class.java)
            Mockito.`when`(players.getAllPlayers()).thenReturn(listOf(player))
            Mockito.`when`(players.getPlayerById(7)).thenReturn(player)
            Mockito.`when`(players.clonePlayer(7)).thenReturn(cloned)
            val transcoding = Mockito.mock(TranscodingService::class.java)
            Mockito.`when`(transcoding.allTranscodings).thenReturn(listOf(Transcoding(3, "MP3", "flac", "mp3", "ffmpeg", null, null, false)))
            Mockito.`when`(transcoding.getTranscodingsForPlayer(player)).thenReturn(emptyList())
            Mockito.`when`(transcoding.getTranscodingsForPlayer(cloned)).thenReturn(emptyList())
            val jukebox = Mockito.mock(JukeboxService::class.java)

            testApplication {
                application {
                    standaloneModule(
                        authService = auth,
                        playerService = players,
                        transcodingService = transcoding,
                        jukeboxService = jukebox,
                    )
                }

                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/admin/players").status)
                assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/players") { bearerAuth(listenerToken) }.status)
                val listed = client.get("/api/v1/admin/players") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, listed.status)
                assertTrue(listed.bodyAsText().contains("Living room"))
                assertTrue(listed.bodyAsText().contains("MP3"))

                val updated = client.put("/api/v1/admin/players/7") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{
                        "name":"Living room","dynamicIp":true,"autoControlEnabled":false,
                        "m3uBomEnabled":true,"technology":"JUKEBOX","transcodeScheme":"OFF","transcodingIds":[3]
                    }""")
                }
                assertEquals(HttpStatusCode.OK, updated.status, updated.bodyAsText())
                assertEquals(PlayerTechnology.JUKEBOX, player.technology)
                Mockito.verify(jukebox).stop(player)
                assertTrue(Mockito.mockingDetails(transcoding).invocations.any { it.method.name == "setTranscodingsForPlayer" })

                val clonedResponse = client.post("/api/v1/admin/players/7/clone") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.Created, clonedResponse.status)
                assertTrue(clonedResponse.bodyAsText().contains("\"id\":8"))
                assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/admin/players/7") { bearerAuth(adminToken) }.status)
                Mockito.verify(players).removePlayerById(7)
            }
        }
    }
}
