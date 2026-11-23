package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.domain.AlbumNotes
import org.airsonic.player.domain.ArtistBio
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.StandaloneLibraryScanService
import org.airsonic.player.service.StandalonePlaylistService
import org.airsonic.player.service.StandalonePlayQueueService
import org.airsonic.player.service.StandaloneFavoriteService
import org.airsonic.player.service.StandaloneBookmarkService
import org.airsonic.player.service.StandaloneRatingService
import org.airsonic.player.service.StandaloneTrackNoteService
import org.airsonic.player.service.StandaloneScrobbleService
import org.airsonic.player.service.StatusService
import org.airsonic.player.service.AudioScrobblerService
import org.airsonic.player.service.LyricsService
import org.airsonic.player.service.SearchService
import org.airsonic.player.service.LastFmService
import org.airsonic.player.domain.RandomSearchCriteria
import org.mockito.Mockito
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
                val otherId = requireNotNull(auth.authenticate(otherToken)).id
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
                    genre = "Rock",
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
                val bookmarks = StandaloneBookmarkService(dataSource, mediaCatalogService)
                val ratings = StandaloneRatingService(dataSource, mediaCatalogService)
                val trackNotes = StandaloneTrackNoteService(dataSource, mediaCatalogService)
                val statusService = Mockito.mock(StatusService::class.java)
                val scrobblerService = Mockito.mock(AudioScrobblerService::class.java)
                val scrobbleService = StandaloneScrobbleService(coreCatalog.mediaFileService, statusService, scrobblerService)
                val lyrics = LyricsService { """
                    <GetLyricResult><Lyric>Test lyrics</Lyric><LyricArtist>Test Artist</LyricArtist><LyricSong>Test Song</LyricSong></GetLyricResult>
                """ }
                val randomSearch = Mockito.mock(SearchService::class.java)
                val topSongs = Mockito.mock(LastFmService::class.java)
                val artistDao = Mockito.mock(org.airsonic.player.dao.ArtistDao::class.java)
                val coreTrackId = mediaCatalogService.tracks(null, null, 10, 0, null).single().id
                val coreFile = runtime.runtime.createPersistenceGraph().daos.mediaFileDao.getMediaFile(coreTrackId.toInt())
                Mockito.`when`(coreCatalog.mediaFileService.getMediaFile(coreTrackId.toInt())).thenReturn(coreFile)
                Mockito.`when`(coreCatalog.mediaFileService.getMediaFile("track.mp3", library.id.toInt())).thenReturn(coreFile)
                Mockito.`when`(randomSearch.getRandomSongs(Mockito.any(RandomSearchCriteria::class.java))).thenReturn(listOf(coreFile))
                Mockito.`when`(topSongs.getTopSongs(Mockito.anyString(), Mockito.anyInt(), Mockito.anyList())).thenReturn(listOf(coreFile))
                Mockito.`when`(topSongs.getSimilarSongs(
                    Mockito.any(org.airsonic.player.domain.Artist::class.java), Mockito.anyInt(), Mockito.anyList(),
                )).thenReturn(listOf(coreFile))
                Mockito.`when`(topSongs.getSimilarSongs(
                    Mockito.any(org.airsonic.player.domain.MediaFile::class.java), Mockito.anyInt(), Mockito.anyList(),
                )).thenReturn(listOf(coreFile))
                Mockito.`when`(artistDao.getArtist(Mockito.eq("Album Artist"), Mockito.anyList()))
                    .thenReturn(org.airsonic.player.domain.Artist().apply { name = "Album Artist" })
                configureExternalMetadata(topSongs)
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
                            bookmarkService = bookmarks,
                            trackNoteService = trackNotes,
                            ratingService = ratings,
                            scrobbleService = scrobbleService,
                            lyricsService = lyrics,
                            randomSearchService = randomSearch,
                            topSongsService = topSongs,
                            mediaFileService = coreCatalog.mediaFileService,
                            artistDao = artistDao,
                        )
                    }

                val tracksResponse = client.get("/api/v1/tracks") { bearerAuth(ownerToken) }
                val trackId = mapper.readTree(tracksResponse.bodyAsText()).get("items")[0].get("id").asLong()
                assertLibraryFolderList(client, mapper, ownerToken, library.id.toLong())
                assertTrackMetadata(client, mapper, ownerToken, otherToken, trackId)
                assertIndependentMetadata(client, mapper, ownerToken, otherToken, auth, otherId)
                assertEquals(true, auth.setAllowedLibraries(otherId, setOf(library.id.toLong())))
                assertTrackNotes(client, mapper, ownerToken, otherToken, trackId)
                assertEquals(true, auth.setAllowedLibraries(otherId, emptySet()))
                assertEquals(0, mapper.readTree(client.get("/api/v1/library/folders") {
                    bearerAuth(otherToken)
                }.bodyAsText()).size())

                val randomTracks = mapper.readTree(client.get(
                    "/api/v1/tracks/random?size=1&genre=Rock&fromYear=1990&toYear=2000&libraryId=${library.id}",
                ) { bearerAuth(ownerToken) }.bodyAsText())
                assertEquals(1, randomTracks.get("count").asInt())
                assertEquals(trackId, randomTracks.get("items")[0].get("id").asLong())
                assertEquals(400, client.get("/api/v1/tracks/random?fromYear=bad") {
                    bearerAuth(ownerToken)
                }.status.value)

                val rockTracks = mapper.readTree(client.get("/api/v1/tracks?genre=Rock") {
                    bearerAuth(ownerToken)
                }.bodyAsText())
                assertEquals(1, rockTracks.get("total").asInt())
                assertEquals(trackId, rockTracks.get("items")[0].get("id").asLong())
                val genres = mapper.readTree(client.get("/api/v1/genres") {
                    bearerAuth(ownerToken)
                }.bodyAsText())
                assertEquals(1, genres.get("count").asInt())
                assertEquals("Rock", genres.get("items")[0].get("name").asText())
                assertEquals(1, genres.get("items")[0].get("trackCount").asInt())

                assertEquals(204, client.put("/api/v1/tracks/$trackId/rating") {
                    bearerAuth(ownerToken)
                    header(io.ktor.http.HttpHeaders.ContentType, "application/json")
                    setBody("""{"rating":4}""")
                }.status.value)
                val ratingRows = dataSource.connection.use { connection ->
                    connection.prepareStatement("SELECT user_id, rating FROM track_rating WHERE track_id = ?").use { statement ->
                        statement.setLong(1, trackId)
                        statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1) to rows.getInt(2)) } }
                    }
                }
                assertEquals(1, ratingRows.size)
                assertEquals(4, ratingRows.single().second)
                assertEquals(4, mapper.readTree(client.get("/api/v1/tracks/$trackId/rating") {
                    bearerAuth(ownerToken)
                }.bodyAsText()).get("rating").asInt())
                assertEquals(404, client.get("/api/v1/tracks/$trackId/rating") {
                    bearerAuth(otherToken)
                }.status.value)
                assertEquals(400, client.put("/api/v1/tracks/$trackId/rating") {
                    bearerAuth(ownerToken)
                    header(io.ktor.http.HttpHeaders.ContentType, "application/json")
                    setBody("""{"rating":6}""")
                }.status.value)
                assertEquals(204, client.put("/api/v1/tracks/$trackId/rating") {
                    bearerAuth(ownerToken)
                    header(io.ktor.http.HttpHeaders.ContentType, "application/json")
                    setBody("""{"rating":0}""")
                }.status.value)
                assertEquals(0, dataSource.connection.use { connection ->
                    connection.prepareStatement("SELECT COUNT(*) FROM track_rating WHERE user_id = ? AND track_id = ?").use { statement ->
                        statement.setLong(1, auth.authenticate(ownerToken)!!.id)
                        statement.setLong(2, trackId)
                        statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
                    }
                })
                assertEquals(true, mapper.readTree(client.get("/api/v1/tracks/$trackId/rating") {
                    bearerAuth(ownerToken)
                }.bodyAsText()).get("rating").isNull)

                assertEquals(204, client.post("/api/v1/tracks/$trackId/scrobble") {
                    bearerAuth(ownerToken)
                    header(io.ktor.http.HttpHeaders.ContentType, "application/json")
                    setBody("{}")
                }.status.value)
                Mockito.verify(coreCatalog.mediaFileService).incrementPlayCount(coreFile)
                Mockito.verify(scrobblerService).register(coreFile, "owner", true, null)

                val lyricsResponse = mapper.readTree(client.get(
                    "/api/v1/lyrics?artist=Test%20Artist&title=Test%20Song&includeLookupStatus=true",
                ) { bearerAuth(ownerToken) }.bodyAsText())
                assertEquals("Test Artist", lyricsResponse.get("artist").asText())
                assertEquals("Test Song", lyricsResponse.get("title").asText())
                assertEquals("Test lyrics", lyricsResponse.get("lyrics").asText())
                assertEquals(false, lyricsResponse.get("tryLater").asBoolean())

                assertEquals(204, client.put("/api/v1/tracks/$trackId/bookmark") {
                    bearerAuth(ownerToken)
                    header(io.ktor.http.HttpHeaders.ContentType, "application/json")
                    setBody("""{"positionMillis":42000,"comment":"resume here"}""")
                }.status.value)
                val savedBookmark = mapper.readTree(client.get("/api/v1/me/bookmarks") {
                    bearerAuth(ownerToken)
                }.bodyAsText())
                assertEquals(1, savedBookmark.get("count").asInt())
                assertEquals(42000, savedBookmark.get("items")[0].get("positionMillis").asLong())
                assertEquals("resume here", savedBookmark.get("items")[0].get("comment").asText())
                assertEquals(0, mapper.readTree(client.get("/api/v1/me/bookmarks") {
                    bearerAuth(otherToken)
                }.bodyAsText()).get("count").asInt())
                assertEquals(400, client.put("/api/v1/tracks/$trackId/bookmark") {
                    bearerAuth(ownerToken)
                    header(io.ktor.http.HttpHeaders.ContentType, "application/json")
                    setBody("""{"positionMillis":-1}""")
                }.status.value)
                assertEquals(204, client.delete("/api/v1/tracks/$trackId/bookmark") {
                    bearerAuth(ownerToken)
                }.status.value)
                assertEquals(404, client.delete("/api/v1/tracks/$trackId/bookmark") {
                    bearerAuth(ownerToken)
                }.status.value)

                val albums = client.get("/api/v1/albums?libraryId=${library.id}") { bearerAuth(ownerToken) }
                val album = mapper.readTree(albums.bodyAsText()).get("items")[0]
                val albumId = album.get("id").asText()
                assertEquals("Test Album", album.get("name").asText())
                val artists = client.get("/api/v1/artists?libraryId=${library.id}") { bearerAuth(ownerToken) }
                val artist = mapper.readTree(artists.bodyAsText()).get("items")[0]
                val artistId = artist.get("id").asText()
                assertEquals("Album Artist", artist.get("name").asText())
                val topTracks = mapper.readTree(client.get("/api/v1/artists/$artistId/top-tracks?count=1") {
                    bearerAuth(ownerToken)
                }.bodyAsText())
                assertEquals(1, topTracks.get("count").asInt())
                assertEquals(trackId, topTracks.get("items")[0].get("id").asLong())
                val similarArtistTracks = mapper.readTree(client.get("/api/v1/artists/$artistId/similar-tracks?count=1") {
                    bearerAuth(ownerToken)
                }.bodyAsText())
                assertEquals(1, similarArtistTracks.get("count").asInt())
                assertEquals(trackId, similarArtistTracks.get("items")[0].get("id").asLong())
                val similarTrackTracks = mapper.readTree(client.get("/api/v1/tracks/$trackId/similar-tracks?count=1") {
                    bearerAuth(ownerToken)
                }.bodyAsText())
                assertEquals(1, similarTrackTracks.get("count").asInt())
                assertEquals(trackId, similarTrackTracks.get("items")[0].get("id").asLong())
                assertEquals(400, client.get("/api/v1/tracks/$trackId/similar-tracks?count=bad") {
                    bearerAuth(ownerToken)
                }.status.value)

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
                assertEquals(404, client.put("/api/v1/artists/$artistId/favorite") {
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

                assertEquals(true, auth.setAllowedLibraries(otherId, setOf(library.id.toLong())))
                assertEquals(204, client.put("/api/v1/albums/$albumId/favorite") { bearerAuth(otherToken) }.status.value)
                assertEquals(204, client.put("/api/v1/artists/$artistId/favorite") { bearerAuth(otherToken) }.status.value)
                val rescan = scans.enqueue(library.id.toLong())
                scans.run(rescan.id)
                assertEquals("COMPLETED", scans.latestScan(library.id.toLong())?.state)
                assertEquals(1, mapper.readTree(client.get("/api/v1/favorites/albums") {
                    bearerAuth(otherToken)
                }.bodyAsText()).get("total").asInt())
                assertEquals(1, mapper.readTree(client.get("/api/v1/favorites/artists") {
                    bearerAuth(otherToken)
                }.bodyAsText()).get("total").asInt())

                assertEquals(true, auth.setAllowedLibraries(otherId, emptySet()))
                assertEquals(0, mapper.readTree(client.get("/api/v1/favorites/albums") {
                    bearerAuth(otherToken)
                }.bodyAsText()).get("total").asInt())
                assertEquals(0, mapper.readTree(client.get("/api/v1/favorites/artists") {
                    bearerAuth(otherToken)
                }.bodyAsText()).get("total").asInt())
                assertEquals(true, auth.setAllowedLibraries(otherId, setOf(library.id.toLong())))
                assertEquals(1, mapper.readTree(client.get("/api/v1/favorites/albums") {
                    bearerAuth(otherToken)
                }.bodyAsText()).get("total").asInt())
                assertEquals(1, mapper.readTree(client.get("/api/v1/favorites/artists") {
                    bearerAuth(otherToken)
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
                assertPlaylistExport(client, ownerToken, otherToken, playlistId)
                assertPlaylistImport(client, ownerToken, otherToken, mapper)
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
                assertEquals(2, mapper.readTree(list.bodyAsText()).size())
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

    private suspend fun assertPlaylistExport(client: HttpClient, ownerToken: String, otherToken: String, playlistId: Long) {
        val exported = client.get("/api/v1/playlists/$playlistId/export") { bearerAuth(ownerToken) }
        assertEquals(200, exported.status.value, exported.bodyAsText())
        assertEquals("no-store", exported.headers[io.ktor.http.HttpHeaders.CacheControl])
        assertEquals(
            "application/vnd.apple.mpegurl",
            exported.headers[io.ktor.http.HttpHeaders.ContentType]?.substringBefore(';'),
        )
        assertEquals(true, exported.headers[io.ktor.http.HttpHeaders.ContentDisposition]?.contains("Road_Trip.m3u8"))
        assertEquals(true, exported.bodyAsText().startsWith("#EXTM3U\n"))
        assertEquals(2, exported.bodyAsText().lines().count { it == "track.mp3" })
        assertEquals(404, client.get("/api/v1/playlists/$playlistId/export") { bearerAuth(otherToken) }.status.value)
    }

    private suspend fun assertPlaylistImport(
        client: HttpClient,
        ownerToken: String,
        otherToken: String,
        mapper: com.fasterxml.jackson.databind.ObjectMapper,
    ) {
        val fileHeaders = Headers.build {
            append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"Imported.m3u\"")
            append(HttpHeaders.ContentType, "audio/x-mpegurl")
        }
        val imported = client.post("/api/v1/playlists/import") {
            bearerAuth(ownerToken)
            setBody(MultiPartFormDataContent(formData {
                append("file", "#EXTM3U\n#EXTINF:120,Track Artist - track\ntrack.mp3\n".toByteArray(), fileHeaders)
            }))
        }
        assertEquals(201, imported.status.value, imported.bodyAsText())
        val body = mapper.readTree(imported.bodyAsText())
        assertEquals("Imported", body.get("name").asText())
        assertEquals(1, body.get("trackIds").size())
        val importedId = body.get("id").asLong()
        assertEquals(404, client.get("/api/v1/playlists/$importedId") { bearerAuth(otherToken) }.status.value)
        val traversal = client.post("/api/v1/playlists/import") {
            bearerAuth(ownerToken)
            setBody(MultiPartFormDataContent(formData {
                append("file", "#EXTM3U\n../outside.mp3\n".toByteArray(), fileHeaders)
            }))
        }
        assertEquals(400, traversal.status.value)
    }

    private suspend fun assertTrackNotes(
        client: HttpClient,
        mapper: com.fasterxml.jackson.databind.ObjectMapper,
        ownerToken: String,
        otherToken: String,
        trackId: Long,
    ) {
        val path = "/api/v1/tracks/$trackId/note"
        val empty = client.get(path) { bearerAuth(ownerToken) }
        assertEquals(200, empty.status.value)
        assertEquals(true, mapper.readTree(empty.bodyAsText()).get("note").isNull)

        assertEquals(204, client.put(path) {
            bearerAuth(ownerToken)
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"note":"  remember the quiet intro  "}""")
        }.status.value)
        val saved = mapper.readTree(client.get(path) { bearerAuth(ownerToken) }.bodyAsText())
        assertEquals("remember the quiet intro", saved.get("note").asText())
        assertEquals(true, saved.get("createdAt").isTextual)
        assertEquals(true, saved.get("updatedAt").isTextual)

        val otherUserView = mapper.readTree(client.get(path) { bearerAuth(otherToken) }.bodyAsText())
        assertEquals(true, otherUserView.get("note").isNull)
        assertEquals(404, client.get("/api/v1/tracks/${trackId + 1}/note") { bearerAuth(otherToken) }.status.value)
        assertEquals(404, client.put("/api/v1/tracks/${trackId + 1}/note") {
            bearerAuth(otherToken)
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"note":"foreign track"}""")
        }.status.value)
        assertEquals(400, client.put(path) {
            bearerAuth(ownerToken)
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"note":"   "}""")
        }.status.value)
        assertEquals(204, client.delete(path) { bearerAuth(ownerToken) }.status.value)
        assertEquals(true, mapper.readTree(client.get(path) { bearerAuth(ownerToken) }.bodyAsText()).get("note").isNull)
        assertEquals(404, client.delete(path) { bearerAuth(ownerToken) }.status.value)
    }

    private fun configureExternalMetadata(lastFm: LastFmService) {
        Mockito.`when`(lastFm.getArtistBio(
            Mockito.any(org.airsonic.player.domain.MediaFile::class.java), Mockito.any(java.util.Locale::class.java),
        )).thenReturn(ArtistBio("Artist biography", "artist-mbid", "https://last.fm/artist", "small", "medium", "large"))
        Mockito.`when`(lastFm.getAlbumNotes(Mockito.any(org.airsonic.player.domain.MediaFile::class.java)))
            .thenReturn(AlbumNotes("Album notes", "album-mbid", "https://last.fm/album", "small", "medium", "large"))
    }

    private suspend fun assertLibraryFolderList(
        client: HttpClient,
        mapper: com.fasterxml.jackson.databind.ObjectMapper,
        ownerToken: String,
        libraryId: Long,
    ) {
        val response = client.get("/api/v1/library/folders") { bearerAuth(ownerToken) }
        assertEquals(200, response.status.value, response.bodyAsText())
        assertEquals(libraryId, mapper.readTree(response.bodyAsText())[0].get("id").asLong())
        assertEquals(401, client.get("/api/v1/library/folders").status.value)
    }

    private suspend fun assertTrackMetadata(
        client: HttpClient,
        mapper: com.fasterxml.jackson.databind.ObjectMapper,
        ownerToken: String,
        otherToken: String,
        trackId: Long,
    ) {
        val response = client.get("/api/v1/tracks/$trackId/metadata") { bearerAuth(ownerToken) }
        assertEquals(200, response.status.value, response.bodyAsText())
        val metadata = mapper.readTree(response.bodyAsText())
        assertEquals("Artist biography", metadata.get("artist").get("biography").asText())
        assertEquals("Album notes", metadata.get("album").get("notes").asText())
        assertEquals(404, client.get("/api/v1/tracks/$trackId/metadata") { bearerAuth(otherToken) }.status.value)
    }

    private suspend fun assertIndependentMetadata(
        client: HttpClient,
        mapper: com.fasterxml.jackson.databind.ObjectMapper,
        ownerToken: String,
        otherToken: String,
        auth: LocalAuthService,
        otherId: Long,
    ) {
        val albumId = mapper.readTree(client.get("/api/v1/albums") { bearerAuth(ownerToken) }.bodyAsText())
            .get("items")[0].get("id").asText()
        val artistId = mapper.readTree(client.get("/api/v1/artists") { bearerAuth(ownerToken) }.bodyAsText())
            .get("items")[0].get("id").asText()
        val album = client.get("/api/v1/albums/$albumId/metadata") { bearerAuth(ownerToken) }
        assertEquals(200, album.status.value, album.bodyAsText())
        assertEquals("Album notes", mapper.readTree(album.bodyAsText()).get("notes").asText())
        val artist = client.get("/api/v1/artists/$artistId/metadata") { bearerAuth(ownerToken) }
        assertEquals(200, artist.status.value, artist.bodyAsText())
        assertEquals("Artist biography", mapper.readTree(artist.bodyAsText()).get("biography").asText())
        assertEquals(401, client.get("/api/v1/albums/$albumId/metadata").status.value)
        assertEquals(401, client.get("/api/v1/artists/$artistId/metadata").status.value)
        assertEquals(true, auth.setAllowedLibraries(otherId, emptySet()))
        assertEquals(404, client.get("/api/v1/albums/$albumId/metadata") { bearerAuth(otherToken) }.status.value)
        assertEquals(404, client.get("/api/v1/artists/$artistId/metadata") { bearerAuth(otherToken) }.status.value)
    }
}
