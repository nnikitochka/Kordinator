package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.StandaloneLibraryScanService
import org.airsonic.player.service.StandaloneLibraryService
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneScanTest {
    @TempDir
    lateinit var musicRoot: Path

    @Test
    fun scanIndexesTrackFallbackMetadataAndExposesPaginatedCatalog() {
        Files.writeString(musicRoot.resolve("01 - First Track.mp3"), "not a decodable audio file")
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_scan_api"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 4
        })

        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            val token = auth.login("admin", "a strong password for tests")!!.token
            val libraryService = StandaloneLibraryService(dataSource)
            val library = libraryService.create("Music", musicRoot)
            val scanService = StandaloneLibraryScanService(dataSource)
            val mapper = jacksonObjectMapper()

            testApplication {
                application { standaloneModule(auth, libraryService, scanService) }

                val started = client.post("/api/v1/admin/libraries/${library.id}/scan") { bearerAuth(token) }
                assertEquals(202, started.status.value)
                val scanId = mapper.readTree(started.bodyAsText()).get("id").asText()

                var state = "QUEUED"
                for (attempt in 0 until 100) {
                    val status = client.get("/api/v1/admin/libraries/${library.id}/scan") { bearerAuth(token) }
                    state = mapper.readTree(status.bodyAsText()).get("state").asText()
                    if (state == "COMPLETED" || state == "FAILED") break
                    delay(25)
                }
                assertEquals("COMPLETED", state, "scan $scanId should complete")

                val catalog = client.get("/api/v1/tracks?libraryId=${library.id}&q=first&limit=10&offset=0") {
                    bearerAuth(token)
                }
                assertEquals(200, catalog.status.value)
                val catalogBody = mapper.readTree(catalog.bodyAsText())
                val items = catalogBody.get("items")
                assertEquals(1, items.size())
                assertEquals(1, catalogBody.get("total").asInt())
                assertEquals("01 - First Track", items[0].get("title").asText())
                assertTrue(items[0].get("relativePath").asText().endsWith("01 - First Track.mp3"))

                Files.delete(musicRoot.resolve("01 - First Track.mp3"))
                val rescan = client.post("/api/v1/admin/libraries/${library.id}/scan") { bearerAuth(token) }
                assertEquals(202, rescan.status.value)
                for (attempt in 0 until 100) {
                    val status = client.get("/api/v1/admin/libraries/${library.id}/scan") { bearerAuth(token) }
                    state = mapper.readTree(status.bodyAsText()).get("state").asText()
                    if (state == "COMPLETED" || state == "FAILED") break
                    delay(25)
                }
                assertEquals("COMPLETED", state)
                val emptyCatalog = client.get("/api/v1/tracks?libraryId=${library.id}") { bearerAuth(token) }
                assertEquals(0, mapper.readTree(emptyCatalog.bodyAsText()).get("count").asInt())
                assertEquals(0, mapper.readTree(emptyCatalog.bodyAsText()).get("total").asInt())
            }
        }
    }
}
