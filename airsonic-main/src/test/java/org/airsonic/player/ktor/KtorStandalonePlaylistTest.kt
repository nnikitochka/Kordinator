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
import org.airsonic.player.service.StandaloneFavoriteService
import org.airsonic.player.service.StandaloneNowPlayingService
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
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
                    artist = "Track Artist",
                    album = "Test Album",
                    albumArtist = "Album Artist",
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
                val favorites = StandaloneFavoriteService(dataSource, mediaCatalogService)
                val nowPlaying = StandaloneNowPlayingService(dataSource, mediaCatalogService)
                val mapper = jacksonObjectMapper()

                testApplication {
                    application {
                        standaloneModule(
                            authService = auth,
                            libraryScanService = scans,
                            playlistService = playlists,
                            favoriteService = favorites,
                            mediaFolderService = runtime.mediaFolderService,
                            mediaCatalogService = mediaCatalogService,
                            playQueueService = playQueue,
                            nowPlayingService = nowPlaying,
                        )
                    }

                val tracksResponse = client.get("/api/v1/tracks") { bearerAuth(ownerToken) }
                val trackId = mapper.readTree(tracksResponse.bodyAsText()).get("items")[0].get("id").asLong()

                assertEquals(401, client.get("/api/v1/me/now-playing").status.value)
                val firstReport = client.put("/api/v1/me/now-playing") {
                    bearerAuth(ownerToken)
                    header("Content-Type", "application/json")
                    setBody("""{"clientId":"desktop-main","trackId":$trackId,"positionMillis":18500,"isPlaying":true}""")
                }
                assertEquals(204, firstReport.status.value)
                val privateState = mapper.readTree(client.get("/api/v1/me/now-playing") {
                    bearerAuth(ownerToken)
                }.bodyAsText())
                assertEquals(1, privateState.get("count").asInt())
                assertEquals(trackId, privateState.get("items")[0].get("trackId").asLong())
                assertEquals("desktop-main", privateState.get("items")[0].get("clientId").asText())
                assertEquals(18500, privateState.get("items")[0].get("positionMillis").asLong())
                assertEquals("track", privateState.get("items")[0].get("title").asText())
                assertEquals(0, mapper.readTree(client.get("/api/v1/me/now-playing") {
                    bearerAuth(otherToken)
                }.bodyAsText()).get("count").asInt())
                assertEquals(404, client.put("/api/v1/me/now-playing") {
                    bearerAuth(otherToken)
                    header("Content-Type", "application/json")
                    setBody("""{"clientId":"other-client","trackId":$trackId,"positionMillis":0,"isPlaying":true}""")
                }.status.value)
                assertEquals(400, client.put("/api/v1/me/now-playing") {
                    bearerAuth(ownerToken)
                    header("Content-Type", "application/json")
                    setBody("""{"clientId":"desktop-main","trackId":$trackId,"positionMillis":-1,"isPlaying":true}""")
                }.status.value)

                val updatedReport = client.put("/api/v1/me/now-playing") {
                    bearerAuth(ownerToken)
                    header("Content-Type", "application/json")
                    setBody("""{"clientId":"desktop-main","trackId":$trackId,"positionMillis":30000,"isPlaying":false}""")
                }
                assertEquals(204, updatedReport.status.value)
                val updatedState = mapper.readTree(client.get("/api/v1/me/now-playing") {
                    bearerAuth(ownerToken)
                }.bodyAsText())
                assertEquals(1, updatedState.get("count").asInt())
                assertEquals(30000, updatedState.get("items")[0].get("positionMillis").asLong())
                assertEquals(false, updatedState.get("items")[0].get("isPlaying").asBoolean())
                assertEquals(204, client.delete("/api/v1/me/now-playing/desktop-main") {
                    bearerAuth(ownerToken)
                }.status.value)
                assertEquals(0, mapper.readTree(client.get("/api/v1/me/now-playing") {
                    bearerAuth(ownerToken)
                }.bodyAsText()).get("count").asInt())
                assertEquals(204, client.put("/api/v1/me/now-playing") {
                    bearerAuth(ownerToken)
                    header("Content-Type", "application/json")
                    setBody("""{"clientId":"stale-client","trackId":$trackId,"positionMillis":0,"isPlaying":false}""")
                }.status.value)
                dataSource.connection.use { connection ->
                    connection.prepareStatement("UPDATE user_now_playing SET updated_at = ? WHERE user_id = ? AND client_id = ?").use { statement ->
                        statement.setTimestamp(1, Timestamp.from(Instant.now().minus(Duration.ofMinutes(6))))
                        statement.setLong(2, auth.users().first { it.username == "owner" }.id)
                        statement.setString(3, "stale-client")
                        statement.executeUpdate()
                    }
                }
                assertEquals(0, mapper.readTree(client.get("/api/v1/me/now-playing") {
                    bearerAuth(ownerToken)
                }.bodyAsText()).get("count").asInt())

                val albums = client.get("/api/v1/albums?libraryId=${library.id}") { bearerAuth(ownerToken) }
                val album = mapper.readTree(albums.bodyAsText()).get("items")[0]
                val albumId = album.get("id").asText()
                assertEquals("Test Album", album.get("name").asText())
                val artists = client.get("/api/v1/artists?libraryId=${library.id}") { bearerAuth(ownerToken) }
                val artist = mapper.readTree(artists.bodyAsText()).get("items")[0]
                val artistId = artist.get("id").asText()
                assertEquals("Album Artist", artist.get("name").asText())

                assertEquals(204, client.put("/api/v1/albums/$albumId/favorite") { bearerAuth(ownerToken) }.status.value)
                assertEquals(204, client.put("/api/v1/albums/$albumId/favorite") { bearerAuth(ownerToken) }.status.value)
                assertEquals(204, client.put("/api/v1/artists/$artistId/favorite") { bearerAuth(ownerToken) }.status.value)
                val favoriteAlbums = mapper.readTree(client.get("/api/v1/favorites/albums") {
                    bearerAuth(ownerToken)
                }.bodyAsText())
                assertEquals(1, favoriteAlbums.get("total").asInt())
                assertEquals(albumId, favoriteAlbums.get("items")[0].get("id").asText())
                val favoriteArtists = mapper.readTree(client.get("/api/v1/favorites/artists") {
                    bearerAuth(ownerToken)
                }.bodyAsText())
                assertEquals(1, favoriteArtists.get("total").asInt())
                assertEquals(artistId, favoriteArtists.get("items")[0].get("id").asText())
                assertEquals(0, mapper.readTree(client.get("/api/v1/favorites/albums") {
                    bearerAuth(otherToken)
                }.bodyAsText()).get("total").asInt())
                assertEquals(404, client.put("/api/v1/albums/$albumId/favorite") {
                    bearerAuth(otherToken)
                }.status.value)
                assertEquals(404, client.put("/api/v1/albums/${library.id}.bm9uZXhpc3RlbnQ/favorite") {
                    bearerAuth(ownerToken)
                }.status.value)
                assertEquals(204, client.delete("/api/v1/albums/$albumId/favorite") { bearerAuth(ownerToken) }.status.value)
                assertEquals(204, client.delete("/api/v1/albums/$albumId/favorite") { bearerAuth(ownerToken) }.status.value)
                assertEquals(204, client.delete("/api/v1/artists/$artistId/favorite") { bearerAuth(ownerToken) }.status.value)
                assertEquals(0, mapper.readTree(client.get("/api/v1/favorites/artists") {
                    bearerAuth(ownerToken)
                }.bodyAsText()).get("total").asInt())

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
