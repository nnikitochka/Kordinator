package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.SearchCriteria
import org.airsonic.player.domain.SearchResult
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.SearchService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.search.IndexType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.nio.file.Path

class KordinatorSearchRoutesTest {
    @Test
    fun searchesOnlyTracksAccessibleInTheUsersFolders() = testApplication {
        val searchService = Mockito.mock(SearchService::class.java)
        val mediaFolderService = Mockito.mock(MediaFolderService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
        val folder = MusicFolder(Path.of("/music"), "Music", MusicFolder.Type.MEDIA, true, null)
        val visibleTrack = MediaFile().apply {
            id = 31
            mediaType = MediaFile.MediaType.MUSIC
            title = "Open road"
            artist = "The Drivers"
        }
        val hiddenTrack = MediaFile().apply {
            id = 32
            mediaType = MediaFile.MediaType.MUSIC
            title = "Private track"
        }
        val searchResult = SearchResult().apply {
            offset = 5
            totalHits = 2
            mediaFiles.addAll(listOf(visibleTrack, hiddenTrack))
        }
        Mockito.`when`(mediaFolderService.getMusicFoldersForUser("alice")).thenReturn(listOf(folder))
        Mockito.`when`(
            searchService.search(Mockito.any(SearchCriteria::class.java), Mockito.eq(listOf(folder)), Mockito.eq(IndexType.SONG)),
        ).thenReturn(searchResult)
        Mockito.`when`(
            securityService.isFolderAccessAllowed(Mockito.any(MediaFile::class.java), Mockito.eq("alice")),
        ).thenAnswer { invocation -> (invocation.arguments[0] as MediaFile).id == 31 }
        val mapper = jacksonObjectMapper()
        application {
            routing {
                KordinatorSearchRoutes(searchService, mediaFolderService, securityService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = mapper,
                )
            }
        }

        val response = client.get("/api/v1/search?query=road&offset=5&limit=10") {
            headers.append("X-Test-User", "alice")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = mapper.readTree(response.bodyAsText())
        assertEquals("road", payload["query"].asText())
        assertEquals(5, payload["offset"].asInt())
        assertEquals(2, payload["totalHits"].asInt())
        assertEquals(1, payload["tracks"].size())
        assertEquals(31, payload["tracks"][0]["id"].asInt())
        val criteria = Mockito.mockingDetails(searchService).invocations.single().arguments[0] as SearchCriteria
        assertEquals("road", criteria.query)
        assertEquals(5, criteria.offset)
        assertEquals(10, criteria.count)
    }

    @Test
    fun rejectsMissingQueryAndUnauthenticatedSearch() = testApplication {
        val searchService = Mockito.mock(SearchService::class.java)
        val mediaFolderService = Mockito.mock(MediaFolderService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
        application {
            routing {
                KordinatorSearchRoutes(searchService, mediaFolderService, securityService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val unauthenticated = client.get("/api/v1/search?query=road")
        val missingQuery = client.get("/api/v1/search?query=%20") { headers.append("X-Test-User", "alice") }

        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)
        assertEquals(HttpStatusCode.BadRequest, missingQuery.status)
        Mockito.verifyNoInteractions(searchService, mediaFolderService)
    }
}
