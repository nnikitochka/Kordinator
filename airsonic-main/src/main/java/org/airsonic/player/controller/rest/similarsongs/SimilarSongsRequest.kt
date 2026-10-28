package org.airsonic.player.controller.rest.similarsongs

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

data class SimilarSongsRequest(
    val username: String,
    val parameters: Parameters,
    val id: Int?,
    val player: () -> Player,
)
