package su.nnedition.kord.api.module.favorite.album

import su.nnedition.kord.api.module.album.AlbumSummaryResponse

data class FavoriteAlbumPage(
    val items: List<AlbumSummaryResponse>,
    val count: Int,
    val total: Long,
    val limit: Int,
    val offset: Int,
)
