package su.nnedition.kord.service.media

import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.util.FileUtil
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** Resolves media-file paths and refreshes stale database records. */
internal class MediaFileLookup(private val service: MediaFileService) {
    fun getMediaFile(fullPath: Path, minimizeDiskAccess: Boolean): MediaFile? {
        val folder = service.securityService.getMusicFolderForFile(fullPath, true, true) ?: return null
        return try {
            getMediaFile(folder.path.relativize(fullPath), folder, minimizeDiskAccess)
        } catch (_: Exception) {
            null
        }
    }

    fun getMediaFile(relativePath: Path, folder: MusicFolder, minimizeDiskAccess: Boolean): MediaFile? {
        val key = "${relativePath}-${folder.id}"
        if (service.memoryCacheEnabled) {
            service.mediaFilePathCache?.get(key)?.let { return it }
        }

        var result = service.mediaFileDao.getMediaFile(relativePath.toString(), folder.id!!)
        if (result != null) {
            result = checkLastModified(result, folder, minimizeDiskAccess)
            if (result != null && service.memoryCacheEnabled) service.mediaFilePathCache?.put(key, result)
            return result
        }

        if (!Files.exists(folder.path.resolve(relativePath))) return null

        result = service.metadata.createMediaFile(relativePath, folder, null)
        service.updateMediaFile(result!!)
        if (service.memoryCacheEnabled) service.mediaFilePathCache?.put(key, result)
        return result
    }

    fun getMediaFile(id: Int): MediaFile? {
        if (service.memoryCacheEnabled) service.mediaFileIdCache?.get(id)?.let { return it }
        val mediaFile = service.mediaFileDao.getMediaFile(id) ?: return null
        val folder = service.mediaFolderService.getMusicFolderById(mediaFile.folderId)
        val result = checkLastModified(mediaFile, folder, service.settingsService.isFastCacheEnabled())
        if (result != null && service.memoryCacheEnabled) service.mediaFileIdCache?.put(id, result)
        return result
    }

    fun updateMediaFile(mediaFile: MediaFile) {
        if (service.mediaFilePathCache != null && mediaFile.path != null) {
            service.mediaFilePathCache.remove("${mediaFile.path}-${mediaFile.folderId}")
        }
        if (service.mediaFileIdCache != null && mediaFile.id != null) {
            service.mediaFileIdCache.remove(mediaFile.id)
        }
        service.mediaFileDao.createOrUpdateMediaFile(mediaFile) { file ->
            val folder = service.mediaFolderService.getMusicFolderById(mediaFile.folderId)
            if (folder != null) {
                val musicFileInfo = service.mediaFileDao.getMusicFileInfo(file.getFullPath(folder.path).toString())
                if (musicFileInfo != null) {
                    file.comment = musicFileInfo.comment
                    file.lastPlayed = musicFileInfo.lastPlayed
                    file.playCount = musicFileInfo.playCount
                }
            }
        }
        service.coverArtService.persistIfNeeded(mediaFile)
    }

    internal fun checkLastModified(
        mediaFile: MediaFile,
        folder: MusicFolder?,
        minimizeDiskAccess: Boolean,
    ): MediaFile? {
        if (minimizeDiskAccess) return mediaFile
        val actualFolder = folder!!
        if (Files.notExists(mediaFile.getFullPath(actualFolder.path))) {
            log.info(
                "Marking media file (id {}, path {} in folder {}) as absent since it no longer exists on disk",
                mediaFile.id,
                mediaFile.path,
                actualFolder.name,
            )
            mediaFile.present = false
            mediaFile.childrenLastUpdated = Instant.ofEpochMilli(1)
            updateMediaFile(mediaFile)
            return mediaFile
        }

        if (mediaFile.getVersion() >= MediaFileDao.VERSION &&
            !service.settingsService.getFullScan() &&
            mediaFile.changed!!.compareTo(FileUtil.lastModified(mediaFile.getFullPath(actualFolder.path))) > -1
        ) {
            log.debug(
                "Detected unmodified file (id {}, path {} in folder {} ({}))",
                mediaFile.id,
                mediaFile.path,
                actualFolder.id,
                actualFolder.name,
            )
            return mediaFile
        }
        log.debug(
            "Updating database file from disk (id {}, path {} in folder {} ({}))",
            mediaFile.id,
            mediaFile.path,
            actualFolder.id,
            actualFolder.name,
        )
        val updated = service.metadata.createMediaFile(mediaFile.relativePath, actualFolder, mediaFile)
        updateMediaFile(updated!!)
        return updated
    }

    private companion object {
        val log = LoggerFactory.getLogger(MediaFileLookup::class.java)
    }
}
