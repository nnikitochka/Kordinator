package org.airsonic.player.controller.rest.stream

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.User
import org.airsonic.player.security.CallerContext
import org.airsonic.player.service.MediaStreamService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.util.Optional
import java.util.function.BiPredicate

class KordinatorTrackStreamTest {
    @Test
    fun servesPartialAudioFromTheExistingStreamService() = testApplication {
        val streams = Mockito.mock(MediaStreamService::class.java)
        val context = CallerContext("alice", emptySet(), emptySet(), true)
        val player = Mockito.mock(Player::class.java)
        val user = Mockito.mock(User::class.java)
        val video = MediaStreamService.VideoOptions(null, null, null, null)
        val notModified = BiPredicate<String?, Long> { _, _ -> false }
        val request = StreamRestController.Request(
            false, context, player, user, null, null, null, 12, null, null, video, null, notModified, { 200 },
        )
        val result = MediaStreamService.Result(ByteArrayInputStream("abcdef".toByteArray()), 6, "audio/mpeg", emptyMap())
        Mockito.`when`(
            streams.open(
                request.jwtAuthenticated,
                request.callerContext,
                request.player,
                request.user,
                request.format,
                request.suffix,
                Optional.ofNullable(request.bitRate),
                Optional.ofNullable(request.id),
                Optional.ofNullable(request.path),
                request.offset,
                request.video,
                request.icyMetadata,
                request.notModified,
            ),
        ).thenReturn(result)

        application {
            routing {
                StreamRestController(streams).routes(this) { request }
            }
        }

        val response = client.get("/api/v1/tracks/12/stream") {
            headers.append(HttpHeaders.Range, "bytes=1-3")
        }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertEquals("bytes 1-3/6", response.headers[HttpHeaders.ContentRange])
        assertEquals("bcd", response.bodyAsText())
    }
}
