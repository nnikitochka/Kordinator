package su.nnedition.kord.api.module.admin.status

data class NowPlayingResponse(
    val transferId: String?,
    val playerId: Int?,
    val trackId: Int?,
    val username: String?,
    val artist: String?,
    val title: String?,
    val minutesAgo: Long,
)
