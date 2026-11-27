package su.nnedition.kord.service.media

import com.google.common.io.MoreFiles
import su.nnedition.kord.domain.CoverArt
import su.nnedition.kord.domain.CoverArt.EntityType
import su.nnedition.kord.domain.CoverArtSource
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.service.metadata.JaudiotaggerParser
import su.nnedition.kord.service.metadata.MetaData
import su.nnedition.kord.util.FileUtil
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import java.util.stream.Collectors

/** Builds media-file records from filesystem entries and embedded metadata. */
internal class MediaFileMetadataFactory(private val service: MediaFileService) {
    fun includeMediaFile(candidate: MediaFile, folder: MusicFolder): Boolean =
        includeMediaFile(candidate.getFullPath(folder.path))

    fun includeMediaFile(candidate: Path): Boolean {
        val suffix = MoreFiles.getFileExtension(candidate).lowercase(Locale.getDefault())
        return !isExcluded(candidate) &&
            (Files.isDirectory(candidate) || isAudioFile(suffix) || isVideoFile(suffix))
    }

    fun createMediaFile(relativePath: Path, folder: MusicFolder, existingFile: MediaFile?): MediaFile? {
        val file = folder.path.resolve(relativePath)
        if (!Files.exists(file)) {
            if (existingFile != null) {
                existingFile.present = false
                existingFile.childrenLastUpdated = Instant.ofEpochMilli(1)
            }
            return existingFile
        }

        val mediaFile = MediaFile()
        val lastModified = FileUtil.lastModified(file)
        mediaFile.path = relativePath.toString()
        mediaFile.folderId = folder.id
        val actualFolder = service.securityService.getMusicFolderForFile(file, true, true)!!
        if (actualFolder.id != folder.id) {
            log.warn(
                "Inconsistent Mediafile folder for media file with path: {}, folder id should be {} and is instead {}",
                file,
                actualFolder.id,
                folder.id,
            )
        }
        mediaFile.parentPath = if (relativePath.toString().isNotEmpty()) {
            relativePath.parent?.toString() ?: ""
        } else {
            null
        }
        mediaFile.changed = lastModified
        mediaFile.lastScanned = Instant.now()
        mediaFile.playCount = existingFile?.playCount ?: 0
        mediaFile.lastPlayed = existingFile?.lastPlayed
        mediaFile.comment = existingFile?.comment
        mediaFile.childrenLastUpdated = Instant.ofEpochMilli(1)
        mediaFile.created = lastModified
        mediaFile.mediaType = MediaFile.MediaType.DIRECTORY
        mediaFile.present = true
        mediaFile.id = existingFile?.id

        if (Files.isRegularFile(file)) {
            val parser = service.metaDataParserFactory.getParser(file)
            if (parser != null) applyMetadata(mediaFile, parser.getMetaData(file))
            mediaFile.format = StringUtils.trimToNull(StringUtils.lowerCase(FilenameUtils.getExtension(mediaFile.path)))
            mediaFile.fileSize = FileUtil.size(file)
            mediaFile.mediaType = getMediaType(mediaFile)
        } else if (!service.children.isRoot(mediaFile)) {
            createDirectoryMetadata(mediaFile, file, folder)
        } else {
            mediaFile.title = folder.name
        }
        return mediaFile
    }

    fun refreshMediaFile(mediaFile: MediaFile, folder: MusicFolder) {
        val refreshed = createMediaFile(mediaFile.relativePath, folder, mediaFile)
        service.updateMediaFile(refreshed!!)
    }

    private fun createDirectoryMetadata(mediaFile: MediaFile, file: Path, folder: MusicFolder) {
        try {
            Files.list(file).use { childStream ->
                val children = childStream.parallel().collect(Collectors.toList())
                val firstFile = children.parallelStream()
                    .filter(::includeMediaFile)
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .orElse(null)
                if (firstFile == null) {
                    mediaFile.artist = file.fileName.toString()
                    return
                }

                mediaFile.mediaType = MediaFile.MediaType.ALBUM
                service.metaDataParserFactory.getParser(firstFile)?.let { parser ->
                    applyAlbumMetadata(mediaFile, parser.getMetaData(firstFile))
                }
                findCoverArt(children)?.let { coverArt ->
                    mediaFile.art = CoverArt(
                        -1,
                        EntityType.MEDIA_FILE,
                        folder.path.relativize(coverArt).toString(),
                        folder.id,
                        false,
                    )
                }
            }
        } catch (exception: IOException) {
            log.warn("Could not retrieve children for {}.", file, exception)
            mediaFile.artist = file.fileName.toString()
        }
    }

