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
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaScannerService
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneLibraryCreationTest {
    @TempDir
    lateinit var home: Path

    @Test
    fun adminCreatesLibraryScansItAndStreamsItsTrack() {
        val musicRoot = Files.createDirectory(home.resolve("default-library"))
        val createdRoot = Files.createTempDirectory(home, "created-library-")
        val relativePath = "new-track.mp3"
        val trackBytes = "created library stream bytes".toByteArray()
        Files.write(createdRoot.resolve(relativePath), trackBytes)

        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:library_creation_api_${home.fileName}"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 4
        })
        val runtime = KtorRuntimeFixture(home.resolve("runtime"), musicRoot, "library_creation_core_${home.fileName}")

        try {
            dataSource.use {
                StandaloneDatabase.migrate(dataSource)
                val auth = LocalAuthService(dataSource)
                auth.createInitialAdmin("admin", "a strong password for tests")
                val token = auth.login("admin", "a strong password for tests")!!.token
                val mediaFiles = Mockito.mock(MediaFileService::class.java)
                val mediaScanner = Mockito.mock(MediaScannerService::class.java)
                val scanService = StandaloneLibraryScanService(
                    dataSource,
                    runtime.mediaFolderService,
                    mediaFiles,
                    mediaScanner,
                )
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
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody(mapper.writeValueAsString(mapOf("name" to "Created through API", "path" to createdRoot.toString())))
                    }
                    assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
                    val libraryId = mapper.readTree(created.bodyAsText()).get("id").asInt()
                    val folder = requireNotNull(runtime.mediaFolderService.getMusicFolderById(libraryId, true, true))

                    mockCoreCatalog(
                        runtime,
                        folder,
                        relativePath,
                        "New Track",
                        trackBytes.size.toLong(),
                        mediaScanner,
                        mediaFiles,
                    )

                    val started = client.post("/api/v1/admin/libraries/$libraryId/scan") { bearerAuth(token) }
                    assertEquals(HttpStatusCode.Accepted, started.status, started.bodyAsText())
                    var state = "QUEUED"
                    for (attempt in 0 until 100) {
                        val status = client.get("/api/v1/admin/libraries/$libraryId/scan") { bearerAuth(token) }
                        state = mapper.readTree(status.bodyAsText()).get("state").asText()
                        if (state == "COMPLETED" || state == "FAILED") break
                        delay(25)
                    }
                    assertEquals("COMPLETED", state)
                    Mockito.verify(mediaScanner).scanLibrary()

                    val catalog = client.get("/api/v1/tracks?libraryId=$libraryId") { bearerAuth(token) }
                    assertEquals(HttpStatusCode.OK, catalog.status)
                    val trackId = mapper.readTree(catalog.bodyAsText()).get("items")[0].get("id").asLong()
                    val stream = client.get("/api/v1/tracks/$trackId/stream") { bearerAuth(token) }
                    assertEquals(HttpStatusCode.OK, stream.status)
                    assertTrue(trackBytes.contentEquals(stream.bodyAsBytes()))
                }
            }
        } finally {
            runtime.close()
        }
    }
}
