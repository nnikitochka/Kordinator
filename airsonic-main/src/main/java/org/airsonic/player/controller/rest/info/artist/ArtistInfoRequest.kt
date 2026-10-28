package org.airsonic.player.controller.rest.info.artist

import io.ktor.http.Parameters
import java.util.Locale

data class ArtistInfoRequest(
    val username: String,
    val parameters: Parameters,
    val id: Int?,
    val locale: () -> Locale,
)
