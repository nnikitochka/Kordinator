package org.airsonic.player.controller.rest.albums

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

class AlbumsRequest(
    val username: String,
    val parameters: Parameters,
    val id: Int?,
    val player: Player?,
)
