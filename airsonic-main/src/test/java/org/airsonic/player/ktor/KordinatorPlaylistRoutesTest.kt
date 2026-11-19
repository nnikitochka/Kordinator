package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.airsonic.player.domain.Playlist
import org.airsonic.player.service.PlaylistService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Instant

class KordinatorPlaylistRoutesTest {
    @Test
    fun listsOnlyPlaylistsVisibleThroughTheExistingPlaylistService() = testApplication {
        val playlistService = Mockito.mock(PlaylistService::class.java)
        val playlist = Playlist().apply {
            id = 7
            name = "Road trip"
            fileCount = 3
            duration = 615.0
            created = Instant.parse("2026-01-02T03:04:05Z")
            changed = Instant.parse("2026-01-03T04:05:06Z")
        }
        Mockito.`when`(playlistService.getReadablePlaylistsForUser("alice")).thenReturn(listOf(playlist))
        Mockito.`when`(playlistService.getReadablePlaylistsForUser("bob")).thenReturn(emptyList())
        val mapper = jacksonObjectMapper()
        application {
            routing {
                KordinatorPlaylistRoutes(playlistService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = mapper,
                )
            }
        }

        val unauthenticated = client.get("/api/v1/playlists")
        val alice = client.get("/api/v1/playlists") { headers.append("X-Test-User", "alice") }
        val bob = client.get("/api/v1/playlists") { headers.append("X-Test-User", "bob") }

        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)
        assertEquals(HttpStatusCode.OK, alice.status)
        assertEquals(
            """[{"id":7,"name":"Road trip","trackCount":3,"durationSeconds":615,"createdAt":"2026-01-02T03:04:05Z","updatedAt":"2026-01-03T04:05:06Z"}]""",
            alice.bodyAsText(),
        )
        assertEquals("[]", bob.bodyAsText())
    }

    @Test
    fun createsAnOwnedPrivatePlaylistForTheAuthenticatedUser() = testApplication {
        val playlistService = Mockito.mock(PlaylistService::class.java)
        val mapper = jacksonObjectMapper()
        Mockito.doAnswer { invocation ->
            (invocation.arguments[0] as Playlist).id = 41
            null
        }.`when`(playlistService).createPlaylist(Mockito.any(Playlist::class.java))
        application {
            routing {
                KordinatorPlaylistRoutes(playlistService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = mapper,
                )
            }
        }

        val response = client.post("/api/v1/playlists") {
            headers.append("X-Test-User", "alice")
            contentType(ContentType.Application.Json)
            setBody("""{"name":" Road trip "}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val payload = mapper.readTree(response.bodyAsText())
        assertEquals(41, payload["id"].asInt())
        assertEquals("Road trip", payload["name"].asText())
        assertEquals(0, payload["trackCount"].asInt())
        assertEquals(payload["createdAt"].asText(), payload["updatedAt"].asText())
        Instant.parse(payload["createdAt"].asText())
        val created = Mockito.mockingDetails(playlistService).invocations
            .single { it.method.name == "createPlaylist" }
            .arguments[0] as Playlist
        assertEquals("alice", created.username)
        assertEquals(false, created.shared)
    }

    @Test
    fun rejectsUnauthenticatedAndInvalidCreateRequests() = testApplication {
        val playlistService = Mockito.mock(PlaylistService::class.java)
        application {
            routing {
                KordinatorPlaylistRoutes(playlistService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val unauthenticated = client.post("/api/v1/playlists") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test"}""")
        }
        val invalid = client.post("/api/v1/playlists") {
            headers.append("X-Test-User", "alice")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"  "}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        Mockito.verify(playlistService, Mockito.never()).createPlaylist(Mockito.any(Playlist::class.java))
    }
}
