package org.airsonic.player.controller.rest.playlists

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

data class PlaylistsRequest(
    val username: String,
    val admin: Boolean,
    val parameters: Parameters,
    val id: Int?,
    val player: Player?,
)
