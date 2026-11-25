package org.airsonic.player.service

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MediaFileComparator
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Arrays
import java.util.Collections
import java.util.function.Function
import java.util.stream.Collectors

/** Maintains and traverses the on-disk hierarchy of media files. */
internal class MediaFileChildren(private val service: MediaFileService) {
    fun getParentOf(mediaFile: MediaFile, minimizeDiskAccess: Boolean): MediaFile? {
        val parentPath = mediaFile.parentPath ?: return null
        return service.lookup.getMediaFile(Path.of(parentPath), service.mediaFolderService.getMusicFolderById(mediaFile.folderId)!!, minimizeDiskAccess)
    }

    fun getChildrenOf(
        parent: MediaFile,
        includeFiles: Boolean,
        includeDirectories: Boolean,
        sort: Boolean,
        minimizeDiskAccess: Boolean,
    ): MutableList<MediaFile> {
        if (!parent.isDirectory) return Collections.emptyList()

        var resultStream = if (!minimizeDiskAccess) updateChildren(parent)?.parallelStream() else null
        if (resultStream == null) {
            val folder = service.mediaFolderService.getMusicFolderById(parent.folderId)
            resultStream = service.mediaFileDao.getChildrenOf(parent.path!!, parent.folderId!!, true)
                .parallelStream()
                .map { service.lookup.checkLastModified(it, folder, minimizeDiskAccess)!! }
                .filter { service.metadata.includeMediaFile(it, folder!!) }
        }

        resultStream = resultStream.filter { (includeDirectories && it.isDirectory) || (includeFiles && it.isFile) }
        if (sort) resultStream = resultStream.sorted(MediaFileComparator(service.settingsService.isSortAlbumsByYear))
        return resultStream.collect(Collectors.toList())
    }

    fun isRoot(mediaFile: MediaFile): Boolean = StringUtils.isEmpty(mediaFile.path) &&
        service.mediaFolderService.getAllMusicFolders(true, true).parallelStream()
            .anyMatch { mediaFile.folderId == it.id }

    fun getDescendantsOf(ancestor: MediaFile, sort: Boolean): MutableList<MediaFile> {
        if (ancestor.isFile) return Arrays.asList(ancestor)
        val result = ArrayList<MediaFile>()
        for (child in getChildrenOf(ancestor, true, true, sort, service.settingsService.isFastCacheEnabled)) {
            if (child.isDirectory) result.addAll(getDescendantsOf(child, sort)) else result.add(child)
        }
        return result
    }

    private fun updateChildren(parent: MediaFile): List<MediaFile>? {
        if (parent.childrenLastUpdated!!.compareTo(parent.changed!!) >= 0) return null

        val storedChildrenMap: MutableMap<String, MediaFile> = service.mediaFileDao
            .getChildrenOf(parent.path!!, parent.folderId!!, false)
            .parallelStream()
            .collect(
                Collectors.toConcurrentMap(
                    Function<MediaFile, String> { it.path!! },
                    Function.identity<MediaFile>(),
                ),
            )
        val folder = service.mediaFolderService.getMusicFolderById(parent.folderId)!!
        try {
            Files.list(parent.getFullPath(folder.path)).use { children ->
                val result = children.parallel()
                    .filter { service.metadata.includeMediaFile(it) }
                    .filter { service.securityService.getMusicFolderForFile(it, true, true)!!.id == parent.folderId }
                    .map { folder.path.relativize(it) }
                    .map { relativePath ->
                        var mediaFile = storedChildrenMap.remove(relativePath.toString())
                        if (mediaFile == null) {
                            mediaFile = service.metadata.createMediaFile(relativePath, folder, null)
                            service.updateMediaFile(mediaFile!!)
                        } else {
                            mediaFile = service.lookup.checkLastModified(mediaFile, folder, false)!!
                        }
                        mediaFile
                    }
                    .collect(Collectors.toList())

                service.mediaFileDao.deleteMediaFiles(storedChildrenMap.keys, parent.folderId)
                parent.childrenLastUpdated = parent.changed
                parent.present = true
                service.updateMediaFile(parent)
                return result
            }
        } catch (exception: IOException) {
            log.warn("Could not retrieve and update all the children for {} in folder {}. Will skip", parent.path, folder.id, exception)
            return null
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(MediaFileChildren::class.java)
    }
}
