package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
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
        application {
            routing {
                KordinatorPlaylistRoutes(playlistService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = jacksonObjectMapper(),
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
}