    private fun applyMetadata(mediaFile: MediaFile, metadata: MetaData) {
        mediaFile.artist = metadata.artist
        mediaFile.albumArtist = metadata.albumArtist
        mediaFile.albumName = metadata.albumName
        mediaFile.title = metadata.title
        mediaFile.discNumber = metadata.discNumber
        mediaFile.trackNumber = metadata.trackNumber
        mediaFile.genre = metadata.genre
        mediaFile.year = metadata.year
        mediaFile.releaseDate = metadata.releaseDate
        mediaFile.duration = metadata.duration
        mediaFile.bitRate = metadata.bitRate
        mediaFile.variableBitRate = metadata.variableBitRate
        mediaFile.height = metadata.height
        mediaFile.width = metadata.width
        mediaFile.musicBrainzReleaseId = metadata.musicBrainzReleaseId
        mediaFile.musicBrainzRecordingId = metadata.musicBrainzRecordingId
    }

    private fun applyAlbumMetadata(mediaFile: MediaFile, metadata: MetaData) {
        mediaFile.artist = metadata.albumArtist
        mediaFile.albumName = metadata.albumName
        mediaFile.year = metadata.year
        mediaFile.releaseDate = metadata.releaseDate
        mediaFile.genre = metadata.genre
    }

    private fun getMediaType(mediaFile: MediaFile): MediaFile.MediaType {
        if (isVideoFile(mediaFile.format)) return MediaFile.MediaType.VIDEO
        val path = mediaFile.path!!.lowercase(Locale.getDefault())
        val genre = StringUtils.trimToEmpty(mediaFile.genre).lowercase(Locale.getDefault())
        if (listOf("audiobook", "audio book").any { path.contains(it) || genre.contains(it) }) {
            return MediaFile.MediaType.AUDIOBOOK
        }
        return MediaFile.MediaType.MUSIC
    }

    private fun isAudioFile(suffix: String): Boolean = service.settingsService.getMusicFileTypesSet().contains(suffix)

    private fun isVideoFile(suffix: String?): Boolean =
        service.settingsService.getVideoFileTypesSet().contains(suffix!!.lowercase(Locale.getDefault()))

    private fun isExcluded(file: Path): Boolean {
        if (service.settingsService.getIgnoreSymLinks() && Files.isSymbolicLink(file)) {
            log.info("excluding symbolic link {}", file)
            return true
        }
        val name = file.fileName.toString()
        val excludePattern = service.settingsService.getExcludePattern()
        if (excludePattern != null && excludePattern.matcher(name).find()) {
            log.info("excluding file which matches exclude pattern {}: {}", service.settingsService.getExcludePatternString(), file)
            return true
        }
        return (name.startsWith(".") && !name.startsWith("..")) || name.startsWith("@eaDir") || name == "Thumbs.db"
    }

    private fun findCoverArt(candidates: Collection<Path>): Path? {
        return when (service.settingsService.getCoverArtSource()) {
            CoverArtSource.TAG_FILE -> findTagCover(candidates) ?: findFileCover(candidates)
            CoverArtSource.FILE -> findFileCover(candidates)
            CoverArtSource.TAG -> findTagCover(candidates)
            else -> findFileCover(candidates) ?: findTagCover(candidates)
        }
    }

    private fun findFileCover(candidates: Collection<Path>): Path? {
        for (mask in service.settingsService.getCoverArtFileTypesSet()) {
            val candidate = candidates.parallelStream().filter { path ->
                val name = path.fileName.toString().lowercase(Locale.getDefault())
                name.endsWith(mask) && !name.startsWith(".") && Files.isRegularFile(path)
            }.findAny().orElse(null)
            if (candidate != null) return candidate
        }
        return null
    }

    private fun findTagCover(candidates: Collection<Path>): Path? = candidates.stream()
        .filter(service.parser::isApplicable)
        .filter(JaudiotaggerParser::isImageAvailable)
        .findFirst()
        .orElse(null)

    private companion object {
        val log = LoggerFactory.getLogger(MediaFileMetadataFactory::class.java)
    }
}
