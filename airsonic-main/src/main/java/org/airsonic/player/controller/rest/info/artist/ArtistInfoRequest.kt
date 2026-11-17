package org.airsonic.player.controller.rest.info.artist

import io.ktor.http.Parameters
import org.airsonic.player.security.CallerContext
import java.util.Locale

data class ArtistInfoRequest(
    val username: String,
    val callerContext: CallerContext,
    val parameters: Parameters,
    val id: Int?,
    val locale: () -> Locale,
)
