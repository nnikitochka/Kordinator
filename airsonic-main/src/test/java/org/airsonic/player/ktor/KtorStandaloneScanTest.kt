package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.head
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
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
import org.airsonic.player.service.StandaloneLibraryScanService
import org.airsonic.player.service.StandaloneFavoriteService
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KtorStandaloneScanTest {
    @TempDir
    lateinit var musicRoot: Path

    @Test
    fun scanIndexesCoreMediaMetadataAndExposesPaginatedCatalog() {
        Files.writeString(musicRoot.resolve("01 - First Track.mp3"), "not a decodable audio file")
        val coverBytes = ByteArrayOutputStream().use { output ->
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output)
            output.toByteArray()
        }
        Files.write(musicRoot.resolve("cover.png"), coverBytes)
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_scan_api_${musicRoot.fileName}"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 4
        })

        val runtime = KtorRuntimeFixture(musicRoot.resolveSibling("runtime"), musicRoot, "standalone_scan_core_${musicRoot.fileName}")
        try {
            dataSource.use {
                StandaloneDatabase.migrate(dataSource)
                val auth = LocalAuthService(dataSource)
                auth.createInitialAdmin("admin", "a strong password for tests")
                val token = auth.login("admin", "a strong password for tests")!!.token
                val listenerId = auth.createUser("listener", "listener password for tests")
                val listenerToken = auth.login("listener", "listener password for tests")!!.token
                val library = runtime.mediaFolderService.getAllMusicFolders(true, true).single()
                val coreCatalog = mockCoreCatalog(
                    runtime,
                    library,
                    "01 - First Track.mp3",
                    "01 - First Track",
                    Files.size(musicRoot.resolve("01 - First Track.mp3")),
                )
                val coreTrackId = runtime.coreDataSource.connection.use { connection ->
                    connection.prepareStatement("SELECT id FROM media_file WHERE path = ? AND folder_id = ?").use { statement ->
                        statement.setString(1, "01 - First Track.mp3")
                        statement.setInt(2, library.id)
                        statement.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
                    }
                }
                val scanService = StandaloneLibraryScanService(
                    dataSource,
                    runtime.mediaFolderService,
                    coreCatalog.mediaFileService,
                    coreCatalog.mediaScannerService,
                )
                val adminId = auth.authenticate(token)!!.id
                val legacyTrackId = 9_876_543L
                dataSource.connection.use { connection ->
                    connection.createStatement().use {
                        it.execute("CREATE TABLE media_track (id BIGINT PRIMARY KEY, library_id BIGINT NOT NULL, relative_path VARCHAR(4096) NOT NULL)")
                    }
                    connection.prepareStatement("INSERT INTO media_track (id, library_id, relative_path) VALUES (?, ?, ?)").use { statement ->
                        statement.setLong(1, legacyTrackId)
                        statement.setInt(2, library.id)
                        statement.setString(3, "01 - First Track.mp3")
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        "INSERT INTO favorite_track (user_id, track_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                    ).use { statement ->
                        statement.setLong(1, adminId)
                        statement.setLong(2, legacyTrackId)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        "INSERT INTO user_playlist (id, owner_id, name, created_at, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    ).use { statement ->
                        statement.setLong(1, 9_876_543L)
                        statement.setLong(2, adminId)
                        statement.setString(3, "Legacy")
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        "INSERT INTO user_playlist_track (playlist_id, position, track_id) VALUES (?, 0, ?)",
                    ).use { statement ->
                        statement.setLong(1, 9_876_543L)
                        statement.setLong(2, legacyTrackId)
                        statement.executeUpdate()
                    }
                }
                val mediaCatalogService = org.airsonic.player.service.KtorCoreMediaCatalogService(runtime.coreDataSource, dataSource)
                val hasTrackProjection = dataSource.connection.use { connection ->
                    connection.metaData.getTables(connection.catalog, null, null, arrayOf("TABLE")).use { tables ->
                        generateSequence { if (tables.next()) tables.getString("TABLE_NAME") else null }
                            .any { it.equals("media_track", ignoreCase = true) }
                    }
                }
                assertFalse(hasTrackProjection)
                dataSource.connection.use { connection ->
                    connection.prepareStatement("SELECT track_id FROM favorite_track WHERE user_id = ?").use { statement ->
                        statement.setLong(1, adminId)
                        statement.executeQuery().use { rows -> rows.next(); assertEquals(coreTrackId, rows.getLong(1)) }
                    }
                    connection.prepareStatement("SELECT track_id FROM user_playlist_track WHERE playlist_id = ?").use { statement ->
                        statement.setLong(1, 9_876_543L)
                        statement.executeQuery().use { rows -> rows.next(); assertEquals(coreTrackId, rows.getLong(1)) }
                    }
                }
                org.mockito.Mockito.`when`(coreCatalog.mediaFileService.getMediaFile(coreTrackId.toInt())).thenAnswer {
                    runtime.runtime.createPersistenceGraph().daos.mediaFileDao.getMediaFile(coreTrackId.toInt()).apply { duration = 30.0 }
                }
                val favoriteService = StandaloneFavoriteService(dataSource, mediaCatalogService)
                val mapper = jacksonObjectMapper()
                val transcoding = org.mockito.Mockito.mock(org.airsonic.player.service.TranscodingService::class.java)
                val profile = org.airsonic.player.domain.Transcoding(1, "test audio", "mp3", "opus", "ffmpeg -i %s -f opus -", null, null, true)
                org.mockito.Mockito.`when`(transcoding.allTranscodings).thenReturn(listOf(profile))
                val starts = mutableListOf<Double>()
                val closedTranscodes = java.util.concurrent.atomic.AtomicInteger()
                org.mockito.Mockito.`when`(transcoding.openTranscodedInputStream(org.mockito.ArgumentMatchers.any())).thenAnswer { invocation ->
                    val parameters = invocation.getArgument<org.airsonic.player.service.TranscodingService.Parameters>(0)
                    assertEquals(128, parameters.maxBitRate)
                    assertEquals(profile, parameters.transcoding)
                    starts.add(parameters.audioStartSeconds)
                    object : java.io.ByteArrayInputStream("encoded audio".toByteArray()) {
                        override fun close() { closedTranscodes.incrementAndGet(); super.close() }
                    }
                }


                testApplication {
                    application {
                        standaloneModule(
                            authService = auth,
                            libraryScanService = scanService,
                            favoriteService = favoriteService,
                            mediaFolderService = runtime.mediaFolderService,
                            mediaCatalogService = mediaCatalogService,
                            mediaFileService = coreCatalog.mediaFileService,
                            transcodingService = transcoding,
                        )
                    }

                val started = client.post("/api/v1/admin/libraries/${library.id}/scan") { bearerAuth(token) }
                assertEquals(202, started.status.value)
                org.mockito.Mockito.verify(coreCatalog.mediaScannerService).scanLibrary()
                val scanId = mapper.readTree(started.bodyAsText()).get("id").asText()

                var state = "QUEUED"
                for (attempt in 0 until 100) {
                    val status = client.get("/api/v1/admin/libraries/${library.id}/scan") { bearerAuth(token) }
                    state = mapper.readTree(status.bodyAsText()).get("state").asText()
                    if (state == "COMPLETED" || state == "FAILED") break
                    delay(25)
                }
                assertEquals("COMPLETED", state, "scan $scanId should complete")

                assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/tracks/$coreTrackId") {
                    bearerAuth(listenerToken)
                }.status)
                val grantLibrary = client.put("/api/v1/admin/users/$listenerId/libraries") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"libraryIds":[${library.id}]}""")
                }
                assertEquals(HttpStatusCode.NoContent, grantLibrary.status)
                val userLibraries = client.get("/api/v1/admin/users/$listenerId/libraries") { bearerAuth(token) }
                assertEquals(HttpStatusCode.OK, userLibraries.status)
                assertEquals(listOf(library.id.toLong()), mapper.readTree(userLibraries.bodyAsText()).map { it.asLong() })
                val listenerCatalog = client.get("/api/v1/tracks?libraryId=${library.id}") {
                    bearerAuth(listenerToken)
                }
                assertEquals(HttpStatusCode.OK, listenerCatalog.status)
                assertEquals(1, mapper.readTree(listenerCatalog.bodyAsText()).get("total").asInt())
                val deniedLibrary = client.get("/api/v1/tracks?libraryId=${library.id + 1}") {
                    bearerAuth(listenerToken)
                }
                assertEquals(HttpStatusCode.NotFound, deniedLibrary.status)
                val revokeLibrary = client.put("/api/v1/admin/users/$listenerId/libraries") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"libraryIds":[]}""")
                }
                assertEquals(HttpStatusCode.NoContent, revokeLibrary.status)
                assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/tracks/$coreTrackId") {
                    bearerAuth(listenerToken)
                }.status)
                assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/tracks/$coreTrackId/stream") {
                    bearerAuth(listenerToken)
                }.status)

                val catalog = client.get("/api/v1/tracks?libraryId=${library.id}&q=first&limit=10&offset=0") {
                    bearerAuth(token)
                }
                assertEquals(200, catalog.status.value)
                assertEquals(401, client.get("/api/v1/tracks").status.value)
                val catalogBody = mapper.readTree(catalog.bodyAsText())
                val items = catalogBody.get("items")
                assertEquals(1, items.size())
                assertEquals(1, catalogBody.get("total").asInt())
                assertEquals(coreTrackId, items[0].get("id").asLong())
                assertEquals("01 - First Track", items[0].get("title").asText())
                assertTrue(items[0].get("relativePath").asText().endsWith("01 - First Track.mp3"))

                runtime.coreDataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "UPDATE media_file SET artist = ?, album_artist = ?, album = ?, duration = ?, disc_number = ?, track_number = ?, year = ?, genre = ? WHERE path = ? AND folder_id = ?",
                    ).use { statement ->
                        statement.setString(1, "Test Artist")
                        statement.setString(2, "Test Artist")
                        statement.setString(3, "Test Album")
                        statement.setDouble(4, 213.0)
                        statement.setInt(5, 2)
                        statement.setInt(6, 7)
                        statement.setInt(7, 1997)
                        statement.setString(8, "Test Genre")
                        statement.setString(9, "01 - First Track.mp3")
                        statement.setInt(10, library.id)
                        assertEquals(1, statement.executeUpdate())
                    }
                }

                val albums = client.get("/api/v1/albums?libraryId=${library.id}") { bearerAuth(token) }
                assertEquals(200, albums.status.value)
                val albumItems = mapper.readTree(albums.bodyAsText()).get("items")
                assertEquals(1, albumItems.size())
                assertEquals("Test Album", albumItems[0].get("name").asText())
                assertEquals("Test Artist", albumItems[0].get("artist").asText())
                assertEquals(213, albumItems[0].get("durationSeconds").asInt())
                assertEquals(1997, albumItems[0].get("year").asInt())
                assertEquals("Test Genre", albumItems[0].get("genre").asText())
                val albumDetails = client.get("/api/v1/albums/${albumItems[0].get("id").asText()}") { bearerAuth(token) }
                assertEquals(200, albumDetails.status.value)
                assertEquals(1, mapper.readTree(albumDetails.bodyAsText()).get("tracks").size())
                val albumTrack = mapper.readTree(albumDetails.bodyAsText()).get("tracks")[0]
                assertEquals(2, albumTrack.get("discNumber").asInt())
                assertEquals(7, albumTrack.get("trackNumber").asInt())
                assertEquals(1997, albumTrack.get("year").asInt())
                assertEquals("Test Genre", albumTrack.get("genre").asText())
                assertEquals(401, client.get("/api/v1/albums").status.value)
                assertEquals(400, client.get("/api/v1/albums?limit=0") { bearerAuth(token) }.status.value)
                assertEquals(400, client.get("/api/v1/albums/invalid") { bearerAuth(token) }.status.value)

                val artists = client.get("/api/v1/artists?libraryId=${library.id}") { bearerAuth(token) }
                assertEquals(200, artists.status.value)
                val artistItems = mapper.readTree(artists.bodyAsText()).get("items")
                assertEquals(1, artistItems.size())
                assertEquals("Test Artist", artistItems[0].get("name").asText())
                assertEquals(1, artistItems[0].get("albumCount").asInt())
                assertEquals(1, artistItems[0].get("trackCount").asInt())
                val artistDetails = client.get("/api/v1/artists/${artistItems[0].get("id").asText()}") { bearerAuth(token) }
                assertEquals(200, artistDetails.status.value)
                assertEquals(1, mapper.readTree(artistDetails.bodyAsText()).get("tracks").size())
                assertEquals(401, client.get("/api/v1/artists").status.value)
                assertEquals(400, client.get("/api/v1/artists?offset=-1") { bearerAuth(token) }.status.value)

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
                assertEquals(204, client.put("/api/v1/tracks/$trackId/favorite") { bearerAuth(token) }.status.value)
                assertEquals(204, client.put("/api/v1/tracks/$trackId/favorite") { bearerAuth(token) }.status.value)
                val favorites = client.get("/api/v1/favorites/tracks?limit=10") { bearerAuth(token) }
                assertEquals(200, favorites.status.value)
                assertEquals(1, mapper.readTree(favorites.bodyAsText()).get("total").asInt())
                assertEquals(trackId, mapper.readTree(favorites.bodyAsText()).get("items")[0].get("id").asLong())
                dataSource.connection.use { connection ->
                    connection.prepareStatement("SELECT track_id FROM favorite_track WHERE user_id = ?").use { statement ->
                        statement.setLong(1, auth.authenticate(token)!!.id)
                        statement.executeQuery().use { rows -> rows.next(); assertEquals(coreTrackId, rows.getLong(1)) }
                    }
                }
                assertEquals(401, client.get("/api/v1/favorites/tracks").status.value)
                assertEquals(400, client.get("/api/v1/favorites/tracks?limit=0") { bearerAuth(token) }.status.value)
                assertEquals(204, client.delete("/api/v1/tracks/$trackId/favorite") { bearerAuth(token) }.status.value)
                assertEquals(0, mapper.readTree(client.get("/api/v1/favorites/tracks") { bearerAuth(token) }.bodyAsText()).get("total").asInt())
                assertEquals(401, client.get("/api/v1/tracks/$trackId").status.value)
                assertEquals(404, client.get("/api/v1/tracks/999999") { bearerAuth(token) }.status.value)
                assertEquals(400, client.get("/api/v1/tracks/-1") { bearerAuth(token) }.status.value)
                val fullStream = client.get("/api/v1/tracks/$trackId/stream") { bearerAuth(token) }
                assertEquals(200, fullStream.status.value, fullStream.bodyAsText())
                assertEquals(401, client.get("/api/v1/tracks/$trackId/stream").status.value)
                assertEquals("bytes", fullStream.headers["Accept-Ranges"])
                assertTrue("not a decodable audio file".toByteArray().contentEquals(fullStream.bodyAsBytes()))
                val encodedUrl = "/api/v1/tracks/$trackId/stream?format=opus&bitrateKbps=128"
                assertEquals(HttpStatusCode.Unauthorized, client.get(encodedUrl).status)
                assertEquals(HttpStatusCode.NotFound, client.get(encodedUrl) { bearerAuth(listenerToken) }.status)
                assertEquals(HttpStatusCode.OK, client.head(encodedUrl) { bearerAuth(token) }.status)
                org.mockito.Mockito.verify(transcoding, org.mockito.Mockito.never()).openTranscodedInputStream(org.mockito.ArgumentMatchers.any())
                val encoded = client.get(encodedUrl) { bearerAuth(token); header("If-None-Match", fullStream.headers["ETag"]!!) }
                assertEquals(HttpStatusCode.OK, encoded.status)
                assertEquals("audio/ogg", encoded.headers["Content-Type"]?.substringBefore(';'))
                assertEquals("private, no-store", encoded.headers["Cache-Control"])
                assertEquals("none", encoded.headers["Accept-Ranges"])
                assertEquals(null, encoded.headers["ETag"])
                assertEquals("encoded audio", encoded.bodyAsText())
                assertTrue(closedTranscodes.get() > 0)
                assertEquals(HttpStatusCode.OK, client.get("$encodedUrl&startSeconds=12.5") { bearerAuth(token) }.status)
                assertEquals(listOf(0.0, 12.5), starts)
                for (start in listOf("bad", "NaN", "Infinity", "-1", "30", "100")) {
                    assertEquals(HttpStatusCode.BadRequest, client.get("$encodedUrl&startSeconds=$start") { bearerAuth(token) }.status)
                }
                profile.step1 = "custom-encoder %s"
                assertEquals(HttpStatusCode.NotAcceptable, client.get("$encodedUrl&startSeconds=1") { bearerAuth(token) }.status)
                profile.step1 = "ffmpeg -i %s -f opus -"

                for (suffix in listOf("format=opus&bitrateKbps=bad", "format=opus&bitrateKbps=0", "format=opus&bitrateKbps=321", "format=")) {
                    assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/tracks/$trackId/stream?$suffix") { bearerAuth(token) }.status)
                }
                assertEquals(HttpStatusCode.NotAcceptable, client.get("/api/v1/tracks/$trackId/stream?format=flac") { bearerAuth(token) }.status)
                assertEquals(HttpStatusCode.BadRequest, client.get(encodedUrl) { bearerAuth(token); header("Range", "bytes=0-9") }.status)
                assertDisconnectStopsTranscoder(encodedUrl, token, auth, scanService, mediaCatalogService,
                    coreCatalog.mediaFileService, transcoding)
                org.mockito.Mockito.doReturn(java.io.ByteArrayInputStream(byteArrayOf())).`when`(transcoding)
                    .openTranscodedInputStream(org.mockito.ArgumentMatchers.any())
                assertEquals(HttpStatusCode.ServiceUnavailable, client.get(encodedUrl) { bearerAuth(token) }.status)
                org.mockito.Mockito.doThrow(java.io.IOException("missing executable")).`when`(transcoding)
                    .openTranscodedInputStream(org.mockito.ArgumentMatchers.any())
                assertEquals(HttpStatusCode.ServiceUnavailable, client.get(encodedUrl) { bearerAuth(token) }.status)
                val streamEtag = requireNotNull(fullStream.headers["ETag"])
                val streamModified = requireNotNull(fullStream.headers["Last-Modified"])
                assertEquals(304, client.get("/api/v1/tracks/$trackId/stream") {
                    bearerAuth(token)
                    header("If-None-Match", streamEtag)
                }.status.value)
                assertEquals(304, client.get("/api/v1/tracks/$trackId/stream") {
                    bearerAuth(token)
                    header("If-Modified-Since", streamModified)
                }.status.value)

                for (ifRange in listOf("Thu, 01 Jan 1970 00:00:00 GMT", "invalid", streamEtag)) {
                    val changed = client.get("/api/v1/tracks/$trackId/stream") {
                        bearerAuth(token); header("Range", "bytes=0-3"); header("If-Range", ifRange)
                    }
                    assertEquals(HttpStatusCode.OK, changed.status)
                    assertTrue(fullStream.bodyAsBytes().contentEquals(changed.bodyAsBytes()))
                }
                val unchangedRange = client.get("/api/v1/tracks/$trackId/stream") {
                    bearerAuth(token); header("Range", "bytes=0-3"); header("If-Range", streamModified)
                }
                assertEquals(HttpStatusCode.PartialContent, unchangedRange.status)
                assertEquals("not ", unchangedRange.bodyAsText())

                val artwork = client.get("/api/v1/tracks/$trackId/artwork") { bearerAuth(token) }
                assertEquals(200, artwork.status.value)
                assertEquals("image/png", artwork.headers["Content-Type"]?.substringBefore(';'))
                assertTrue(coverBytes.contentEquals(artwork.bodyAsBytes()))
                assertEquals(null, artwork.headers["Last-Modified"])
                val artworkEtag = requireNotNull(artwork.headers["ETag"])
                assertTrue(artwork.headers["Cache-Control"]!!.contains("max-age=300"))
                assertEquals(304, client.get("/api/v1/tracks/$trackId/artwork") {
                    bearerAuth(token)
                    header("If-None-Match", artworkEtag)
                }.status.value)
                Files.delete(musicRoot.resolve("cover.png"))
                val fallbackArtwork = client.get("/api/v1/tracks/$trackId/artwork") { bearerAuth(token) }
                val defaultCover = requireNotNull(javaClass.getResourceAsStream(
                    "/org/airsonic/player/controller/default_cover.jpg",
                )).use { it.readBytes() }
                assertEquals(200, fallbackArtwork.status.value)
                assertEquals("image/jpeg", fallbackArtwork.headers["Content-Type"]?.substringBefore(';'))
                assertTrue(defaultCover.contentEquals(fallbackArtwork.bodyAsBytes()))
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
                runtime.coreDataSource.connection.use { connection ->
                    connection.prepareStatement("UPDATE media_file SET present = false WHERE path = ? AND folder_id = ?").use { statement ->
                        statement.setString(1, "01 - First Track.mp3")
                        statement.setInt(2, library.id)
                        assertEquals(1, statement.executeUpdate())
                    }
                }
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
        } finally {
            runtime.close()
        }
    }
    private suspend fun assertDisconnectStopsTranscoder(
        encodedUrl: String,
        token: String,
        auth: LocalAuthService,
        scanService: StandaloneLibraryScanService,
        mediaCatalogService: org.airsonic.player.service.KtorCoreMediaCatalogService,
        mediaFiles: org.airsonic.player.service.MediaFileService,
        transcoding: org.airsonic.player.service.TranscodingService,
    ) {
        if (!Files.isExecutable(Path.of("/usr/bin/tail"))) return
        val opened = kotlinx.coroutines.CompletableDeferred<org.airsonic.player.io.TranscodeInputStream>()
        org.mockito.Mockito.doAnswer {
            org.airsonic.player.io.TranscodeInputStream(
                ProcessBuilder("/usr/bin/tail", "-f", "/dev/null"), null, null,
            ).also { opened.complete(it) }
        }.`when`(transcoding).openTranscodedInputStream(org.mockito.ArgumentMatchers.any())
        val server = io.ktor.server.engine.embeddedServer(io.ktor.server.netty.Netty, host = "127.0.0.1", port = 0) {
            standaloneModule(authService = auth, libraryScanService = scanService,
                mediaCatalogService = mediaCatalogService, mediaFileService = mediaFiles,
                transcodingService = transcoding)
        }.start(wait = false)
        var blocked: org.airsonic.player.io.TranscodeInputStream? = null
        try {
            val port = server.engine.resolvedConnectors().first().port
            java.net.Socket("127.0.0.1", port).use { socket ->
                socket.getOutputStream().write(("GET $encodedUrl HTTP/1.1\r\nHost: localhost\r\n" +
                    "Authorization: Bearer $token\r\nConnection: close\r\n\r\n").toByteArray())
                socket.getOutputStream().flush()
                blocked = kotlinx.coroutines.withTimeout(5_000) { opened.await() }
            }
            val stopped = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                blocked!!.process!!.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            }
            assertTrue(stopped, "Closing HTTP connection must terminate a blocked transcoder")
        } finally {
            blocked?.close()
            server.stop(0, 1_000)
        }
    }

}
