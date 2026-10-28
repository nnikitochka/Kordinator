package org.airsonic.player.controller.rest.info.album

import io.ktor.http.Parameters

data class AlbumInfoRequest(
    val parameters: Parameters,
    val id: Int?,
)
