package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsBytes
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
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneScanTest {
    @TempDir
    lateinit var musicRoot: Path

    @Test
    fun scanIndexesTrackFallbackMetadataAndExposesPaginatedCatalog() {
        Files.writeString(musicRoot.resolve("01 - First Track.mp3"), "not a decodable audio file")
        val coverBytes = ByteArrayOutputStream().use { output ->
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output)
            output.toByteArray()
        }
        Files.write(musicRoot.resolve("cover.png"), coverBytes)
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
                assertEquals(401, client.get("/api/v1/tracks").status.value)
                val catalogBody = mapper.readTree(catalog.bodyAsText())
                val items = catalogBody.get("items")
                assertEquals(1, items.size())
                assertEquals(1, catalogBody.get("total").asInt())
                assertEquals("01 - First Track", items[0].get("title").asText())
                assertTrue(items[0].get("relativePath").asText().endsWith("01 - First Track.mp3"))

                val search = client.get("/api/v1/search?query=first&limit=1") { bearerAuth(token) }
                assertEquals(200, search.status.value)
                val searchBody = mapper.readTree(search.bodyAsText())
                assertEquals(1, searchBody.get("totalHits").asInt())
                assertEquals(items[0].get("id").asLong(), searchBody.get("tracks")[0].get("id").asLong())
                assertEquals(401, client.get("/api/v1/search?query=first").status.value)
                assertEquals(400, client.get("/api/v1/search?query=first&limit=0") { bearerAuth(token) }.status.value)

                val trackId = items[0].get("id").asLong()
                val trackDetails = client.get("/api/v1/tracks/$trackId") { bearerAuth(token) }
                assertEquals(200, trackDetails.status.value)
                assertEquals(trackId, mapper.readTree(trackDetails.bodyAsText()).get("id").asLong())
                assertEquals(401, client.get("/api/v1/tracks/$trackId").status.value)
                assertEquals(404, client.get("/api/v1/tracks/999999") { bearerAuth(token) }.status.value)
                assertEquals(400, client.get("/api/v1/tracks/-1") { bearerAuth(token) }.status.value)
                val fullStream = client.get("/api/v1/tracks/$trackId/stream") { bearerAuth(token) }
                assertEquals(200, fullStream.status.value, fullStream.bodyAsText())
                assertEquals(401, client.get("/api/v1/tracks/$trackId/stream").status.value)
                assertEquals("bytes", fullStream.headers["Accept-Ranges"])
                assertTrue("not a decodable audio file".toByteArray().contentEquals(fullStream.bodyAsBytes()))

                val artwork = client.get("/api/v1/tracks/$trackId/artwork") { bearerAuth(token) }
                assertEquals(200, artwork.status.value)
                assertEquals("image/png", artwork.headers["Content-Type"]?.substringBefore(';'))
                assertTrue(coverBytes.contentEquals(artwork.bodyAsBytes()))
                assertEquals(401, client.get("/api/v1/tracks/$trackId/artwork").status.value)
                assertEquals(404, client.get("/api/v1/tracks/999999/artwork") { bearerAuth(token) }.status.value)

                val partialStream = client.get("/api/v1/tracks/$trackId/stream") {
                    bearerAuth(token)
                    headers.append("Range", "bytes=2-5")
                }
                assertEquals(206, partialStream.status.value)
                assertEquals("bytes 2-5/26", partialStream.headers["Content-Range"])
                assertTrue("t a ".toByteArray().contentEquals(partialStream.bodyAsBytes()))

                val invalidRange = client.get("/api/v1/tracks/$trackId/stream") {
                    bearerAuth(token)
                    headers.append("Range", "bytes=999-")
                }
                assertEquals(416, invalidRange.status.value)
                assertEquals("bytes */26", invalidRange.headers["Content-Range"])
                val missingStream = client.get("/api/v1/tracks/999999/stream") { bearerAuth(token) }
                assertEquals(404, missingStream.status.value)

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
