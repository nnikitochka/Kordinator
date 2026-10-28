package org.airsonic.player.domain

data class MediaFileWithUrlInfo(
    val file: MediaFile,
    val streamable: Boolean,
    val coverArtUrl: String,
    val streamUrl: String,
    val captionsUrl: String,
    val contentType: String
)
