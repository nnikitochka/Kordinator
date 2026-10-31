package org.airsonic.player.controller.rest.bookmarks

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

class BookmarksRequest(
    val username: String,
    val parameters: Parameters,
    val id: Int?,
    val player: Player?,
)
