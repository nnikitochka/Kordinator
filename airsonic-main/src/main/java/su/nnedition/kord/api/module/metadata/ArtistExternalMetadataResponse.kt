package su.nnedition.kord.api.module.metadata

data class ArtistExternalMetadataResponse(
    val biography: String?,
    val musicBrainzId: String?,
    val lastFmUrl: String?,
    val smallImageUrl: String?,
    val mediumImageUrl: String?,
    val largeImageUrl: String?,
)
