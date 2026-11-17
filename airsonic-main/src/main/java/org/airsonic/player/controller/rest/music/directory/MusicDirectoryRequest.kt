package org.airsonic.player.controller.rest.music.directory

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player
import org.airsonic.player.security.CallerContext

class MusicDirectoryRequest(
    val username: String,
    val callerContext: CallerContext,
    val player: Player,
    val parameters: Parameters,
)
