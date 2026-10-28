package org.airsonic.player.controller.rest.artists

import io.ktor.http.Parameters

/** Authenticated user and merged query/form parameters from the temporary servlet adapter. */
data class ArtistsRequest(val username: String, val parameters: Parameters)
