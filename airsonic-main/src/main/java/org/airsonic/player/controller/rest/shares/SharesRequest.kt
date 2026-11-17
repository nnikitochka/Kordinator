package org.airsonic.player.controller.rest.shares

import io.ktor.http.Parameters
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.Share
import org.airsonic.player.domain.User
import org.airsonic.player.security.CallerContext

class SharesRequest(
    val parameters: Parameters,
    val user: User,
    val callerContext: CallerContext,
    val player: Player?,
    val id: String?,
    val createShare: (List<MediaFile>) -> Share,
    val shareUrl: (Share) -> String,
)
