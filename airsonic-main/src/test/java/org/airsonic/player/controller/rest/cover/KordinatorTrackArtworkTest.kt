package org.airsonic.player.controller.rest.cover

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.airsonic.player.ktor.KordinatorTrackLookup
import org.airsonic.player.ktor.KordinatorTrackResponse
import org.airsonic.player.service.CoverArtImageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream

class KordinatorTrackArtworkTest {
    @Test
    fun returnsExistingArtworkOnlyForAnAccessibleTrack() = testApplication {
        val images = Mockito.mock(CoverArtImageService::class.java)
        val imageBytes = "image-bytes".toByteArray()
        Mockito.`when`(images.getImage("5", null, null)).thenReturn(
            CoverArtImageService.ImageResult(ByteArrayInputStream(imageBytes), "image/png"),
        )
        application {
            routing {
                CoverArtRestController(images).kordinatorTrackRoutes(
                    this,
                    trackLookup = KordinatorTrackLookup { id, username ->
                        if (id == 5 && username == "alice") {
                            KordinatorTrackResponse(5, "Track", "Artist", "Album", "Artist", null, null, null, 0, null, null)
                        } else {
                            null
                        }
                    },
                    currentUsername = { call -> if (call.request.headers["X-Test-User"] == "alice") "alice" else null },
                )
            }
        }

        val unauthenticated = client.get("/api/v1/tracks/5/artwork")
        val invalid = client.get("/api/v1/tracks/nope/artwork") {
            headers.append("X-Test-User", "alice")
        }
        val inaccessible = client.get("/api/v1/tracks/6/artwork") {
            headers.append("X-Test-User", "alice")
        }
        val artwork = client.get("/api/v1/tracks/5/artwork") {
            headers.append("X-Test-User", "alice")
        }

        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertEquals(HttpStatusCode.NotFound, inaccessible.status)
        assertEquals(HttpStatusCode.OK, artwork.status)
        assertEquals("image/png", artwork.headers["Content-Type"]?.substringBefore(';'))
        assertEquals(imageBytes.decodeToString(), artwork.bodyAsText())
    }
}
