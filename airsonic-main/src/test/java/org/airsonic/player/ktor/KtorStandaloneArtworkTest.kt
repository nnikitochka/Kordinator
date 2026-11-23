package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.ArtworkSearchResult
import org.airsonic.player.service.ArtworkManagementService
import org.airsonic.player.service.StandaloneArtworkSearchService
import org.airsonic.player.service.StoredArtwork
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneArtworkTest {
    @TempDir
    lateinit var home: Path

    @Test
    fun artworkSearchAndDirectoryUpdateRequireBearerAndAdministratorAccess() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_artwork_api_${home.fileName}"
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
            val bytes = byteArrayOf(1, 2, 3, 4)
            var savedDirectoryId: Int? = null
            var savedBytes: ByteArray? = null
            val management = object : ArtworkManagementService {
                override fun setDirectoryArtwork(directoryId: Int, bytes: ByteArray): StoredArtwork {
                    savedDirectoryId = directoryId
                    savedBytes = bytes
                    return StoredArtwork("Album/cover.png", "image/png", bytes.size)
                }
            }
            val search = StandaloneArtworkSearchService(
                searchProvider = { query ->
                    assertEquals("Artist Album", query)
                    listOf(ArtworkSearchResult("https://lastfm.freetls.fastly.net/cover.png", "Artist", "Album"))
                },
                imageDownloader = { bytes },
            )

            testApplication {
                application {
                    standaloneModule(
                        authService = auth,
                        artworkSearchService = search,
                        artworkManagementService = management,
                    )
                }

                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/artwork/search?artist=Artist&album=Album").status)
                val matches = client.get("/api/v1/artwork/search?artist=Artist&album=Album") { bearerAuth(listenerToken) }
                assertEquals(HttpStatusCode.OK, matches.status)
                assertTrue(matches.bodyAsText().contains("https://lastfm.freetls.fastly.net/cover.png"))

                val denied = client.put("/api/v1/library/directories/12/artwork") {
                    bearerAuth(listenerToken)
                    setBody(bytes)
                }
                assertEquals(HttpStatusCode.Forbidden, denied.status)
                val unsupported = client.put("/api/v1/library/directories/12/artwork") {
                    bearerAuth(adminToken)
                    headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(bytes)
                }
                assertEquals(HttpStatusCode.UnsupportedMediaType, unsupported.status)

                val saved = client.put("/api/v1/library/directories/12/artwork") {
                    bearerAuth(adminToken)
                    headers.append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                    setBody(bytes)
                }
                assertEquals(HttpStatusCode.OK, saved.status, saved.bodyAsText())
                assertTrue(saved.bodyAsText().contains("Album/cover.png"))
                assertEquals(12, savedDirectoryId)
                assertTrue(savedBytes!!.contentEquals(bytes))

                val selected = client.put("/api/v1/library/directories/12/artwork/from-search") {
                    bearerAuth(adminToken)
                    headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("""{"artist":"Artist","album":"Album","imageUrl":"https://lastfm.freetls.fastly.net/cover.png"}""")
                }
                assertEquals(HttpStatusCode.OK, selected.status, selected.bodyAsText())
                assertTrue(selected.bodyAsText().contains("Album/cover.png"))
            }
        }
    }
}
