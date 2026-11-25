package su.nnedition.kord.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import su.nnedition.kord.runtime.StandaloneDatabase
import su.nnedition.kord.security.LocalAuthService
import su.nnedition.kord.service.KtorCoreMediaCatalogService
import su.nnedition.kord.service.StandaloneFavoriteService
import su.nnedition.kord.service.library.scan.StandaloneLibraryScanService
import su.nnedition.kord.service.StandalonePlaylistService
import su.nnedition.kord.service.StandaloneRatingService
import su.nnedition.kord.service.StandaloneShareService
import org.junit.jupiter.api.io.TempDir
import su.nnedition.kord.api.configureApiRouting
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneRealScanIsolationTest {
    @TempDir
    lateinit var home: Path

    @Test
    fun realScannerIndexesTwoLibrariesAndUsersOnlyReadTheirAssignedLibrary() {
        val firstRoot = Files.createDirectory(home.resolve("first-library"))
        val secondRoot = Files.createDirectory(home.resolve("second-library"))
        val firstBytes = copyAudioFixture(firstRoot.resolve("first.mp3"))
        val secondBytes = copyAudioFixture(secondRoot.resolve("second.mp3"))
        val firstCover = ByteArrayOutputStream().use { output ->
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output)
            output.toByteArray()
        }
        Files.write(firstRoot.resolve("cover.png"), firstCover)
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:real_scan_isolation_${home.fileName}"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 4
        })
        val runtime = KtorRuntimeFixture(home.resolve("runtime"), firstRoot, "real_scan_isolation_core_${home.fileName}")
        val previousKordinatorHome = System.getProperty("kordinator.home")

        try {
            System.setProperty("kordinator.home", home.resolve("runtime").toString())
            dataSource.use {
                StandaloneDatabase.migrate(dataSource)
                val auth = LocalAuthService(dataSource)
                auth.createInitialAdmin("admin", "a strong password for tests")
                val firstUserId = auth.createUser("first-listener", "first listener password")
                val secondUserId = auth.createUser("second-listener", "second listener password")
                val adminToken = auth.login("admin", "a strong password for tests")!!.token
                val firstToken = auth.login("first-listener", "first listener password")!!.token
                val secondToken = auth.login("second-listener", "second listener password")!!.token
                val firstLibrary = runtime.mediaFolderService.getAllMusicFolders(true, true).single()
                val realServices = createRealMediaServices(runtime)
                val originalScanService = StandaloneLibraryScanService(
                    dataSource,
                    runtime.mediaFolderService,
                    realServices.mediaFileService,
                    realServices.mediaScannerService,
                )
                val interruptedScan = originalScanService.enqueue(firstLibrary.id!!.toLong())
                dataSource.connection.use { connection ->
                    connection.prepareStatement("UPDATE library_scan_job SET state = 'RUNNING' WHERE id = ?").use { statement ->
                        statement.setString(1, interruptedScan.id)
                        assertEquals(1, statement.executeUpdate())
                    }
                }
                val scanService = StandaloneLibraryScanService(
                    dataSource,
                    runtime.mediaFolderService,
                    realServices.mediaFileService,
                    realServices.mediaScannerService,
                )
                assertEquals("FAILED", scanService.latestScan(firstLibrary.id!!.toLong())?.state)
                assertTrue(scanService.latestScan(firstLibrary.id!!.toLong())?.finishedAt != null)
                val mediaCatalog = KtorCoreMediaCatalogService(runtime.coreDataSource, dataSource)
                val shareService = StandaloneShareService(dataSource, mediaCatalog)
                val mapper = jacksonObjectMapper()

                testApplication {
                    application {
                        configureApiRouting(
                            authService = auth,
                            libraryScanService = scanService,
                            mediaFolderService = runtime.mediaFolderService,
                            mediaCatalogService = mediaCatalog,
                            playlistService = StandalonePlaylistService(dataSource, mediaCatalog),
                            favoriteService = StandaloneFavoriteService(dataSource, mediaCatalog),
                            ratingService = StandaloneRatingService(dataSource, mediaCatalog),
                            shareService = shareService,
                        )
                    }

                    val created = client.post("/api/v1/admin/libraries") {
                        bearerAuth(adminToken)
                        contentType(ContentType.Application.Json)
                        setBody(mapper.writeValueAsString(mapOf("name" to "Second library", "path" to secondRoot.toString())))
                    }
                    assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
                    val secondLibraryId = mapper.readTree(created.bodyAsText()).get("id").asInt()
                    assertTrue(auth.setAllowedLibraries(firstUserId, setOf(firstLibrary.id!!.toLong())))
                    assertTrue(auth.setAllowedLibraries(secondUserId, setOf(secondLibraryId.toLong())))

                    val started = client.post("/api/v1/admin/libraries/${firstLibrary.id}/scan") {
                        bearerAuth(adminToken)
                    }
                    assertEquals(HttpStatusCode.Accepted, started.status, started.bodyAsText())
                    var state = "QUEUED"
                    for (attempt in 0 until 200) {
                        val status = client.get("/api/v1/admin/libraries/${firstLibrary.id}/scan") {
                            bearerAuth(adminToken)
                        }
                        state = mapper.readTree(status.bodyAsText()).get("state").asText()
                        if (state == "COMPLETED" || state == "FAILED") break
                        delay(50)
                    }
                    assertEquals("COMPLETED", state)
                    assertTrue(realServices.mediaScannerService.scanCount > 0)

                    runtime.coreDataSource.connection.use { connection ->
                        connection.prepareStatement(
                            "UPDATE media_file SET title = ?, artist = ?, album_artist = ?, album = ?, track_number = ?, " +
                                "genre = ?, year = ?, play_count = ?, last_played = ?, created = ? " +
                                "WHERE path = ? AND folder_id = ?",
                        ).use { statement ->
                            listOf(
                                CatalogFixture(firstLibrary.id!!, "first.mp3", "Rock", 2000, 1, "2020-01-01T00:00:00Z", "2022-01-01T00:00:00Z"),
                                CatalogFixture(secondLibraryId, "second.mp3", "Jazz", 2020, 10, "2024-01-01T00:00:00Z", "2025-01-01T00:00:00Z"),
                            ).forEach { fixture ->
                                statement.setString(1, "Shared Title")
                                statement.setString(2, "Shared Artist")
                                statement.setString(3, "Shared Artist")
                                statement.setString(4, "Shared Album")
                                statement.setInt(5, 1)
                                statement.setString(6, fixture.genre)
                                statement.setInt(7, fixture.year)
                                statement.setInt(8, fixture.playCount)
                                statement.setTimestamp(9, java.sql.Timestamp.from(java.time.Instant.parse(fixture.lastPlayed)))
                                statement.setTimestamp(10, java.sql.Timestamp.from(java.time.Instant.parse(fixture.created)))
                                statement.setString(11, fixture.path)
                                statement.setInt(12, fixture.libraryId)
                                assertEquals(1, statement.executeUpdate())
                            }
                        }
                    }

                    val firstCatalog = client.get("/api/v1/tracks?libraryId=${firstLibrary.id}") {
                        bearerAuth(firstToken)
                    }
                    assertEquals(HttpStatusCode.OK, firstCatalog.status)
                    val firstTrack = mapper.readTree(firstCatalog.bodyAsText()).get("items").single()
                    assertTrue(firstTrack.get("relativePath").asText().endsWith("first.mp3"))
                    val firstTrackId = firstTrack.get("id").asLong()
                    val createdShare = client.post("/api/v1/shares") {
                        bearerAuth(firstToken)
                        contentType(ContentType.Application.Json)
                        setBody(mapper.writeValueAsString(mapOf("trackIds" to listOf(firstTrackId), "description" to "One track")))
                    }
                    assertEquals(HttpStatusCode.Created, createdShare.status, createdShare.bodyAsText())
                    val shareJson = mapper.readTree(createdShare.bodyAsText())
                    val shareId = shareJson.get("id").asLong()
                    val shareToken = shareJson.get("token").asText()
                    dataSource.connection.use { connection ->
                        connection.prepareStatement("SELECT token_hash FROM shared_link WHERE id = ?").use { statement ->
                            statement.setLong(1, shareId)
                            statement.executeQuery().use { rows ->
                                assertTrue(rows.next())
                                assertTrue(rows.getString(1) != shareToken)
                            }
                        }
                    }
                    assertEquals(0, mapper.readTree(client.get("/api/v1/shares") { bearerAuth(secondToken) }.bodyAsText()).size())
                    val publicShare = client.get("/api/v1/public/shares/$shareToken")
                    assertEquals(HttpStatusCode.OK, publicShare.status, publicShare.bodyAsText())
                    assertEquals(1, mapper.readTree(publicShare.bodyAsText()).get("tracks").size())
                    val publicStream = client.get("/api/v1/public/shares/$shareToken/tracks/$firstTrackId/stream")
                    assertEquals(HttpStatusCode.OK, publicStream.status, publicStream.bodyAsText())
                    assertTrue(firstBytes.contentEquals(publicStream.bodyAsBytes()))
                    assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/public/shares/$shareToken/tracks/${firstTrackId + 1}/stream").status)
                    val ownerShares = mapper.readTree(client.get("/api/v1/shares") { bearerAuth(firstToken) }.bodyAsText())
                    assertEquals(firstTrackId, ownerShares[0].get("trackIds")[0].asLong())
                    val foreignDelete = client.delete("/api/v1/shares/$shareId") { bearerAuth(secondToken) }
                    assertEquals(HttpStatusCode.NotFound, foreignDelete.status, foreignDelete.bodyAsText())
                    dataSource.connection.use { connection ->
                        connection.prepareStatement("UPDATE shared_link SET expires_at = TIMESTAMP '2000-01-01 00:00:00' WHERE id = ?").use { statement ->
                            statement.setLong(1, shareId)
                            assertEquals(1, statement.executeUpdate())
                        }
                    }
                    assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/public/shares/$shareToken").status)
                    assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/shares/$shareId") { bearerAuth(firstToken) }.status)
                    assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/public/shares/$shareToken").status)
                    assertEquals(HttpStatusCode.OK, client.get("/api/v1/tracks/$firstTrackId") {
                        bearerAuth(firstToken)
                    }.status)
                    assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/tracks/$firstTrackId") {
                        bearerAuth(secondToken)
                    }.status)
                    val firstArtwork = client.get("/api/v1/tracks/$firstTrackId/artwork") { bearerAuth(firstToken) }
                    assertEquals(HttpStatusCode.OK, firstArtwork.status)
                    assertTrue(firstCover.contentEquals(firstArtwork.bodyAsBytes()))
                    val firstArtworkHead = client.head("/api/v1/tracks/$firstTrackId/artwork") { bearerAuth(firstToken) }
                    assertEquals(HttpStatusCode.OK, firstArtworkHead.status)
                    assertTrue(firstArtworkHead.bodyAsBytes().isEmpty())
                    assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/tracks/$firstTrackId/artwork") {
                        bearerAuth(secondToken)
                    }.status)
                    val playlistResponse = client.post("/api/v1/playlists") {
                        bearerAuth(firstToken)
                        contentType(ContentType.Application.Json)
                        setBody(mapper.writeValueAsString(mapOf("name" to "Artwork playlist", "trackIds" to listOf(firstTrackId))))
                    }
                    assertEquals(HttpStatusCode.Created, playlistResponse.status, playlistResponse.bodyAsText())
                    val playlistId = mapper.readTree(playlistResponse.bodyAsText()).get("id").asLong()
                    val playlistArtwork = client.get("/api/v1/playlists/$playlistId/artwork") { bearerAuth(firstToken) }
                    assertEquals(HttpStatusCode.OK, playlistArtwork.status)
                    assertTrue(firstCover.contentEquals(playlistArtwork.bodyAsBytes()))
                    assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/playlists/$playlistId/artwork") {
                        bearerAuth(secondToken)
                    }.status)
                    val firstTrackRange = client.get("/api/v1/tracks/$firstTrackId/stream") {
                        bearerAuth(firstToken)
                        headers.append("Range", "bytes=0-5")
                    }
                    assertEquals(HttpStatusCode.PartialContent, firstTrackRange.status)
                    assertEquals("bytes 0-5/${firstBytes.size}", firstTrackRange.headers["Content-Range"])
                    assertTrue(firstBytes.copyOfRange(0, 6).contentEquals(firstTrackRange.bodyAsBytes()))

                    val secondCatalog = client.get("/api/v1/tracks?libraryId=$secondLibraryId") {
                        bearerAuth(secondToken)
                    }
                    assertEquals(HttpStatusCode.OK, secondCatalog.status)
                    val secondTrack = mapper.readTree(secondCatalog.bodyAsText()).get("items").single()
                    assertTrue(secondTrack.get("relativePath").asText().endsWith("second.mp3"))

                    assertCatalogPagination(
                        client,
                        mapper,
                        adminToken,
                        firstToken,
                        secondToken,
                        firstLibrary.id!!.toLong(),
                        firstTrack.get("title").asText(),
                    )

                    assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/tracks?libraryId=$secondLibraryId") {
                        bearerAuth(firstToken)
                    }.status)
                    assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/tracks?libraryId=${firstLibrary.id}") {
                        bearerAuth(secondToken)
                    }.status)

                    val repeatedScan = client.post("/api/v1/admin/libraries/${firstLibrary.id}/scan") {
                        bearerAuth(adminToken)
                    }
                    assertEquals(HttpStatusCode.Accepted, repeatedScan.status)
                    var repeatState = "QUEUED"
                    for (attempt in 0 until 200) {
                        repeatState = mapper.readTree(client.get("/api/v1/admin/libraries/${firstLibrary.id}/scan") {
                            bearerAuth(adminToken)
                        }.bodyAsText()).get("state").asText()
                        if (repeatState == "COMPLETED" || repeatState == "FAILED") break
                        delay(50)
                    }
                    assertEquals("COMPLETED", repeatState)
                    val repeatedCatalog = client.get("/api/v1/tracks?libraryId=${firstLibrary.id}") {
                        bearerAuth(firstToken)
                    }
                    val repeatedItems = mapper.readTree(repeatedCatalog.bodyAsText()).get("items")
                    assertEquals(1, repeatedItems.size(), "repeat scan must not duplicate tracks")
                    assertEquals(firstTrack.get("id").asLong(), repeatedItems[0].get("id").asLong())

                    assertFavoritesSurviveRescanAndLibraryAccessChanges(
                        client, mapper, auth, adminToken, firstToken, firstUserId, firstLibrary.id!!.toLong(),
                    )

                    val executor = Executors.newFixedThreadPool(2)
                    val ready = CountDownLatch(2)
                    val startTogether = CountDownLatch(1)
                    val concurrentEnqueues = try {
                        (1..2).map {
                            executor.submit<Boolean> {
                                ready.countDown()
                                check(startTogether.await(5, TimeUnit.SECONDS))
                                try {
                                    scanService.enqueue(secondLibraryId.toLong())
                                    true
                                } catch (_: IllegalArgumentException) {
                                    false
                                }
                            }
                        }.also {
                            assertTrue(ready.await(5, TimeUnit.SECONDS))
                            startTogether.countDown()
                        }.map { it.get(5, TimeUnit.SECONDS) }
                    } finally {
                        executor.shutdownNow()
                    }
                    assertEquals(1, concurrentEnqueues.count { it }, "only one simultaneous scan enqueue may win")
                    val duplicateScan = client.post("/api/v1/admin/libraries/$secondLibraryId/scan") {
                        bearerAuth(adminToken)
                    }
                    assertEquals(HttpStatusCode.Conflict, duplicateScan.status)
                    scanService.run(requireNotNull(scanService.latestScan(secondLibraryId.toLong())).id)
                    assertEquals("COMPLETED", scanService.latestScan(secondLibraryId.toLong())?.state)

                    val missingRoot = Files.createDirectory(home.resolve("removed-library"))
                    val missingLibraryResponse = client.post("/api/v1/admin/libraries") {
                        bearerAuth(adminToken)
                        contentType(ContentType.Application.Json)
                        setBody(mapper.writeValueAsString(mapOf("name" to "Removed library", "path" to missingRoot.toString())))
                    }
                    assertEquals(HttpStatusCode.Created, missingLibraryResponse.status)
                    val missingLibraryId = mapper.readTree(missingLibraryResponse.bodyAsText()).get("id").asInt()
                    Files.delete(missingRoot)
                    val missingScan = client.post("/api/v1/admin/libraries/$missingLibraryId/scan") {
                        bearerAuth(adminToken)
                    }
                    assertEquals(HttpStatusCode.Accepted, missingScan.status)
                    var missingScanState = "QUEUED"
                    for (attempt in 0 until 100) {
                        val status = client.get("/api/v1/admin/libraries/$missingLibraryId/scan") {
                            bearerAuth(adminToken)
                        }
                        val body = mapper.readTree(status.bodyAsText())
                        missingScanState = body.get("state").asText()
                        if (missingScanState == "COMPLETED" || missingScanState == "FAILED") {
                            assertTrue(body.get("errorCount").asLong() > 0)
                            break
                        }
                        delay(25)
                    }
                    assertEquals("FAILED", missingScanState)

                    val firstStream = client.get("/api/v1/tracks/${firstTrack.get("id").asLong()}/stream") {
                        bearerAuth(firstToken)
                    }
                    assertEquals(HttpStatusCode.OK, firstStream.status)
                    assertTrue(firstBytes.contentEquals(firstStream.bodyAsBytes()))
                    val secondStream = client.get("/api/v1/tracks/${secondTrack.get("id").asLong()}/stream") {
                        bearerAuth(secondToken)
                    }
                    assertEquals(HttpStatusCode.OK, secondStream.status)
                    assertTrue(secondBytes.contentEquals(secondStream.bodyAsBytes()))
                    assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/tracks/${secondTrack.get("id").asLong()}") {
                        bearerAuth(firstToken)
                    }.status)

                    Files.write(firstRoot.resolve("first.mp3"), byteArrayOf(0), StandardOpenOption.APPEND)
                    val changedScan = client.post("/api/v1/admin/libraries/${firstLibrary.id}/scan") {
                        bearerAuth(adminToken)
                    }
                    assertEquals(HttpStatusCode.Accepted, changedScan.status)
                    var changedState = "QUEUED"
                    for (attempt in 0 until 200) {
                        changedState = mapper.readTree(client.get("/api/v1/admin/libraries/${firstLibrary.id}/scan") {
                            bearerAuth(adminToken)
                        }.bodyAsText()).get("state").asText()
                        if (changedState == "COMPLETED" || changedState == "FAILED") break
                        delay(50)
                    }
                    assertEquals("COMPLETED", changedState)
                    val changedTrack = mapper.readTree(client.get("/api/v1/tracks?libraryId=${firstLibrary.id}") {
                        bearerAuth(firstToken)
                    }.bodyAsText()).get("items").single()
                    assertEquals(firstTrack.get("id").asLong(), changedTrack.get("id").asLong())
                    assertEquals(firstBytes.size + 1L, changedTrack.get("sizeBytes").asLong())

                    val renamedPath = firstRoot.resolve("renamed.mp3")
                    Files.move(firstRoot.resolve("first.mp3"), renamedPath, StandardCopyOption.ATOMIC_MOVE)
                    val renamedScan = client.post("/api/v1/admin/libraries/${firstLibrary.id}/scan") {
                        bearerAuth(adminToken)
                    }
                    assertEquals(HttpStatusCode.Accepted, renamedScan.status)
                    var renamedState = "QUEUED"
                    for (attempt in 0 until 200) {
                        renamedState = mapper.readTree(client.get("/api/v1/admin/libraries/${firstLibrary.id}/scan") {
                            bearerAuth(adminToken)
                        }.bodyAsText()).get("state").asText()
                        if (renamedState == "COMPLETED" || renamedState == "FAILED") break
                        delay(50)
                    }
                    assertEquals("COMPLETED", renamedState)
                    val renamedTracks = mapper.readTree(client.get("/api/v1/tracks?libraryId=${firstLibrary.id}") {
                        bearerAuth(firstToken)
                    }.bodyAsText()).get("items")
                    assertEquals(1, renamedTracks.size())
                    assertTrue(renamedTracks[0].get("relativePath").asText().endsWith("renamed.mp3"))
                    assertTrue(renamedTracks[0].get("id").asLong() != changedTrack.get("id").asLong())
                    assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/tracks/${changedTrack.get("id").asLong()}") {
                        bearerAuth(firstToken)
                    }.status)

                    Files.delete(renamedPath)
                    val deletedScan = client.post("/api/v1/admin/libraries/${firstLibrary.id}/scan") {
                        bearerAuth(adminToken)
                    }
                    assertEquals(HttpStatusCode.Accepted, deletedScan.status)
                    var deletedState = "QUEUED"
                    for (attempt in 0 until 200) {
                        deletedState = mapper.readTree(client.get("/api/v1/admin/libraries/${firstLibrary.id}/scan") {
                            bearerAuth(adminToken)
                        }.bodyAsText()).get("state").asText()
                        if (deletedState == "COMPLETED" || deletedState == "FAILED") break
                        delay(50)
                    }
                    assertEquals("COMPLETED", deletedState)
                    val afterDeletion = client.get("/api/v1/tracks?libraryId=${firstLibrary.id}") {
                        bearerAuth(firstToken)
                    }
                    assertEquals(0, mapper.readTree(afterDeletion.bodyAsText()).get("total").asInt())
                    assertEquals(1, mapper.readTree(client.get("/api/v1/tracks?libraryId=$secondLibraryId") {
                        bearerAuth(secondToken)
                    }.bodyAsText()).get("total").asInt())
                }
            }
        } finally {
            runtime.close()
            if (previousKordinatorHome == null) {
                System.clearProperty("kordinator.home")
            } else {
                System.setProperty("kordinator.home", previousKordinatorHome)
            }
        }
    }

    private fun copyAudioFixture(destination: Path): ByteArray {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/MEDIAS/piano.mp3")).use { it.readBytes() }
        Files.write(destination, bytes)
        return bytes
    }

    private data class CatalogFixture(
        val libraryId: Int,
        val path: String,
        val genre: String,
        val year: Int,
        val playCount: Int,
        val lastPlayed: String,
        val created: String,
    )

    private suspend fun assertCatalogPagination(
        client: HttpClient,
        mapper: ObjectMapper,
        adminToken: String,
        firstToken: String,
        secondToken: String,
        firstLibraryId: Long,
        sharedTitle: String,
    ) {
        val trackPage0 = mapper.readTree(client.get("/api/v1/tracks?limit=1&offset=0") {
            bearerAuth(adminToken)
        }.bodyAsText())
        val trackPage1 = mapper.readTree(client.get("/api/v1/tracks?limit=1&offset=1") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(2, trackPage0.get("total").asInt())
        assertEquals(1, trackPage0.get("count").asInt())
        assertEquals(1, trackPage1.get("count").asInt())
        assertTrue(trackPage0.get("items")[0].get("id").asLong() != trackPage1.get("items")[0].get("id").asLong())
        val repeatedTrackPage0 = mapper.readTree(client.get("/api/v1/tracks?limit=1&offset=0") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(trackPage0.get("items")[0].get("id").asLong(), repeatedTrackPage0.get("items")[0].get("id").asLong())

        val allArtists = mapper.readTree(client.get("/api/v1/artists?limit=10") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(2, allArtists.get("total").asInt())
        assertEquals(allArtists.get("items")[0].get("name").asText(), allArtists.get("items")[1].get("name").asText())
        assertTrue(allArtists.get("items")[0].get("id").asText() != allArtists.get("items")[1].get("id").asText())
        val prefixArtists = mapper.readTree(client.get("/api/v1/artists?prefix=Shared&limit=10") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(2, prefixArtists.get("total").asInt())
        val absentPrefixArtists = mapper.readTree(client.get("/api/v1/artists?prefix=Missing") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(0, absentPrefixArtists.get("total").asInt())
        val wildcardPrefixArtists = mapper.readTree(client.get("/api/v1/artists?prefix=%25") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(0, wildcardPrefixArtists.get("total").asInt())
        val firstArtist = allArtists.get("items").first { it.get("libraryId").asLong() == firstLibraryId }
        val artistDetailsResponse = client.get("/api/v1/artists/${firstArtist.get("id").asText()}") {
            bearerAuth(firstToken)
        }
        assertEquals(HttpStatusCode.OK, artistDetailsResponse.status)
        val artistDetails = mapper.readTree(artistDetailsResponse.bodyAsText())
        assertTrue(artistDetails.get("tracks").size() > 0)
        assertEquals(artistDetails.get("albumCount").asInt(), artistDetails.get("albums").size())
        assertTrue(artistDetails.get("albums").size() > 0)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/artists/${firstArtist.get("id").asText()}") {
            bearerAuth(secondToken)
        }.status)
        val artistArtwork = client.get("/api/v1/artists/${firstArtist.get("id").asText()}/artwork") {
            bearerAuth(firstToken)
        }
        assertEquals(HttpStatusCode.OK, artistArtwork.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/artists/${firstArtist.get("id").asText()}/artwork") {
            bearerAuth(secondToken)
        }.status)
        val artistPage0 = mapper.readTree(client.get("/api/v1/artists?limit=1&offset=0") {
            bearerAuth(adminToken)
        }.bodyAsText()).get("items")[0]
        val artistPage1 = mapper.readTree(client.get("/api/v1/artists?limit=1&offset=1") {
            bearerAuth(adminToken)
        }.bodyAsText()).get("items")[0]
        assertTrue(artistPage0.get("id").asText() != artistPage1.get("id").asText())
        assertEquals(1, mapper.readTree(client.get("/api/v1/artists") {
            bearerAuth(firstToken)
        }.bodyAsText()).get("total").asInt())

        val allAlbums = mapper.readTree(client.get("/api/v1/albums?limit=10") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(2, allAlbums.get("total").asInt())
        assertEquals(allAlbums.get("items")[0].get("name").asText(), allAlbums.get("items")[1].get("name").asText())
        assertTrue(allAlbums.get("items")[0].get("id").asText() != allAlbums.get("items")[1].get("id").asText())
        val secondLibraryId = allAlbums.get("items").first { it.get("libraryId").asLong() != firstLibraryId }
            .get("libraryId").asLong()
        val rockAlbums = mapper.readTree(client.get("/api/v1/albums?genre=Rock") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(1, rockAlbums.get("total").asInt())
        assertEquals("Rock", rockAlbums.get("items")[0].get("genre").asText())
        assertEquals(0, mapper.readTree(client.get("/api/v1/albums?genre=Jazz") {
            bearerAuth(firstToken)
        }.bodyAsText()).get("total").asInt())
        val byYear = mapper.readTree(client.get("/api/v1/albums?sort=year&fromYear=1990&toYear=2025") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(2000, byYear.get("items")[0].get("year").asInt())
        assertEquals(2020, byYear.get("items")[1].get("year").asInt())
        val mostPlayed = mapper.readTree(client.get("/api/v1/albums?sort=most-played") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(secondLibraryId, mostPlayed.get("items")[0].get("libraryId").asLong())
        val recentlyPlayed = mapper.readTree(client.get("/api/v1/albums?sort=recently-played") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(secondLibraryId, recentlyPlayed.get("items")[0].get("libraryId").asLong())
        val newest = mapper.readTree(client.get("/api/v1/albums?sort=newest") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(secondLibraryId, newest.get("items")[0].get("libraryId").asLong())
        val randomAlbums = mapper.readTree(client.get("/api/v1/albums?sort=random") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(2, randomAlbums.get("total").asInt())
        assertEquals(2, randomAlbums.get("items").size())
        val firstAlbumId = allAlbums.get("items").first { it.get("libraryId").asLong() == firstLibraryId }
            .get("id").asText()
        val secondAlbumId = allAlbums.get("items").first { it.get("libraryId").asLong() == secondLibraryId }
            .get("id").asText()
        assertEquals(HttpStatusCode.NoContent, client.put("/api/v1/albums/$firstAlbumId/rating") {
            bearerAuth(firstToken)
            contentType(ContentType.Application.Json)
            setBody(mapper.writeValueAsString(mapOf("rating" to 5)))
        }.status)
        assertEquals(HttpStatusCode.NoContent, client.put("/api/v1/albums/$secondAlbumId/rating") {
            bearerAuth(secondToken)
            contentType(ContentType.Application.Json)
            setBody(mapper.writeValueAsString(mapOf("rating" to 1)))
        }.status)
        assertEquals(5, mapper.readTree(client.get("/api/v1/albums/$firstAlbumId/rating") {
            bearerAuth(firstToken)
        }.bodyAsText()).get("rating").asInt())
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/albums/$firstAlbumId/rating") {
            bearerAuth(secondToken)
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.put("/api/v1/albums/$firstAlbumId/rating") {
            bearerAuth(secondToken)
            contentType(ContentType.Application.Json)
            setBody(mapper.writeValueAsString(mapOf("rating" to 1)))
        }.status)
        assertEquals(HttpStatusCode.BadRequest, client.put("/api/v1/albums/$firstAlbumId/rating") {
            bearerAuth(firstToken)
            contentType(ContentType.Application.Json)
            setBody(mapper.writeValueAsString(mapOf("rating" to 6)))
        }.status)
        val highestRated = mapper.readTree(client.get("/api/v1/albums?sort=highest-rated") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(firstLibraryId, highestRated.get("items")[0].get("libraryId").asLong())
        assertEquals(HttpStatusCode.NoContent, client.put("/api/v1/albums/$firstAlbumId/rating") {
            bearerAuth(firstToken)
            contentType(ContentType.Application.Json)
            setBody(mapper.writeValueAsString(mapOf("rating" to 0)))
        }.status)
        assertTrue(mapper.readTree(client.get("/api/v1/albums/$firstAlbumId/rating") {
            bearerAuth(firstToken)
        }.bodyAsText()).get("rating").isNull)
        assertEquals(400, client.get("/api/v1/albums?sort=unknown") { bearerAuth(adminToken) }.status.value)
        assertEquals(400, client.get("/api/v1/albums?fromYear=2000") { bearerAuth(adminToken) }.status.value)
        assertEquals(400, client.get("/api/v1/albums?fromYear=2025&toYear=2000") { bearerAuth(adminToken) }.status.value)
        val firstAlbum = allAlbums.get("items").first { it.get("libraryId").asLong() == firstLibraryId }
        val albumDetailsResponse = client.get("/api/v1/albums/${firstAlbum.get("id").asText()}") {
            bearerAuth(firstToken)
        }
        assertEquals(HttpStatusCode.OK, albumDetailsResponse.status)
        val albumDetails = mapper.readTree(albumDetailsResponse.bodyAsText())
        assertTrue(albumDetails.get("trackCount").asInt() > 0)
        assertEquals(albumDetails.get("trackCount").asInt(), albumDetails.get("tracks").size())
        assertTrue(albumDetails.has("year"))
        assertTrue(albumDetails.has("genre"))
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/albums/${firstAlbum.get("id").asText()}") {
            bearerAuth(secondToken)
        }.status)
        val albumArtwork = client.get("/api/v1/albums/${firstAlbum.get("id").asText()}/artwork") {
            bearerAuth(firstToken)
        }
        assertEquals(HttpStatusCode.OK, albumArtwork.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/albums/${firstAlbum.get("id").asText()}/artwork") {
            bearerAuth(secondToken)
        }.status)
        val albumPage0 = mapper.readTree(client.get("/api/v1/albums?limit=1&offset=0") {
            bearerAuth(adminToken)
        }.bodyAsText()).get("items")[0]
        val albumPage1 = mapper.readTree(client.get("/api/v1/albums?limit=1&offset=1") {
            bearerAuth(adminToken)
        }.bodyAsText()).get("items")[0]
        assertTrue(albumPage0.get("id").asText() != albumPage1.get("id").asText())
        assertEquals(1, mapper.readTree(client.get("/api/v1/albums") {
            bearerAuth(firstToken)
        }.bodyAsText()).get("total").asInt())

        val searchQuery = URLEncoder.encode(sharedTitle, StandardCharsets.UTF_8)
        val searchPage0 = mapper.readTree(client.get("/api/v1/search?query=$searchQuery&limit=1&offset=0") {
            bearerAuth(adminToken)
        }.bodyAsText())
        val searchPage1 = mapper.readTree(client.get("/api/v1/search?query=$searchQuery&limit=1&offset=1") {
            bearerAuth(adminToken)
        }.bodyAsText())
        assertEquals(2, searchPage0.get("totalHits").asInt())
        assertEquals(1, searchPage0.get("tracks").size())
        assertEquals(1, searchPage1.get("tracks").size())
        assertTrue(searchPage0.get("tracks")[0].get("id").asLong() != searchPage1.get("tracks")[0].get("id").asLong())
    }

    private suspend fun assertFavoritesSurviveRescanAndLibraryAccessChanges(
        client: HttpClient,
        mapper: ObjectMapper,
        auth: LocalAuthService,
        adminToken: String,
        userToken: String,
        userId: Long,
        libraryId: Long,
    ) {
        val firstAlbum = mapper.readTree(client.get("/api/v1/albums") { bearerAuth(adminToken) }.bodyAsText())
            .get("items").first { it.get("libraryId").asLong() == libraryId }
        val firstArtist = mapper.readTree(client.get("/api/v1/artists") { bearerAuth(adminToken) }.bodyAsText())
            .get("items").first { it.get("libraryId").asLong() == libraryId }
        assertEquals(HttpStatusCode.NoContent, client.put("/api/v1/albums/${firstAlbum.get("id").asText()}/favorite") {
            bearerAuth(userToken)
        }.status)
        assertEquals(HttpStatusCode.NoContent, client.put("/api/v1/artists/${firstArtist.get("id").asText()}/favorite") {
            bearerAuth(userToken)
        }.status)
        assertEquals(1, favoriteTotal(client, mapper, userToken, "albums"))
        assertEquals(1, favoriteTotal(client, mapper, userToken, "artists"))

        val scanResponse = client.post("/api/v1/admin/libraries/$libraryId/scan") { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.Accepted, scanResponse.status)
        var scanState = "QUEUED"
        for (attempt in 0 until 200) {
            scanState = mapper.readTree(client.get("/api/v1/admin/libraries/$libraryId/scan") {
                bearerAuth(adminToken)
            }.bodyAsText()).get("state").asText()
            if (scanState == "COMPLETED" || scanState == "FAILED") break
            delay(50)
        }
        assertEquals("COMPLETED", scanState)
        assertEquals(1, favoriteTotal(client, mapper, userToken, "albums"), "album favorite must survive a library rescan")
        assertEquals(1, favoriteTotal(client, mapper, userToken, "artists"), "artist favorite must survive a library rescan")

        assertTrue(auth.setAllowedLibraries(userId, emptySet()))
        assertEquals(0, favoriteTotal(client, mapper, userToken, "albums"), "album favorite must be hidden when library access is revoked")
        assertEquals(0, favoriteTotal(client, mapper, userToken, "artists"), "artist favorite must be hidden when library access is revoked")
        assertTrue(auth.setAllowedLibraries(userId, setOf(libraryId)))
        assertEquals(1, favoriteTotal(client, mapper, userToken, "albums"), "album favorite must return when library access is restored")
        assertEquals(1, favoriteTotal(client, mapper, userToken, "artists"), "artist favorite must return when library access is restored")
    }

    private suspend fun favoriteTotal(client: HttpClient, mapper: ObjectMapper, token: String, type: String): Int =
        mapper.readTree(client.get("/api/v1/favorites/$type") { bearerAuth(token) }.bodyAsText()).get("total").asInt()
}
