package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.airsonic.player.domain.Playlist
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Instant

class KordinatorPlaylistRoutesTest {
    @Test
    fun listsOnlyPlaylistsVisibleThroughTheExistingPlaylistService() = testApplication {
        val playlistService = Mockito.mock(PlaylistService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
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
                KordinatorPlaylistRoutes(playlistService, securityService).routes(
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
    fun returnsReadablePlaylistAndOnlyTracksAccessibleToTheUser() = testApplication {
        val playlistService = Mockito.mock(PlaylistService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
        val playlist = Playlist().apply {
            id = 7
            username = "alice"
            name = "Road trip"
            duration = 615.0
            created = Instant.parse("2026-01-02T03:04:05Z")
            changed = Instant.parse("2026-01-03T04:05:06Z")
        }
        val visibleTrack = MediaFile().apply {
            id = 15
            mediaType = MediaFile.MediaType.MUSIC
            title = "Open road"
            artist = "The Drivers"
            duration = 200.0
        }
        val hiddenTrack = MediaFile().apply {
            id = 16
            mediaType = MediaFile.MediaType.MUSIC
            title = "Private track"
            duration = 400.0
        }
        Mockito.`when`(playlistService.getPlaylist(7)).thenReturn(playlist)
        Mockito.`when`(playlistService.isReadAllowed(playlist, "alice")).thenReturn(true)
        Mockito.`when`(playlistService.getFilesInPlaylist(7)).thenReturn(listOf(visibleTrack, hiddenTrack))
        Mockito.`when`(
            securityService.isFolderAccessAllowed(Mockito.any(MediaFile::class.java), Mockito.eq("alice")),
        ).thenAnswer { invocation -> (invocation.arguments[0] as MediaFile).id == 15 }

        val mapper = jacksonObjectMapper()
        application {
            routing {
                KordinatorPlaylistRoutes(playlistService, securityService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = mapper,
                )
            }
        }

        val response = client.get("/api/v1/playlists/7") { headers.append("X-Test-User", "alice") }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = mapper.readTree(response.bodyAsText())
        assertEquals("Road trip", payload["name"].asText())
        assertEquals(1, payload["trackCount"].asInt())
        assertEquals(200, payload["durationSeconds"].asInt())
        assertEquals(15, payload["tracks"][0]["id"].asInt())
        assertEquals("Open road", payload["tracks"][0]["title"].asText())
    }

    @Test
    fun hidesPlaylistsThatTheUserCannotRead() = testApplication {
        val playlistService = Mockito.mock(PlaylistService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
        val playlist = Playlist().apply {
            id = 7
            username = "bob"
            name = "Private"
        }
        Mockito.`when`(playlistService.getPlaylist(7)).thenReturn(playlist)
        Mockito.`when`(playlistService.isReadAllowed(playlist, "alice")).thenReturn(false)
        application {
            routing {
                KordinatorPlaylistRoutes(playlistService, securityService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val response = client.get("/api/v1/playlists/7") { headers.append("X-Test-User", "alice") }

        assertEquals(HttpStatusCode.NotFound, response.status)
        Mockito.verify(playlistService, Mockito.never()).getFilesInPlaylist(7)
    }

    @Test
    fun updatesPlaylistMetadataOnlyForItsOwner() = testApplication {
        val playlistService = Mockito.mock(PlaylistService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
        val playlist = Playlist().apply {
            id = 7
            username = "alice"
            name = "Old name"
        }
        Mockito.`when`(playlistService.getPlaylist(7)).thenReturn(playlist)
        Mockito.`when`(playlistService.isWriteAllowed(playlist, "alice")).thenReturn(true)
        val mapper = jacksonObjectMapper()
        application {
            routing {
                KordinatorPlaylistRoutes(playlistService, securityService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = mapper,
                )
            }
        }

        val response = client.put("/api/v1/playlists/7") {
            headers.append("X-Test-User", "alice")
            contentType(ContentType.Application.Json)
            setBody("""{"name":" New name ","comment":" Road music "}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = mapper.readTree(response.bodyAsText())
        assertEquals(7, payload["id"].asInt())
        assertEquals("New name", payload["name"].asText())
        val updated = Mockito.mockingDetails(playlistService).invocations
            .single { it.method.name == "updatePlaylist" }
            .arguments[0] as Playlist
        assertEquals("alice", updated.username)
        assertEquals("Road music", updated.comment)
    }

    @Test
    fun rejectsPlaylistUpdateFromNonOwner() = testApplication {
        val playlistService = Mockito.mock(PlaylistService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
        val playlist = Playlist().apply {
            id = 7
            username = "bob"
            name = "Private"
        }
        Mockito.`when`(playlistService.getPlaylist(7)).thenReturn(playlist)
        Mockito.`when`(playlistService.isWriteAllowed(playlist, "alice")).thenReturn(false)
        application {
            routing {
                KordinatorPlaylistRoutes(playlistService, securityService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val response = client.put("/api/v1/playlists/7") {
            headers.append("X-Test-User", "alice")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Hijacked"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        Mockito.verify(playlistService, Mockito.never()).updatePlaylist(Mockito.any(Playlist::class.java))
    }

    @Test
    fun deletesPlaylistOnlyForItsOwner() = testApplication {
        val playlistService = Mockito.mock(PlaylistService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
        val playlist = Playlist().apply {
            id = 7
            username = "alice"
            name = "To delete"
        }
        Mockito.`when`(playlistService.getPlaylist(7)).thenReturn(playlist)
        Mockito.`when`(playlistService.isWriteAllowed(playlist, "alice")).thenReturn(true)
        application {
            routing {
                KordinatorPlaylistRoutes(playlistService, securityService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val response = client.delete("/api/v1/playlists/7") { headers.append("X-Test-User", "alice") }

        assertEquals(HttpStatusCode.NoContent, response.status)
        Mockito.verify(playlistService).deletePlaylist(7)
    }

    @Test
    fun rejectsPlaylistDeletionFromNonOwner() = testApplication {
        val playlistService = Mockito.mock(PlaylistService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
        val playlist = Playlist().apply {
            id = 7
            username = "bob"
            name = "Private"
        }
        Mockito.`when`(playlistService.getPlaylist(7)).thenReturn(playlist)
        Mockito.`when`(playlistService.isWriteAllowed(playlist, "alice")).thenReturn(false)
        application {
            routing {
                KordinatorPlaylistRoutes(playlistService, securityService).routes(
                    this,
                    currentUsername = { call -> call.request.headers["X-Test-User"] },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val response = client.delete("/api/v1/playlists/7") { headers.append("X-Test-User", "alice") }

        assertEquals(HttpStatusCode.NotFound, response.status)
        Mockito.verify(playlistService, Mockito.never()).deletePlaylist(7)
    }

    @Test
    fun createsAnOwnedPrivatePlaylistForTheAuthenticatedUser() = testApplication {
        val playlistService = Mockito.mock(PlaylistService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
        val mapper = jacksonObjectMapper()
        Mockito.doAnswer { invocation ->
            (invocation.arguments[0] as Playlist).id = 41
            null
        }.`when`(playlistService).createPlaylist(Mockito.any(Playlist::class.java))
        application {
            routing {
                KordinatorPlaylistRoutes(playlistService, securityService).routes(
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
        val securityService = Mockito.mock(SecurityService::class.java)
        application {
            routing {
                KordinatorPlaylistRoutes(playlistService, securityService).routes(
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
