package org.airsonic.player.controller.share

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Share

data class ShareInfo(
    val shareUrl: String,
    val share: Share,
    val dir: MediaFile
)
