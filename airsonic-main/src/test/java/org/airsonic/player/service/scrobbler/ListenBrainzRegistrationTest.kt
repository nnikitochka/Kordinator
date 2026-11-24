package org.airsonic.player.service.scrobbler

import com.sun.net.httpserver.HttpServer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Test
import java.time.Instant

class ListenBrainzRegistrationTest {
    @Test
    fun buildsSubmissionAndNowPlayingWithOptionalMetadata() {
        val registration = ListenBrainzRegistration(
            "http://example.test", "token", "Artist", null, "Track", "release", null, 3, 120,
            Instant.ofEpochSecond(12345), true,
        )
        assertEquals(mapOf("listen_type" to "single", "payload" to listOf(mapOf(
            "listened_at" to 12345L,
            "track_metadata" to mapOf("artist_name" to "Artist", "track_name" to "Track",
                "additional_info" to mapOf("release_mbid" to "release", "tracknumber" to 3)),
        ))), registration.payload())
        assertEquals(mapOf("listen_type" to "playing_now", "payload" to listOf(emptyMap<String, Any>())),
            registration.copy(artist = null, title = null, musicBrainzReleaseId = null, trackNumber = null,
                submission = false).payload())
    }
    @Test
    fun sendsBearerTokenAndJsonToConfiguredEndpoint() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val authorization = AtomicReference<String>()
        val body = AtomicReference<String>()
        server.createContext("/listen") { exchange ->
            authorization.set(exchange.requestHeaders.getFirst("Authorization"))
            body.set(exchange.requestBody.bufferedReader().use { it.readText() })
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            val registration = ListenBrainzRegistration(
                "http://127.0.0.1:${server.address.port}/listen", "secret", "Artist", null, "Track", null, null,
                null, 0, Instant.ofEpochSecond(42), true,
            )
            val method = ListenBrainzScrobbler::class.java.getDeclaredMethod("scrobble", ListenBrainzRegistration::class.java)
            method.isAccessible = true
            method.invoke(ListenBrainzScrobbler(), registration)
            assertEquals("token secret", authorization.get())
            val json = jacksonObjectMapper().readTree(body.get())
            assertEquals("single", json["listen_type"].asText())
            assertEquals(42L, json["payload"][0]["listened_at"].asLong())
            assertEquals("Track", json["payload"][0]["track_metadata"]["track_name"].asText())
        } finally {
            server.stop(0)
        }
    }
}
