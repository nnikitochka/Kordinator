package su.nnedition.kord.api.module.metadata

data class TrackExternalMetadataResponse(
    val trackId: Long,
    val artist: ArtistExternalMetadataResponse?,
    val album: AlbumExternalMetadataResponse?,
)
