package su.nnedition.kord.library.model

import java.io.File

data class AlbumFolder(
    val artistFolderPath: File,
    val name: String,
    val cover: Cover,
    val tracks: List<TrackFile>
) {
    val folder = File(artistFolderPath, name)
}