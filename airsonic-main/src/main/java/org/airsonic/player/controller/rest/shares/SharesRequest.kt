package org.airsonic.player.controller.rest.shares

import io.ktor.http.Parameters
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.Share
import org.airsonic.player.domain.User

class SharesRequest(
    val parameters: Parameters,
    val user: User,
    val player: Player?,
    val id: String?,
    val createShare: (List<MediaFile>) -> Share,
    val shareUrl: (Share) -> String,
)
