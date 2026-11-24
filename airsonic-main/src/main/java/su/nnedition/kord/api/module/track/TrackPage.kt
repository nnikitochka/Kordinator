package su.nnedition.kord.api.module.track

data class TrackPage(
    val items: List<TrackResponse>,
    val count: Int,
    val total: Long,
    val limit: Int,
    val offset: Int,
)