package org.airsonic.player.controller.rest.music.song

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player
import org.airsonic.player.security.CallerContext

class SongRequest(
    val username: String,
    val callerContext: CallerContext,
    val player: Player,
    val parameters: Parameters,
)
