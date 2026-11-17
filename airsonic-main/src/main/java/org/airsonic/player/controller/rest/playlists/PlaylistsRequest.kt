package org.airsonic.player.controller.rest.playlists

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player
import org.airsonic.player.security.CallerContext

data class PlaylistsRequest(
    val username: String,
    val callerContext: CallerContext,
    val admin: Boolean,
    val parameters: Parameters,
    val id: Int?,
    val player: Player?,
)
