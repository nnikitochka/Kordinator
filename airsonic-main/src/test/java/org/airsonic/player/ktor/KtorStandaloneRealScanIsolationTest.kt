package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.KtorCoreMediaCatalogService
import org.airsonic.player.service.StandaloneLibraryScanService
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:real_scan_isolation_${home.fileName}"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 4
        })
        val runtime = KtorRuntimeFixture(home.resolve("runtime"), firstRoot, "real_scan_isolation_core_${home.fileName}")
        val previousAirsonicHome = System.getProperty("airsonic.home")

        try {
            System.setProperty("airsonic.home", home.resolve("runtime").toString())
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
                val mapper = jacksonObjectMapper()

                testApplication {
                    application {
                        standaloneModule(
                            authService = auth,
                            libraryScanService = scanService,
                            mediaFolderService = runtime.mediaFolderService,
                            mediaCatalogService = mediaCatalog,
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

                    val firstCatalog = client.get("/api/v1/tracks?libraryId=${firstLibrary.id}") {
                        bearerAuth(firstToken)
                    }
                    assertEquals(HttpStatusCode.OK, firstCatalog.status)
                    val firstTrack = mapper.readTree(firstCatalog.bodyAsText()).get("items").single()
                    assertTrue(firstTrack.get("relativePath").asText().endsWith("first.mp3"))

                    val secondCatalog = client.get("/api/v1/tracks?libraryId=$secondLibraryId") {
                        bearerAuth(secondToken)
                    }
                    assertEquals(HttpStatusCode.OK, secondCatalog.status)
                    val secondTrack = mapper.readTree(secondCatalog.bodyAsText()).get("items").single()
                    assertTrue(secondTrack.get("relativePath").asText().endsWith("second.mp3"))

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

                    Files.delete(firstRoot.resolve("first.mp3"))
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
            if (previousAirsonicHome == null) {
                System.clearProperty("airsonic.home")
            } else {
                System.setProperty("airsonic.home", previousAirsonicHome)
            }
        }
    }

    private fun copyAudioFixture(destination: Path): ByteArray {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/MEDIAS/piano.mp3")).use { it.readBytes() }
        Files.write(destination, bytes)
        return bytes
    }
}
