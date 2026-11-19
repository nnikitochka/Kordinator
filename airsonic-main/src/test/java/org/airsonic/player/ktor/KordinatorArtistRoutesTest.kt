package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.domain.Album
import org.airsonic.player.domain.Artist
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.service.MediaFolderService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.nio.file.Path

class KordinatorArtistRoutesTest {
    @Test
    fun listsArtistsFromTheCurrentUsersMusicFolders() = testApplication {
        val artistDao = Mockito.mock(ArtistDao::class.java)
        val albumDao = Mockito.mock(AlbumDao::class.java)
        val mediaFolderService = Mockito.mock(MediaFolderService::class.java)
        val folder = MusicFolder(Path.of("/music"), "Music", MusicFolder.Type.MEDIA, true, null)
        val artist = Artist(4, "The Drivers", 3, null, true, 1)
        Mockito.`when`(mediaFolderService.getMusicFoldersForUser("alice")).thenReturn(listOf(folder))
        Mockito.`when`(artistDao.getAlphabetialArtists(10, 25, listOf(folder))).thenReturn(listOf(artist))
        application {
            routing {
                KordinatorArtistRoutes(artistDao, albumDao, mediaFolderService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val response = client.get("/api/v1/artists?offset=10&limit=25") {
            headers.append("X-Test-User", "alice")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""[{"id":4,"name":"The Drivers","albumCount":3}]""", response.bodyAsText())
    }

    @Test
    fun rejectsUnauthenticatedAndInvalidArtistListRequests() = testApplication {
        val artistDao = Mockito.mock(ArtistDao::class.java)
        val albumDao = Mockito.mock(AlbumDao::class.java)
        val mediaFolderService = Mockito.mock(MediaFolderService::class.java)
        application {
            routing {
                KordinatorArtistRoutes(artistDao, albumDao, mediaFolderService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val unauthenticated = client.get("/api/v1/artists")
        val invalid = client.get("/api/v1/artists?offset=-1") { headers.append("X-Test-User", "alice") }

        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        Mockito.verifyNoInteractions(mediaFolderService, artistDao, albumDao)
    }

    @Test
    fun returnsArtistAlbumsOnlyAfterCheckingTheArtistsFolder() = testApplication {
        val artistDao = Mockito.mock(ArtistDao::class.java)
        val albumDao = Mockito.mock(AlbumDao::class.java)
        val mediaFolderService = Mockito.mock(MediaFolderService::class.java)
        val folder = MusicFolder(1, Path.of("/music"), "Music", MusicFolder.Type.MEDIA, true, null)
        val artist = Artist(4, "The Drivers", 1, null, true, 1)
        val album = Album().apply {
            id = 17
            name = "Night Drive"
            this.artist = "The Drivers"
            year = 2024
            genre = "Electronic"
            songCount = 9
            duration = 2400.0
        }
        Mockito.`when`(mediaFolderService.getMusicFoldersForUser("alice")).thenReturn(listOf(folder))
        Mockito.`when`(artistDao.getArtist(4)).thenReturn(artist)
        Mockito.`when`(albumDao.getAlbumsForArtist("The Drivers", listOf(folder))).thenReturn(listOf(album))
        val mapper = jacksonObjectMapper()
        application {
            routing {
                KordinatorArtistRoutes(artistDao, albumDao, mediaFolderService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = mapper,
                )
            }
        }

        val response = client.get("/api/v1/artists/4") { headers.append("X-Test-User", "alice") }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = mapper.readTree(response.bodyAsText())
        assertEquals("The Drivers", payload["name"].asText())
        assertEquals(1, payload["albumCount"].asInt())
        assertEquals("Night Drive", payload["albums"][0]["name"].asText())
        assertEquals(9, payload["albums"][0]["trackCount"].asInt())
    }

    @Test
    fun hidesArtistOutsideTheUsersMusicFolders() = testApplication {
        val artistDao = Mockito.mock(ArtistDao::class.java)
        val albumDao = Mockito.mock(AlbumDao::class.java)
        val mediaFolderService = Mockito.mock(MediaFolderService::class.java)
        val folder = MusicFolder(1, Path.of("/music"), "Music", MusicFolder.Type.MEDIA, true, null)
        val artist = Artist(4, "Private Artist", 2, null, true, 2)
        Mockito.`when`(mediaFolderService.getMusicFoldersForUser("alice")).thenReturn(listOf(folder))
        Mockito.`when`(artistDao.getArtist(4)).thenReturn(artist)
        application {
            routing {
                KordinatorArtistRoutes(artistDao, albumDao, mediaFolderService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val response = client.get("/api/v1/artists/4") { headers.append("X-Test-User", "alice") }

        assertEquals(HttpStatusCode.NotFound, response.status)
        Mockito.verifyNoInteractions(albumDao)
    }
}
