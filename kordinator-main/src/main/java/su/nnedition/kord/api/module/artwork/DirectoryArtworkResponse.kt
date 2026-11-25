package su.nnedition.kord.api.module.artwork

data class DirectoryArtworkResponse(
    val directoryId: Int,
    val path: String,
    val contentType: String,
    val sizeBytes: Int,
)
