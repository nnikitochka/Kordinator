package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.KtorCoreMediaCatalogService
import org.airsonic.player.service.StandaloneVideoPlaybackService
import org.airsonic.player.service.StandaloneVideoBookmarkService
import org.airsonic.player.service.StandalonePlaylistService
import org.airsonic.player.service.TranscodingService
import org.mockito.Mockito
import java.nio.file.Path
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class KtorStandaloneVideoTest {
    @TempDir
    lateinit var home: Path

    @Test
    fun listsVideosOnlyFromLibrariesAssignedToTheCurrentUser() {
        val musicRoot = home.resolve("music")
        Files.createDirectories(musicRoot)
        val runtime = KtorRuntimeFixture(home.resolve("runtime"), musicRoot, "standalone_videos_core_${home.fileName}")
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_videos_api_${home.fileName}"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })
        try {
            dataSource.use {
                StandaloneDatabase.migrate(dataSource)
                val auth = LocalAuthService(dataSource)
                val adminId = auth.createInitialAdmin("admin", "a strong password for tests")
                val listenerId = auth.createUser("listener", "listener password for tests")
                val adminToken = auth.login("admin", "a strong password for tests")!!.token
                val listenerToken = auth.login("listener", "listener password for tests")!!.token
                val library = runtime.mediaFolderService.getAllMusicFolders(true, true).single()
                val changed = Instant.now()
                val videos = listOf("Concert A", "Concert B").mapIndexed { index, title ->
                    MediaFile().apply {
                        path = "video-$index.mp4"
                        folderId = library.id
                        mediaType = MediaFile.MediaType.VIDEO
                        format = "mp4"
                        this.title = title
                        artist = "Live Artist"
                        duration = 90.0 + index
                        fileSize = 1_000L + index
                        width = 1920
                        height = 1080
                        parentPath = ""
                        created = changed
                        this.changed = changed
                        lastScanned = changed
                        childrenLastUpdated = changed
                        setPresent(true)
                    }.also { video -> runtime.runtime.createPersistenceGraph().daos.mediaFileDao.createOrUpdateMediaFile(video) { } }
                }
                val mediaFileDao = runtime.runtime.createPersistenceGraph().daos.mediaFileDao
                val mediaFileService = Mockito.mock(MediaFileService::class.java)
                videos.forEach { video -> Mockito.`when`(mediaFileService.getMediaFile(video.id!!)).thenReturn(video) }
                val legacyTrackTime = Instant.now()
                val legacyTrack = MediaFile().apply {
                    path = "legacy-track.mp3"
                    folderId = library.id
                    mediaType = MediaFile.MediaType.MUSIC
                    title = "Legacy Track"
                    albumName = "Legacy Rated Album"
                    artist = "Legacy Artist"
                    format = "mp3"
                    duration = 180.0
                    fileSize = 1_000
                    parentPath = ""
                    created = legacyTrackTime
                    this.changed = legacyTrackTime
                    lastScanned = legacyTrackTime
                    childrenLastUpdated = legacyTrackTime
                    setPresent(true)
                }.also { mediaFileDao.createOrUpdateMediaFile(it) { } }
                val legacyAlbum = MediaFile().apply {
                    path = "legacy-rated-album"
                    folderId = library.id
                    mediaType = MediaFile.MediaType.ALBUM
                    title = "Legacy Rated Album"
                    albumName = "Legacy Rated Album"
                    artist = "Legacy Artist"
                    parentPath = ""
                    created = legacyTrackTime
                    this.changed = legacyTrackTime
                    lastScanned = legacyTrackTime
                    childrenLastUpdated = legacyTrackTime
                    setPresent(true)
                }.also { mediaFileDao.createOrUpdateMediaFile(it) { } }
                runtime.coreDataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "INSERT INTO bookmark (media_file_id, position_millis, username, comment, created, changed) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    ).use { statement ->
                        statement.setInt(1, videos.first().id!!)
                        statement.setLong(2, 12_000)
                        statement.setString(3, "admin")
                        statement.setString(4, "migrated bookmark")
                        statement.setTimestamp(5, java.sql.Timestamp.from(Instant.now()))
                        statement.setTimestamp(6, java.sql.Timestamp.from(Instant.now()))
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        "INSERT INTO bookmark (media_file_id, position_millis, username, comment, created, changed) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    ).use { statement ->
                        statement.setInt(1, legacyTrack.id!!)
                        statement.setLong(2, 45_000)
                        statement.setString(3, "admin")
                        statement.setString(4, "migrated track bookmark")
                        statement.setTimestamp(5, java.sql.Timestamp.from(Instant.now()))
                        statement.setTimestamp(6, java.sql.Timestamp.from(Instant.now()))
                        statement.executeUpdate()
                    }
                    listOf(legacyTrack.id!! to 4, legacyAlbum.id!! to 5).forEach { (mediaId, rating) ->
                        connection.prepareStatement(
                            "INSERT INTO user_rating (username, media_file_id, rating) VALUES (?, ?, ?)",
                        ).use { statement ->
                            statement.setString(1, "admin")
                            statement.setInt(2, mediaId)
                            statement.setInt(3, rating)
                            statement.executeUpdate()
                        }
                    }
                    listOf(legacyTrack.id!!, legacyAlbum.id!!).forEach { mediaId ->
                        connection.prepareStatement(
                            "INSERT INTO starred_media_file (media_file_id, username, created) VALUES (?, ?, ?)",
                        ).use { statement ->
                            statement.setInt(1, mediaId)
                            statement.setString(2, "admin")
                            statement.setTimestamp(3, java.sql.Timestamp.from(Instant.now()))
                            statement.executeUpdate()
                        }
                    }
                    val legacyPlaylistId = connection.prepareStatement(
                        "INSERT INTO playlist (id, username, is_public, name, comment, file_count, duration, created, changed) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    ).use { statement ->
                        statement.setInt(1, 701)
                        statement.setString(2, "admin")
                        statement.setBoolean(3, true)
                        statement.setString(4, "Imported shared playlist")
                        statement.setString(5, "legacy playlist comment")
                        statement.setInt(6, 1)
                        statement.setInt(7, 180)
                        statement.setTimestamp(8, java.sql.Timestamp.from(legacyTrackTime))
                        statement.setTimestamp(9, java.sql.Timestamp.from(legacyTrackTime))
                        statement.executeUpdate()
                        701
                    }
                    connection.prepareStatement("INSERT INTO playlist_file (playlist_id, media_file_id) VALUES (?, ?)").use { statement ->
                        statement.setInt(1, legacyPlaylistId)
                        statement.setInt(2, legacyTrack.id!!)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        "INSERT INTO artist (name, album_count, last_scanned, present, folder_id) VALUES (?, ?, ?, ?, ?)",
                    ).use { statement ->
                        statement.setString(1, "Legacy Artist")
                        statement.setInt(2, 1)
                        statement.setTimestamp(3, java.sql.Timestamp.from(legacyTrackTime))
                        statement.setBoolean(4, true)
                        statement.setInt(5, library.id!!)
                        statement.executeUpdate()
                    }
                    val artistId = connection.prepareStatement("SELECT id FROM artist WHERE name = ?").use { statement ->
                        statement.setString(1, "Legacy Artist")
                        statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
                    }
                    connection.prepareStatement(
                        "INSERT INTO starred_artist (artist_id, username, created) VALUES (?, ?, ?)",
                    ).use { statement ->
                        statement.setInt(1, artistId)
                        statement.setString(2, "admin")
                        statement.setTimestamp(3, java.sql.Timestamp.from(Instant.now()))
                        statement.executeUpdate()
                    }
                }
                val mediaCatalog = KtorCoreMediaCatalogService(runtime.coreDataSource, dataSource)
                val playlists = StandalonePlaylistService(dataSource, mediaCatalog)
                val importedPlaylistId = dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "SELECT playlist_id FROM legacy_playlist_import WHERE legacy_playlist_id = ?",
                    ).use { statement ->
                        statement.setInt(1, 701)
                        statement.executeQuery().use { rows -> assertTrue(rows.next()); rows.getLong(1) }
                    }
                }
                val importedPlaylist = playlists.get(adminId, importedPlaylistId)
                assertEquals("Imported shared playlist", importedPlaylist?.name)
                assertEquals("PUBLIC", importedPlaylist?.visibility)
                assertEquals("legacy playlist comment", importedPlaylist?.description)
                assertEquals(legacyTrack.id!!.toLong(), importedPlaylist?.tracks?.single()?.id)
                assertEquals(importedPlaylistId, playlists.list(listenerId).single().id)
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "SELECT position_ms, comment FROM track_bookmark WHERE user_id = ? AND track_id = ?",
                    ).use { statement ->
                        statement.setLong(1, adminId)
                        statement.setLong(2, legacyTrack.id!!.toLong())
                        statement.executeQuery().use { rows ->
                            assertTrue(rows.next())
                            assertEquals(45_000, rows.getLong("position_ms"))
                            assertEquals("migrated track bookmark", rows.getString("comment"))
                        }
                    }
                    connection.prepareStatement(
                        "SELECT rating FROM track_rating WHERE user_id = ? AND track_id = ?",
                    ).use { statement ->
                        statement.setLong(1, adminId)
                        statement.setLong(2, legacyTrack.id!!.toLong())
                        statement.executeQuery().use { rows ->
                            assertTrue(rows.next())
                            assertEquals(4, rows.getInt("rating"))
                        }
                    }
                    connection.prepareStatement(
                        "SELECT rating FROM album_rating WHERE user_id = ? AND library_id = ? AND album_name = ?",
                    ).use { statement ->
                        statement.setLong(1, adminId)
                        statement.setLong(2, library.id!!.toLong())
                        statement.setString(3, "Legacy Rated Album")
                        statement.executeQuery().use { rows ->
                            assertTrue(rows.next())
                            assertEquals(5, rows.getInt("rating"))
                        }
                    }
                    connection.prepareStatement("SELECT 1 FROM favorite_track WHERE user_id = ? AND track_id = ?").use { statement ->
                        statement.setLong(1, adminId)
                        statement.setLong(2, legacyTrack.id!!.toLong())
                        assertTrue(statement.executeQuery().use { it.next() })
                    }
                    connection.prepareStatement(
                        "SELECT 1 FROM favorite_album WHERE user_id = ? AND library_id = ? AND album_name = ?",
                    ).use { statement ->
                        statement.setLong(1, adminId)
                        statement.setLong(2, library.id!!.toLong())
                        statement.setString(3, "Legacy Rated Album")
                        assertTrue(statement.executeQuery().use { it.next() })
                    }
                    connection.prepareStatement(
                        "SELECT 1 FROM favorite_artist WHERE user_id = ? AND library_id = ? AND artist_name = ?",
                    ).use { statement ->
                        statement.setLong(1, adminId)
                        statement.setLong(2, library.id!!.toLong())
                        statement.setString(3, "Legacy Artist")
                        assertTrue(statement.executeQuery().use { it.next() })
                    }
                }
                val playbackService = StandaloneVideoPlaybackService(mediaFileService, Mockito.mock(TranscodingService::class.java))
                val videoBookmarkService = StandaloneVideoBookmarkService(dataSource, mediaFileService)
                val previousHome = System.getProperty("airsonic.home")
                System.setProperty("airsonic.home", home.resolve("airsonic-home").toString())

                try {
                testApplication {
                    application {
                        standaloneModule(
                            authService = auth,
                            mediaFolderService = runtime.mediaFolderService,
                            mediaFileDao = mediaFileDao,
                            mediaFileService = mediaFileService,
                            videoPlaybackService = playbackService,
                            videoBookmarkService = videoBookmarkService,
                        )
                    }

                    val hidden = client.get("/api/v1/videos") { bearerAuth(listenerToken) }
                    assertEquals(HttpStatusCode.OK, hidden.status)
                    assertEquals(0, jacksonObjectMapper().readTree(hidden.bodyAsText()).get("items").size())
                    val videoId = requireNotNull(videos.first().id)
                    val hiddenPlayback = client.post("/api/v1/videos/$videoId/playback-sessions") {
                        bearerAuth(listenerToken)
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                    assertEquals(HttpStatusCode.NotFound, hiddenPlayback.status)

                    val first = client.get("/api/v1/videos?limit=1") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.OK, first.status)
                    val firstBody = jacksonObjectMapper().readTree(first.bodyAsText())
                    assertEquals(1, firstBody.get("items").size())
                    assertEquals("Concert A", firstBody.get("items")[0].get("title").asText())
                    assertEquals(1920, firstBody.get("items")[0].get("width").asInt())
                    assertTrue(firstBody.get("hasMore").asBoolean())

                    assertTrue(auth.setAllowedLibraries(listenerId, setOf(library.id!!.toLong())))
                    val visible = client.get("/api/v1/videos") { bearerAuth(listenerToken) }
                    assertEquals(2, jacksonObjectMapper().readTree(visible.bodyAsText()).get("items").size())
                    val imported = jacksonObjectMapper().readTree(
                        client.get("/api/v1/me/video-bookmarks") { bearerAuth(adminToken) }.bodyAsText(),
                    )
                    assertEquals(1, imported.get("count").asInt())
                    assertEquals(12_000, imported.get("items")[0].get("positionMillis").asLong())
                    assertEquals("migrated bookmark", imported.get("items")[0].get("comment").asText())

                    val unauthenticatedBookmarks = client.get("/api/v1/me/video-bookmarks")
                    assertEquals(HttpStatusCode.Unauthorized, unauthenticatedBookmarks.status)
                    val hiddenBookmark = client.put("/api/v1/videos/$videoId/bookmark") {
                        bearerAuth(adminToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"positionMillis":10000}""")
                    }
                    assertEquals(HttpStatusCode.NoContent, hiddenBookmark.status)
                    val privateBookmarks = client.get("/api/v1/me/video-bookmarks") { bearerAuth(listenerToken) }
                    assertEquals(0, jacksonObjectMapper().readTree(privateBookmarks.bodyAsText()).get("count").asInt())
                    val savedBookmark = client.put("/api/v1/videos/$videoId/bookmark") {
                        bearerAuth(listenerToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"positionMillis":10000,"comment":"resume here"}""")
                    }
                    assertEquals(HttpStatusCode.NoContent, savedBookmark.status)
                    val bookmarkList = jacksonObjectMapper().readTree(
                        client.get("/api/v1/me/video-bookmarks") { bearerAuth(listenerToken) }.bodyAsText(),
                    )
                    assertEquals(1, bookmarkList.get("count").asInt())
                    assertEquals(videoId, bookmarkList.get("items")[0].get("videoId").asInt())
                    assertEquals("resume here", bookmarkList.get("items")[0].get("comment").asText())
                    assertEquals(HttpStatusCode.BadRequest, client.put("/api/v1/videos/$videoId/bookmark") {
                        bearerAuth(listenerToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"positionMillis":-1}""")
                    }.status)
                    val nearEnd = client.put("/api/v1/videos/$videoId/bookmark") {
                        bearerAuth(listenerToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"positionMillis":40000}""")
                    }
                    assertEquals(HttpStatusCode.NoContent, nearEnd.status)
                    assertEquals(0, jacksonObjectMapper().readTree(
                        client.get("/api/v1/me/video-bookmarks") { bearerAuth(listenerToken) }.bodyAsText(),
                    ).get("count").asInt())

                    val createdSession = client.post("/api/v1/videos/$videoId/playback-sessions") {
                        bearerAuth(listenerToken)
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                    assertEquals(HttpStatusCode.Created, createdSession.status, createdSession.bodyAsText())
                    val sessionJson = jacksonObjectMapper().readTree(createdSession.bodyAsText())
                    val playlistUrl = sessionJson.get("playlistUrl").asText()
                    val playlist = client.get(playlistUrl) { bearerAuth(listenerToken) }
                    assertEquals(HttpStatusCode.OK, playlist.status)
                    assertTrue(playlist.bodyAsText().contains("segments/0.ts"))
                    val outOfRangeSegment = client.get("/api/v1/videos/$videoId/playback-sessions/${sessionJson.get("id").asText()}/segments/99.ts") {
                        bearerAuth(listenerToken)
                    }
                    assertEquals(HttpStatusCode.BadRequest, outOfRangeSegment.status)
                    assertEquals(HttpStatusCode.NotFound, client.get(playlistUrl) { bearerAuth(adminToken) }.status)
                    assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/videos/$videoId/playback-sessions/${sessionJson.get("id").asText()}") {
                        bearerAuth(listenerToken)
                    }.status)

                    val invalidLimit = client.get("/api/v1/videos?limit=bad") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.BadRequest, invalidLimit.status)
                    val invalidOffset = client.get("/api/v1/videos?offset=-1") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.BadRequest, invalidOffset.status)
                    assertFalse(jacksonObjectMapper().readTree(invalidLimit.bodyAsText()).get("message").asText().isBlank())
                }
                } finally {
                    playbackService.close()
                    if (previousHome == null) System.clearProperty("airsonic.home") else System.setProperty("airsonic.home", previousHome)
                }
            }
        } finally {
            runtime.close()
        }
    }
}
