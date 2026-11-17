package org.airsonic.player.controller.rest.indexes

import io.ktor.http.Parameters

class IndexesRequest(
    val username: String,
    val parameters: Parameters,
    val folderId: Int?,
    val playerId: Int?,
)
