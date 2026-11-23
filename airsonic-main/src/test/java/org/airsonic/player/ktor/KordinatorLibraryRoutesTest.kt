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
import org.airsonic.player.security.LocalUserPrincipal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.nio.file.Path

class KordinatorLibraryRoutesTest {
    @Test
    fun listsAllowedFoldersAndPagesRootDirectoryEntries() = testApplication {
        val mediaFiles = Mockito.mock(MediaFileService::class.java)
        val folders = Mockito.mock(MediaFolderService::class.java)
        val folder = MusicFolder(3, Path.of("/music"), "My Music", MusicFolder.Type.MEDIA, true, null)
        val root = MediaFile().apply {
            id = 30
            folderId = 3
            mediaType = MediaFile.MediaType.DIRECTORY
            path = ""
        }
        val subdirectory = MediaFile().apply {
            id = 31
            folderId = 3
            mediaType = MediaFile.MediaType.DIRECTORY
            path = "Albums"
            title = "Albums"
        }
        val track = MediaFile().apply {
            id = 41
            folderId = 3
            mediaType = MediaFile.MediaType.MUSIC
            path = "single.mp3"
            title = "Single"
            artist = "Artist"
            duration = 100.0
        }
        Mockito.`when`(folders.getAllMusicFolders(false, false)).thenReturn(listOf(folder))
        Mockito.`when`(mediaFiles.getMediaFile("", 3)).thenReturn(root)
        Mockito.`when`(mediaFiles.getChildrenOf(root, true, true, true)).thenReturn(listOf(subdirectory, track))
        val mapper = jacksonObjectMapper()
        application {
            routing {
                KordinatorLibraryRoutes(mediaFiles, folders).routes(
                    this,
                    currentUser = { call -> if (call.request.headers["X-Test-User"] == "alice") LocalUserPrincipal(1, "alice", "USER", setOf(3)) else null },
                    objectMapper = mapper,
                )
            }
        }

        val folderList = client.get("/api/v1/library/folders") {
            headers.append("X-Test-User", "alice")
        }
        val contents = client.get("/api/v1/library/folders/3?limit=1") {
            headers.append("X-Test-User", "alice")
        }

        assertEquals(HttpStatusCode.OK, folderList.status)
        assertEquals(3, mapper.readTree(folderList.bodyAsText())[0]["id"].asInt())
        assertEquals("My Music", mapper.readTree(folderList.bodyAsText())[0]["name"].asText())
        assertEquals(HttpStatusCode.OK, contents.status)
        val page = mapper.readTree(contents.bodyAsText())
        assertEquals(30, page["id"].asInt())
        assertEquals(3, page["folderId"].asInt())
        assertEquals(true, page["hasMore"].asBoolean())
        assertEquals("directory", page["entries"][0]["type"].asText())
        assertEquals(31, page["entries"][0]["id"].asInt())
    }

    @Test
    fun hidesDirectoriesOutsideTheUsersAllowedFolders() = testApplication {
        val mediaFiles = Mockito.mock(MediaFileService::class.java)
        val folders = Mockito.mock(MediaFolderService::class.java)
        val folder = MusicFolder(3, Path.of("/music"), "My Music", MusicFolder.Type.MEDIA, true, null)
        val directory = MediaFile().apply {
            id = 31
            folderId = 4
            mediaType = MediaFile.MediaType.DIRECTORY
        }
        Mockito.`when`(mediaFiles.getMediaFile(31)).thenReturn(directory)
        Mockito.`when`(folders.getAllMusicFolders(false, false)).thenReturn(listOf(folder))
        application {
            routing {
                KordinatorLibraryRoutes(mediaFiles, folders).routes(
                    this,
                    currentUser = { call -> if (call.request.headers["X-Test-User"] == "alice") LocalUserPrincipal(1, "alice", "USER", setOf(3)) else null },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val anonymous = client.get("/api/v1/library/directories/31")
        val inaccessible = client.get("/api/v1/library/directories/31") {
            headers.append("X-Test-User", "alice")
        }

        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertEquals(HttpStatusCode.NotFound, inaccessible.status)
        Mockito.verify(folders).getAllMusicFolders(false, false)
        Mockito.verify(mediaFiles).getMediaFile(31)
    }
}
