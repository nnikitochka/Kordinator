package su.nnedition.kord.api.module.artwork

data class ArtworkSelectionRequest(
    val artist: String?,
    val album: String?,
    val imageUrl: String,
)