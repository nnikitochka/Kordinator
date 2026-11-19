package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.SecurityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class KordinatorTrackRoutesTest {
    @Test
    fun rejectsRequestsWithoutAnAuthenticatedUser() = testApplication {
        var lookupCalled = false
        application {
            routing {
                kordinatorTrackRoutes(
                    trackLookup = KordinatorTrackLookup { _, _ ->
                        lookupCalled = true
                        null
                    },
                    currentUsername = { null },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val response = client.get("/api/v1/tracks/12")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertFalse(lookupCalled)
    }

    @Test
    fun rejectsInvalidIdsAndReturnsTrackJsonForAccessibleTrack() = testApplication {
        application {
            routing {
                kordinatorTrackRoutes(
                    trackLookup = KordinatorTrackLookup { id, username ->
                        if (id == 12 && username == "alice") {
                            KordinatorTrackResponse(12, "Track", "Artist", "Album", "Artist", 1, 4, 2024, 215, 4096, "flac")
                        } else {
                            null
                        }
                    },
                    currentUsername = { "alice" },
                    objectMapper = jacksonObjectMapper(),
                )
            }
        }

        val invalidResponse = client.get("/api/v1/tracks/0")
        val trackResponse = client.get("/api/v1/tracks/12")

        assertEquals(HttpStatusCode.BadRequest, invalidResponse.status)
        assertEquals(HttpStatusCode.OK, trackResponse.status)
        assertEquals("application/json", trackResponse.headers["Content-Type"]?.substringBefore(';'))
        assertEquals(
            """{"id":12,"title":"Track","artist":"Artist","album":"Album","albumArtist":"Artist","discNumber":1,"trackNumber":4,"year":2024,"durationSeconds":215,"fileSize":4096,"format":"flac"}""",
            trackResponse.bodyAsText(),
        )
    }

    @Test
    fun mediaFileLookupUsesExistingLibraryAndHidesFilesOutsideUsersFolders() {
        val mediaFileService = Mockito.mock(MediaFileService::class.java)
        val securityService = Mockito.mock(SecurityService::class.java)
        val mediaFile = MediaFile().apply {
            id = 12
            mediaType = MediaFile.MediaType.MUSIC
            title = "Track"
            artist = "Artist"
            albumName = "Album"
            albumArtist = "Artist"
            discNumber = 1
            trackNumber = 4
            year = 2024
            duration = 215.8
            fileSize = 4096L
            format = "flac"
        }
        Mockito.`when`(mediaFileService.getMediaFile(12)).thenReturn(mediaFile)
        Mockito.`when`(securityService.isFolderAccessAllowed(mediaFile, "alice")).thenReturn(true)
        Mockito.`when`(securityService.isFolderAccessAllowed(mediaFile, "bob")).thenReturn(false)
        val lookup = MediaFileTrackLookup(mediaFileService, securityService)

        val accessible = lookup.findAccessibleTrack(12, "alice")
        val inaccessible = lookup.findAccessibleTrack(12, "bob")

        assertTrue(accessible?.title == "Track")
        assertEquals(215, accessible?.durationSeconds)
        assertNull(inaccessible)
    }
}
