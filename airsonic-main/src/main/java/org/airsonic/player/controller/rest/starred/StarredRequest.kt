package org.airsonic.player.controller.rest.starred

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

class StarredRequest(
    val username: String,
    val parameters: Parameters,
    val player: Player?,
)
