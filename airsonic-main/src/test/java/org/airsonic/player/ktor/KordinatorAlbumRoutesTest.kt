package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SecurityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.nio.file.Path

class KordinatorAlbumRoutesTest {
    @Test
    fun listsAlbumsFromUserFoldersAndFiltersInaccessibleFolders() = testApplication {
        val mediaFileService = Mockito.mock(MediaFileService::class.java)
        val mediaFolderService = Mockito.mock(MediaFolderService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
        val musicFolder = MusicFolder(Path.of("/music"), "Music", MusicFolder.Type.MEDIA, true, null)
        val visible = MediaFile().apply {
            id = 21
            folderId = 1
            mediaType = MediaFile.MediaType.ALBUM
            albumName = "Night Drive"
            artist = "The Drivers"
            year = 2024
            genre = "Electronic"
            duration = 321.0
        }
        val hidden = MediaFile().apply {
            id = 22
            folderId = 2
            mediaType = MediaFile.MediaType.ALBUM
            albumName = "Private Album"
        }
        Mockito.`when`(mediaFolderService.getMusicFoldersForUser("alice")).thenReturn(listOf(musicFolder))
        Mockito.`when`(
            mediaFileService.getAlphabeticalAlbums(5, 10, true, listOf(musicFolder)),
        ).thenReturn(listOf(visible, hidden))
        Mockito.`when`(
            securityService.isFolderAccessAllowed(Mockito.any(MediaFile::class.java), Mockito.eq("alice")),
        ).thenAnswer { invocation -> (invocation.arguments[0] as MediaFile).id == 21 }

        val mapper = jacksonObjectMapper()
        application {
            routing {
                KordinatorAlbumRoutes(mediaFileService, mediaFolderService, securityService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = mapper,
                )
            }
        }

        val response = client.get("/api/v1/albums?offset=5&limit=10&sort=artist") {
            headers.append("X-Test-User", "alice")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            """[{"id":21,"name":"Night Drive","artist":"The Drivers","year":2024,"genre":"Electronic","durationSeconds":321}]""",
            response.bodyAsText(),
        )
    }

    @Test
    fun rejectsInvalidPaginationAndUnauthenticatedRequests() = testApplication {
        val mediaFileService = Mockito.mock(MediaFileService::class.java)
        val mediaFolderService = Mockito.mock(MediaFolderService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
        application {
            routing {
                KordinatorAlbumRoutes(mediaFileService, mediaFolderService, securityService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val unauthenticated = client.get("/api/v1/albums")
        val invalid = client.get("/api/v1/albums?limit=1000") { headers.append("X-Test-User", "alice") }

        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        Mockito.verifyNoInteractions(mediaFolderService, mediaFileService)
    }
}
