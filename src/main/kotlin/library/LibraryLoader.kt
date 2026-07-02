package su.nnedition.kord.library

import su.nnedition.kord.library.model.AlbumFolder
import su.nnedition.kord.library.model.ArtistFolder
import su.nnedition.kord.library.model.Cover
import su.nnedition.kord.library.model.TrackFile
import su.nnedition.kord.library.tags.AudioTagsManager
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.walk
import kotlin.sequences.forEach
import kotlin.time.Clock
import kotlin.time.Instant

class LibraryLoader(
    val rootDir: File
) {
    init {
        if (!rootDir.exists() || !rootDir.isDirectory) {
            throw IllegalArgumentException("Library root dir does not exist")
        }
    }

//    fun scanFull(): Result<LibraryScanResult> {
//        val result = scanLibrary()
//    }

    fun scanLibrary(): Result<LibraryScanResult> {
        if (!rootDir.exists())
            return Result.failure(RuntimeException("Library directory does not exist"))

        val rootFiles = rootDir.listFiles() ?: return Result.failure(IllegalArgumentException("Library is empty"))
        val artists = rootFiles.filter { it.isDirectory }

        val artistFolders = artists.map { artistFile ->
            val artistFolderFile = artistFile.absoluteFile
            val albumFiles = artistFile.listFiles { it.isDirectory } ?: emptyArray()

            val albumFolders = albumFiles.mapNotNull { albumFile ->
                val albumFolderFiles = albumFile.listFiles() ?: return@mapNotNull null

                val maybeAlbumCover = albumFolderFiles.filter { it.extension in coverExtensions }
                val cover: Cover = if (maybeAlbumCover.isEmpty()) {
                    Cover.None()
                } else if (maybeAlbumCover.size == 1) {
                    Cover.FileCover(maybeAlbumCover.first())
                } else {
                    val albumCover = maybeAlbumCover.firstOrNull { it.name == albumCoverFileName }
                    if (albumCover != null) {
                        Cover.FileCover(albumCover)
                    } else {
                        Cover.FileCover(maybeAlbumCover.first())
                    }
                }

                val trackFiles = albumFolderFiles.filter { it.isFile && it.extension in musicExtensions }
                val tracks = trackFiles.map { file ->
//                    println(createRelativePath(file.path))
                    TrackFile(file)
                }

                AlbumFolder(artistFolderFile, albumFile.name, cover, tracks)
            }

            ArtistFolder(artistFolderFile, artistFile.name, null, albumFolders)
        }

        return Result.success(LibraryScanResult(
            artistFolders,
        ))
    }

    private val roodDirLength = rootDir.path.length
    private fun createRelativePath(path: String): String = path.substring(roodDirLength+1)

    private companion object {
        val musicExtensions = listOf("mp3", "wav", "flac")
        val coverExtensions = listOf("png", "jpg", "jpeg", "gif")

        val artistCoverFileName = "artist"
        val albumCoverFileName = "albumart"
    }
}