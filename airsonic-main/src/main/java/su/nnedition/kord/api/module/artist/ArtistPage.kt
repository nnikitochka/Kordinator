package su.nnedition.kord.api.module.artist

data class ArtistPage(
    val items: List<ArtistSummaryResponse>,
    val count: Int,
    val total: Long,
    val limit: Int,
    val offset: Int,
)