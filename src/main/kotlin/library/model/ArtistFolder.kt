package su.nnedition.kord.library.model

import java.io.File

data class ArtistFolder(
    val path: File,
    val name: String,
    val cover: File?,
    val albumsFolders: List<AlbumFolder>,
)