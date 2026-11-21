package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.StandaloneLibraryScanService
import org.airsonic.player.service.StandalonePlaylistService
import org.airsonic.player.service.StandalonePlayQueueService
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorStandalonePlaylistTest {
    @TempDir
    lateinit var musicRoot: Path

    @Test
    fun playlistsArePrivateOrderedAndUpdatedAtomically() {
        Files.writeString(musicRoot.resolve("track.mp3"), "stream bytes")
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_playlist_api_${musicRoot.fileName}"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 4
        })

        val runtime = KtorRuntimeFixture(musicRoot.resolveSibling("runtime"), musicRoot, "standalone_playlist_core_${musicRoot.fileName}")
        try {
            dataSource.use {
                StandaloneDatabase.migrate(dataSource)
                val auth = LocalAuthService(dataSource)
                auth.createInitialAdmin("owner", "a strong password for tests")
                auth.createUser("other", "another strong password")
                val ownerToken = auth.login("owner", "a strong password for tests")!!.token
                val otherToken = auth.login("other", "another strong password")!!.token
                val library = runtime.mediaFolderService.getAllMusicFolders(true, true).single()
                val coreCatalog = mockCoreCatalog(
                    runtime,
                    library,
                    "track.mp3",
                    "track",
                    Files.size(musicRoot.resolve("track.mp3")),
                )
                val scans = StandaloneLibraryScanService(
                    dataSource,
                    runtime.mediaFolderService,
                    coreCatalog.mediaFileService,
                    coreCatalog.mediaScannerService,
                )
                val mediaCatalogService = org.airsonic.player.service.KtorCoreMediaCatalogService(runtime.coreDataSource, dataSource)
                val scan = scans.enqueue(library.id.toLong())
                scans.run(scan.id)
                val playlists = StandalonePlaylistService(dataSource, mediaCatalogService)
                val playQueue = StandalonePlayQueueService(dataSource, mediaCatalogService)
                val mapper = jacksonObjectMapper()

                testApplication {
                    application {
                        standaloneModule(
                            authService = auth,
                            libraryScanService = scans,
                            playlistService = playlists,
                            mediaFolderService = runtime.mediaFolderService,
                            mediaCatalogService = mediaCatalogService,
                            playQueueService = playQueue,
                        )
                    }

                val tracksResponse = client.get("/api/v1/tracks") { bearerAuth(ownerToken) }
                val trackId = mapper.readTree(tracksResponse.bodyAsText()).get("items")[0].get("id").asLong()

                val created = client.post("/api/v1/playlists") {
                    bearerAuth(ownerToken)
                    header("Content-Type", "application/json")
                    setBody("""{"name":"Road Trip","trackIds":[$trackId,$trackId]}""")
                }
                assertEquals(201, created.status.value)
                val createdBody = mapper.readTree(created.bodyAsText())
                val playlistId = createdBody.get("id").asLong()
                assertEquals("Road Trip", createdBody.get("name").asText())
                assertEquals(listOf(trackId, trackId), createdBody.get("trackIds").map { it.asLong() })
                val coreTrackId = runtime.coreDataSource.connection.use { connection ->
                    connection.prepareStatement("SELECT id FROM media_file WHERE path = ? AND folder_id = ?").use { statement ->
                        statement.setString(1, "track.mp3")
                        statement.setInt(2, library.id)
                        statement.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
                    }
                }
                assertEquals(401, client.get("/api/v1/me/play-queue").status.value)
                val initialQueue = client.get("/api/v1/me/play-queue") { bearerAuth(ownerToken) }
                assertEquals(200, initialQueue.status.value)
                assertEquals(emptyList(), mapper.readTree(initialQueue.bodyAsText()).get("trackIds").map { it.asLong() })

                val savedQueue = client.put("/api/v1/me/play-queue") {
                    bearerAuth(ownerToken)
                    header("Content-Type", "application/json")
                    setBody("""{"trackIds":[$trackId,$trackId],"currentTrackId":$trackId,"positionMillis":4321}""")
                }
                assertEquals(200, savedQueue.status.value)
                val savedQueueBody = mapper.readTree(savedQueue.bodyAsText())
                assertEquals(listOf(trackId, trackId), savedQueueBody.get("trackIds").map { it.asLong() })
                assertEquals(trackId, savedQueueBody.get("currentTrackId").asLong())
                assertEquals(4321, savedQueueBody.get("positionMillis").asLong())
                assertEquals("track", savedQueueBody.get("tracks")[0].get("title").asText())
                assertEquals(emptyList(), mapper.readTree(client.get("/api/v1/me/play-queue") {
                    bearerAuth(otherToken)
                }.bodyAsText()).get("trackIds").map { it.asLong() })

                val invalidQueue = client.put("/api/v1/me/play-queue") {
                    bearerAuth(ownerToken)
                    header("Content-Type", "application/json")
                    setBody("""{"trackIds":[999999],"currentTrackId":999999,"positionMillis":0}""")
                }
                assertEquals(400, invalidQueue.status.value)
                val unchangedQueue = mapper.readTree(client.get("/api/v1/me/play-queue") {
                    bearerAuth(ownerToken)
                }.bodyAsText())
                assertEquals(listOf(trackId, trackId), unchangedQueue.get("trackIds").map { it.asLong() })

                assertEquals(204, client.delete("/api/v1/me/play-queue") { bearerAuth(ownerToken) }.status.value)
                assertEquals(emptyList(), mapper.readTree(client.get("/api/v1/me/play-queue") {
                    bearerAuth(ownerToken)
                }.bodyAsText()).get("trackIds").map { it.asLong() })

                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "SELECT track_id FROM user_playlist_track WHERE playlist_id = ? ORDER BY position",
                    ).use { statement ->
                        statement.setLong(1, playlistId)
                        statement.executeQuery().use { rows ->
                            assertEquals(listOf(coreTrackId, coreTrackId), buildList { while (rows.next()) add(rows.getLong(1)) })
                        }
                    }
                }

                val list = client.get("/api/v1/playlists") { bearerAuth(ownerToken) }
                assertEquals(1, mapper.readTree(list.bodyAsText()).size())
                val privateList = client.get("/api/v1/playlists") { bearerAuth(otherToken) }
                assertEquals(0, mapper.readTree(privateList.bodyAsText()).size())
                assertEquals(404, client.get("/api/v1/playlists/$playlistId") { bearerAuth(otherToken) }.status.value)
                assertEquals(
                    404,
                    client.put("/api/v1/playlists/$playlistId") {
                        bearerAuth(otherToken)
                        header("Content-Type", "application/json")
                        setBody("""{"name":"Stolen","trackIds":[]}""")
                    }.status.value,
                )
                assertEquals(404, client.delete("/api/v1/playlists/$playlistId") { bearerAuth(otherToken) }.status.value)

                val invalidUpdate = client.put("/api/v1/playlists/$playlistId") {
                    bearerAuth(ownerToken)
                    header("Content-Type", "application/json")
                    setBody("""{"name":"Broken","trackIds":[999999]}""")
                }
                assertEquals(400, invalidUpdate.status.value)
                val unchanged = client.get("/api/v1/playlists/$playlistId") { bearerAuth(ownerToken) }
                val unchangedBody = mapper.readTree(unchanged.bodyAsText())
                assertEquals("Road Trip", unchangedBody.get("name").asText())
                assertEquals(listOf(trackId, trackId), unchangedBody.get("trackIds").map { it.asLong() })

                val update = client.put("/api/v1/playlists/$playlistId") {
                    bearerAuth(ownerToken)
                    header("Content-Type", "application/json")
                    setBody("""{"name":"Favorites","trackIds":[$trackId]}""")
                }
                assertEquals(200, update.status.value)
                val updatedBody = mapper.readTree(update.bodyAsText())
                assertEquals("Favorites", updatedBody.get("name").asText())
                assertEquals(listOf(trackId), updatedBody.get("trackIds").map { it.asLong() })

                assertEquals(204, client.delete("/api/v1/playlists/$playlistId") { bearerAuth(ownerToken) }.status.value)
                assertEquals(404, client.get("/api/v1/playlists/$playlistId") { bearerAuth(ownerToken) }.status.value)
                }
            }
        } finally {
            runtime.close()
        }
    }
}
