package org.airsonic.player.controller.rest.jukebox

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

class JukeboxRequest(
    val username: String,
    val authorized: Boolean,
    val player: Player?,
    val parameters: Parameters,
)
