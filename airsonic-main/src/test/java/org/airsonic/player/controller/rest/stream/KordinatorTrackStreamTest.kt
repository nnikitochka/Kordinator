package org.airsonic.player.controller.rest.stream

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.User
import org.airsonic.player.controller.rest.RestPlayerResolver
import org.airsonic.player.ktor.KordinatorSessionService
import org.airsonic.player.ktor.KordinatorUserPrincipal
import org.airsonic.player.security.CallerContext
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaStreamService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.SecurityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.time.Instant

class KordinatorTrackStreamTest {
    @Test
    fun servesPartialAudioFromTheExistingStreamService() = testApplication {
        val streams = Mockito.mock(
            MediaStreamService::class.java,
            org.mockito.stubbing.Answer<MediaStreamService.Result?> { invocation ->
                val notModified = invocation.getArgument<java.util.function.BiPredicate<String?, Long>>(12)
                    .test("6", Instant.parse("2026-03-01T00:00:00Z").toEpochMilli())
                if (notModified) null else {
                    MediaStreamService.Result(ByteArrayInputStream("abcdef".toByteArray()), 6, "audio/mpeg", emptyMap())
                }
            },
        )
        val sessions = Mockito.mock(KordinatorSessionService::class.java)
        val security = Mockito.mock(SecurityService::class.java)
        val mediaFiles = Mockito.mock(MediaFileService::class.java)
        val players = Mockito.mock(PlayerService::class.java)
        val playerResolver = Mockito.mock(RestPlayerResolver::class.java)
        val context = CallerContext("alice", emptySet(), emptySet(), true)
        val player = Player()
        val user = User("alice", null, false, 0, 0, 0, setOf(User.Role.STREAM))
        val file = MediaFile().apply {
            id = 12
            mediaType = MediaFile.MediaType.MUSIC
            changed = Instant.parse("2026-03-01T00:00:00Z")
        }
        Mockito.`when`(sessions.authenticate("server-token"))
            .thenReturn(KordinatorUserPrincipal("alice", setOf("STREAM")))
        Mockito.`when`(security.getUserByName("alice")).thenReturn(user)
        Mockito.`when`(security.createCallerContext("alice", true)).thenReturn(context)
        Mockito.`when`(security.isFolderAccessAllowed(file, context)).thenReturn(true)
        Mockito.`when`(mediaFiles.getMediaFile(12)).thenReturn(file)
        Mockito.`when`(playerResolver.getOrCreatePlayerId(
            Mockito.eq("alice"), Mockito.eq("desktop"), Mockito.anyString(), Mockito.eq(false),
        )).thenReturn(77)
        Mockito.`when`(players.getPlayerById(77)).thenReturn(player)
        application {
            routing {
                KordinatorTrackStreamRoutes(
                    StreamRestController(streams), sessions, security, mediaFiles, players, playerResolver,
                ).routes(this)
            }
        }

        val response = client.get("/api/v1/tracks/12/stream") {
            headers.append(HttpHeaders.Range, "bytes=1-3")
            headers.append(HttpHeaders.Authorization, "Bearer server-token")
            url { parameters.append("c", "desktop") }
        }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertEquals("bytes 1-3/6", response.headers[HttpHeaders.ContentRange])
        assertEquals("\"6\"", response.headers[HttpHeaders.ETag])
        assertEquals("Sun, 1 Mar 2026 00:00:00 GMT", response.headers[HttpHeaders.LastModified])
        assertEquals("bcd", response.bodyAsText())

        val notModified = client.get("/api/v1/tracks/12/stream") {
            headers.append(HttpHeaders.Authorization, "Bearer server-token")
            headers.append(HttpHeaders.IfNoneMatch, "\"6\"")
            url { parameters.append("c", "desktop") }
        }
        assertEquals(HttpStatusCode.NotModified, notModified.status)
        assertEquals("", notModified.bodyAsText())
    }

    @Test
    fun requiresBearerAuthenticationBeforeLookingUpTrack() = testApplication {
        val sessions = Mockito.mock(KordinatorSessionService::class.java)
        Mockito.`when`(sessions.authenticate(null)).thenReturn(null)
        val mediaFiles = Mockito.mock(MediaFileService::class.java)
        val security = Mockito.mock(SecurityService::class.java)
        val playerService = Mockito.mock(PlayerService::class.java)
        val playerResolver = Mockito.mock(RestPlayerResolver::class.java)
        val streams = Mockito.mock(MediaStreamService::class.java)

        application {
            routing {
                KordinatorTrackStreamRoutes(
                    StreamRestController(streams), sessions, security, mediaFiles, playerService, playerResolver,
                ).routes(this)
            }
        }

        val response = client.get("/api/v1/tracks/12/stream")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        Mockito.verifyNoInteractions(mediaFiles, security, playerService, playerResolver, streams)
    }
}
