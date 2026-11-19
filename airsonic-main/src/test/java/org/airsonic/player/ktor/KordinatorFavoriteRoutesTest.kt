package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SecurityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.nio.file.Path

class KordinatorFavoriteRoutesTest {
    @Test
    fun listsAndUpdatesOnlyTheAuthenticatedUsersFavoriteTracks() = testApplication {
        val mediaFileDao = Mockito.mock(MediaFileDao::class.java)
        val mediaFileService = Mockito.mock(MediaFileService::class.java)
        val mediaFolderService = Mockito.mock(MediaFolderService::class.java)
        val security = Mockito.mock(SecurityService::class.java)
        val folder = MusicFolder(3, Path.of("/music"), "Music", MusicFolder.Type.MEDIA, true, null)
        val track = MediaFile().apply {
            id = 12
            mediaType = MediaFile.MediaType.MUSIC
            title = "Track"
            artist = "Artist"
            albumName = "Album"
            duration = 185.0
            format = "mp3"
        }
        Mockito.`when`(mediaFolderService.getMusicFoldersForUser("alice")).thenReturn(listOf(folder))
        Mockito.`when`(mediaFileDao.getStarredFiles(0, 10, "alice", listOf(folder))).thenReturn(listOf(track))
        Mockito.`when`(security.isFolderAccessAllowed(track, "alice")).thenReturn(true)
        Mockito.`when`(mediaFileService.getMediaFile(12)).thenReturn(track)
        val mapper = jacksonObjectMapper()
        application {
            routing {
                KordinatorFavoriteRoutes(mediaFileDao, mediaFileService, mediaFolderService, security).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = mapper,
                )
            }
        }

        val listed = client.get("/api/v1/favorites/tracks?limit=10") {
            headers.append("X-Test-User", "alice")
        }
        val added = client.put("/api/v1/tracks/12/favorite") {
            headers.append("X-Test-User", "alice")
        }
        val removed = client.delete("/api/v1/tracks/12/favorite") {
            headers.append("X-Test-User", "alice")
        }

        assertEquals(HttpStatusCode.OK, listed.status)
        val response = mapper.readTree(listed.bodyAsText())
        assertEquals(0, response["offset"].asInt())
        assertEquals(10, response["limit"].asInt())
        assertEquals(12, response["tracks"][0]["id"].asInt())
        assertEquals(HttpStatusCode.NoContent, added.status)
        assertEquals(HttpStatusCode.NoContent, removed.status)
        Mockito.verify(mediaFileDao).starMediaFile(12, "alice")
        Mockito.verify(mediaFileDao).unstarMediaFile(12, "alice")
    }

    @Test
    fun rejectsUnauthenticatedAndInaccessibleTrackChanges() = testApplication {
        val mediaFileDao = Mockito.mock(MediaFileDao::class.java)
        val mediaFileService = Mockito.mock(MediaFileService::class.java)
        val mediaFolderService = Mockito.mock(MediaFolderService::class.java)
        val security = Mockito.mock(SecurityService::class.java)
        val track = MediaFile().apply {
            id = 12
            mediaType = MediaFile.MediaType.MUSIC
        }
        Mockito.`when`(mediaFileService.getMediaFile(12)).thenReturn(track)
        Mockito.`when`(security.isFolderAccessAllowed(track, "alice")).thenReturn(false)
        application {
            routing {
                KordinatorFavoriteRoutes(mediaFileDao, mediaFileService, mediaFolderService, security).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val anonymous = client.put("/api/v1/tracks/12/favorite")
        val inaccessible = client.put("/api/v1/tracks/12/favorite") {
            headers.append("X-Test-User", "alice")
        }

        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertEquals(HttpStatusCode.NotFound, inaccessible.status)
        Mockito.verifyNoInteractions(mediaFileDao)
    }
}
