package su.nnedition.kord.api.module.share

data class PublicShareResponse(
    val description: String?,
    val expiresAt: String?,
    val tracks: List<PublicShareTrackResponse>,
)