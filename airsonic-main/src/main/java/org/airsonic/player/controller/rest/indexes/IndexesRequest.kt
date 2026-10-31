package org.airsonic.player.controller.rest.indexes

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

class IndexesRequest(
    val username: String,
    val parameters: Parameters,
    val folderId: Int?,
    val lastModified: () -> Long,
    val player: () -> Player,
)
