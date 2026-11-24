package su.nnedition.kord.api.module.album

data class AlbumPage(
    val items: List<AlbumSummaryResponse>,
    val count: Int,
    val total: Long,
    val limit: Int,
    val offset: Int,
)
