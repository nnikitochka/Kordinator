package su.nnedition.kord.api.module.favorite.artist

import su.nnedition.kord.api.module.artist.ArtistSummaryResponse

data class FavoriteArtistPage(
    val items: List<ArtistSummaryResponse>,
    val count: Int,
    val total: Long,
    val limit: Int,
    val offset: Int,
)
