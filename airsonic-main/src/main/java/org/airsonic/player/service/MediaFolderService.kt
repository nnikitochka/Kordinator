package org.airsonic.player.service

import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.dao.MusicFolderDao
import org.airsonic.player.domain.MusicFolder
import org.apache.commons.lang3.tuple.Triple
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.cache.CacheManager

/** Coordinates music-folder access, hierarchy changes and associated cache invalidation. */
class MediaFolderService @JvmOverloads constructor(
    private val musicFolderDao: MusicFolderDao,
    private val mediaFileDao: MediaFileDao,
    private val cacheManager: CacheManager? = null,
) {
    private var cachedMusicFolders: List<MusicFolder>? = null
    private val cachedMusicFoldersPerUser = ConcurrentHashMap<String, List<MusicFolder>>()

    fun getAllMusicFolders(): List<MusicFolder> = getAllMusicFolders(false, false)

    fun getAllMusicFolders(includeDisabled: Boolean, includeNonExisting: Boolean): List<MusicFolder> {
        val folders = cachedMusicFolders ?: musicFolderDao.getAllMusicFolders().also { cachedMusicFolders = it }
        return folders.filter { folder ->
            (includeDisabled || folder.isEnabled()) && (includeNonExisting || Files.exists(folder.path))
        }
    }

    fun getAllMusicFolders(
        includeDisabled: Boolean,
        includeNonExisting: Boolean,
        includeDeleted: Boolean,
    ): List<MusicFolder> = ArrayList<MusicFolder>().apply {
        addAll(getAllMusicFolders(includeDisabled, includeNonExisting))
        if (includeDeleted) addAll(getDeletedMusicFolders())
    }

    fun getMusicFoldersForUser(username: String): List<MusicFolder> =
        cachedMusicFoldersPerUser.computeIfAbsent(username) { user ->
            musicFolderDao.getMusicFoldersForUser(user).toMutableList().apply {
                retainAll(getAllMusicFolders(false, false))
            }
        }

    fun getMusicFoldersForUser(username: String, selectedMusicFolderId: Int?): List<MusicFolder> =
        getMusicFoldersForUser(username).filter { folder ->
            selectedMusicFolderId == null || selectedMusicFolderId < 0 || folder.id == selectedMusicFolderId
        }

    fun setMusicFoldersForUser(username: String, musicFolderIds: Collection<Int>) {
        musicFolderDao.setMusicFoldersForUser(username, musicFolderIds)
        cachedMusicFoldersPerUser.remove(username)
    }

    fun getMusicFolderById(id: Int?): MusicFolder? = getMusicFolderById(id, false, false)

    fun getMusicFolderById(id: Int?, includeDisabled: Boolean, includeNonExisting: Boolean): MusicFolder? =
        getAllMusicFolders(includeDisabled, includeNonExisting).firstOrNull { id == it.id }

    fun createMusicFolder(musicFolder: MusicFolder) {
        val overlaps = getMusicFolderPathOverlaps(musicFolder, getAllMusicFolders(true, true, true))
        if (overlaps.left.isNotEmpty()) {
            throw IllegalArgumentException(
                "Music folder with path ${musicFolder.path} overlaps with existing music folder path(s) " +
                    "(${logMusicFolderOverlap(overlaps)}) and can therefore not be created.",
            )
        }

        musicFolderDao.createMusicFolder(musicFolder)
        if (overlaps.middle.isNotEmpty()) {
            val ancestor = overlaps.middle[0]
            musicFolderDao.reassignChildren(ancestor, musicFolder)
            clearMediaFileCache()
        }
        overlaps.right.asSequence()
            .filter { it.id!! < 0 }
            .forEach { deletedFolder ->
                musicFolderDao.reassignChildren(deletedFolder, musicFolder)
                musicFolderDao.deleteMusicFolder(deletedFolder.id)
                clearMediaFileCache()
            }
        clearMusicFolderCache()
    }

    fun deleteMusicFolder(id: Int?) {
        if (mediaFileDao.getMediaFileCount(id) == 0) {
            musicFolderDao.deleteMusicFolder(id)
            clearMusicFolderCache()
            return
        }

        val folder = getMusicFolderById(id, true, true)!!
        val overlaps = getMusicFolderPathOverlaps(folder, getAllMusicFolders(true, true, true))
        if (overlaps.middle.isNotEmpty()) {
            musicFolderDao.reassignChildren(folder, overlaps.middle[0])
            musicFolderDao.deleteMusicFolder(id)
        }

        val deletedId = -id!! - 1
        musicFolderDao.updateMusicFolderId(id, deletedId)
        folder.id = deletedId
        folder.setEnabled(false)
        musicFolderDao.updateMusicFolder(folder)
        mediaFileDao.deleteMediaFiles(deletedId)
        clearMusicFolderCache()
        clearMediaFileCache()
    }

    fun expunge() {
        musicFolderDao.expungeMusicFolders()
    }

    fun updateMusicFolder(musicFolder: MusicFolder) {
        val allIncludingDeleted = getAllMusicFolders(true, true, true)
        val otherFolders = allIncludingDeleted.filter { it.id != musicFolder.id }
        val overlaps = getMusicFolderPathOverlaps(musicFolder, otherFolders)
        val existing = getAllMusicFolders(true, true).firstOrNull { it.id == musicFolder.id }
        if (existing != null && existing.path != musicFolder.path &&
            (overlaps.left.isNotEmpty() || overlaps.middle.isNotEmpty() || overlaps.right.isNotEmpty())
        ) {
            throw IllegalArgumentException(
                "Music folder with path ${musicFolder.path} overlaps with existing music folder path(s) " +
                    "(${logMusicFolderOverlap(overlaps)}) and can therefore not be updated.",
            )
        }
        musicFolderDao.updateMusicFolder(musicFolder)
        clearMusicFolderCache()
    }

    fun getDeletedMusicFolders(): List<MusicFolder> = musicFolderDao.getDeletedMusicFolders()

    fun clearMusicFolderCache() {
        cachedMusicFolders = null
        cachedMusicFoldersPerUser.clear()
    }

    fun clearMediaFileCache() {
        cacheManager?.getCache<Any, Any>(MEDIA_FILE_PATH_CACHE)?.removeAll()
        cacheManager?.getCache<Any, Any>(MEDIA_FILE_ID_CACHE)?.removeAll()
    }

    companion object {
        private const val MEDIA_FILE_PATH_CACHE = "mediaFilePathCache"
        private const val MEDIA_FILE_ID_CACHE = "mediaFileIdCache"

        @JvmStatic
        fun getMusicFolderPathOverlaps(
            folder: MusicFolder,
            allFolders: List<MusicFolder>,
        ): Triple<List<MusicFolder>, List<MusicFolder>, List<MusicFolder>> {
            val absolutePath = folder.path.normalize().toAbsolutePath()
            val sameFolders = allFolders.filter { candidate ->
                candidate.path.normalize().toAbsolutePath() == absolutePath && candidate.id != folder.id
            }
            val ancestors = allFolders.filter { candidate ->
                val candidatePath = candidate.path.normalize().toAbsolutePath()
                absolutePath.nameCount > candidatePath.nameCount && absolutePath.startsWith(candidatePath)
            }.sortedByDescending { it.path.nameCount }
            val descendants = allFolders.filter { candidate ->
                val candidatePath = candidate.path.normalize().toAbsolutePath()
                candidatePath.nameCount > absolutePath.nameCount && candidatePath.startsWith(absolutePath)
            }.sortedBy { it.path.nameCount }
            return Triple.of(sameFolders, ancestors, descendants)
        }

        @JvmStatic
        fun logMusicFolderOverlap(
            overlaps: Triple<List<MusicFolder>, List<MusicFolder>, List<MusicFolder>>,
        ): String = buildString {
            append("SAME: ")
            append(overlaps.left.joinToString(",", "[", "]") { it.name })
            append(", ANCESTOR: ")
            append(overlaps.middle.joinToString(",", "[", "]") { it.name })
            append(", DESCENDANT: ")
            append(overlaps.right.joinToString(",", "[", "]") { it.name })
        }
    }
}
