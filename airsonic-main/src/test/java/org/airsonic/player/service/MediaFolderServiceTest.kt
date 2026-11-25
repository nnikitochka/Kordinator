package org.airsonic.player.service

import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.dao.MusicFolderDao
import org.airsonic.player.domain.MusicFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.nio.file.Path

class MediaFolderServiceTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun filtersDisabledAndMissingFoldersUnlessRequested() {
        val existingPath = Files.createDirectory(tempDirectory.resolve("enabled"))
        val disabledPath = Files.createDirectory(tempDirectory.resolve("disabled"))
        val existing = folder(1, existingPath, "enabled", true)
        val disabled = folder(2, disabledPath, "disabled", false)
        val missing = folder(3, tempDirectory.resolve("missing"), "missing", true)
        val dao = mock(MusicFolderDao::class.java)
        `when`(dao.getAllMusicFolders()).thenReturn(listOf(existing, disabled, missing))
        val service = MediaFolderService(dao, mock(MediaFileDao::class.java))

        assertEquals(listOf(existing), service.getAllMusicFolders())
        assertEquals(listOf(existing, disabled), service.getAllMusicFolders(includeDisabled = true, includeNonExisting = false))
        assertEquals(3, service.getAllMusicFolders(includeDisabled = true, includeNonExisting = true).size)
    }

    @Test
    fun cachesPerUserFoldersAndInvalidatesThatCacheAfterAssignmentsChange() {
        val firstPath = Files.createDirectory(tempDirectory.resolve("first"))
        val secondPath = Files.createDirectory(tempDirectory.resolve("second"))
        val first = folder(1, firstPath, "first", true)
        val second = folder(2, secondPath, "second", true)
        val dao = mock(MusicFolderDao::class.java)
        `when`(dao.getAllMusicFolders()).thenReturn(listOf(first, second))
        `when`(dao.getMusicFoldersForUser("alice")).thenReturn(listOf(first), listOf(second))
        val service = MediaFolderService(dao, mock(MediaFileDao::class.java))

        assertEquals(listOf(first), service.getMusicFoldersForUser("alice"))
        assertEquals(listOf(first), service.getMusicFoldersForUser("alice"))
        service.setMusicFoldersForUser("alice", setOf(2))
        assertEquals(listOf(second), service.getMusicFoldersForUser("alice"))
        verify(dao, org.mockito.Mockito.times(2)).getMusicFoldersForUser("alice")
    }

    @Test
    fun detectsExactPathsAncestorsAndDescendantsInNearestFirstOrder() {
        val rootPath = tempDirectory.resolve("music")
        val ancestorPath = rootPath.resolve("artist")
        val targetPath = ancestorPath.resolve("album")
        val childPath = targetPath.resolve("disc")
        val deepChildPath = childPath.resolve("track")
        val target = folder(3, targetPath, "target", true)
        val same = folder(4, targetPath, "same", true)
        val root = folder(1, rootPath, "root", true)
        val ancestor = folder(2, ancestorPath, "ancestor", true)
        val child = folder(5, childPath, "child", true)
        val deepChild = folder(6, deepChildPath, "deep-child", true)

        val overlaps = MediaFolderService.getMusicFolderPathOverlaps(
            target,
            listOf(root, deepChild, same, child, ancestor, target),
        )

        assertEquals(listOf(same), overlaps.left)
        assertEquals(listOf(ancestor, root), overlaps.middle)
        assertEquals(listOf(child, deepChild), overlaps.right)
        assertEquals("SAME: [same], ANCESTOR: [ancestor,root], DESCENDANT: [child,deep-child]", MediaFolderService.logMusicFolderOverlap(overlaps))
    }

    @Test
    fun refusesCreatingFolderWithAnExactPathMatch() {
        val path = Files.createDirectory(tempDirectory.resolve("music"))
        val existing = folder(1, path, "existing", true)
        val duplicate = folder(2, path, "duplicate", true)
        val dao = mock(MusicFolderDao::class.java)
        `when`(dao.getAllMusicFolders()).thenReturn(listOf(existing))
        val service = MediaFolderService(dao, mock(MediaFileDao::class.java))

        assertThrows(IllegalArgumentException::class.java) {
            service.createMusicFolder(duplicate)
        }
        verify(dao, never()).createMusicFolder(duplicate)
    }

    @Test
    fun creatingNestedFolderReassignsItsAncestorTree() {
        val ancestorPath = Files.createDirectory(tempDirectory.resolve("music"))
        val childPath = Files.createDirectory(ancestorPath.resolve("new-library"))
        val ancestor = folder(1, ancestorPath, "music", true)
        val child = folder(2, childPath, "new-library", true)
        val dao = mock(MusicFolderDao::class.java)
        `when`(dao.getAllMusicFolders()).thenReturn(listOf(ancestor))
        `when`(dao.getDeletedMusicFolders()).thenReturn(emptyList())
        val service = MediaFolderService(dao, mock(MediaFileDao::class.java))

        service.createMusicFolder(child)

        verify(dao).createMusicFolder(child)
        verify(dao).reassignChildren(ancestor, child)
    }

    @Test
    fun deletingNonEmptyNestedFolderReassignsItsTreeAndMarksItDeleted() {
        val ancestorPath = Files.createDirectory(tempDirectory.resolve("music"))
        val childPath = Files.createDirectory(ancestorPath.resolve("old-library"))
        val ancestor = folder(1, ancestorPath, "music", true)
        val child = folder(2, childPath, "old-library", true)
        val musicFolderDao = mock(MusicFolderDao::class.java)
        val mediaFileDao = mock(MediaFileDao::class.java)
        `when`(musicFolderDao.getAllMusicFolders()).thenReturn(listOf(ancestor, child))
        `when`(musicFolderDao.getDeletedMusicFolders()).thenReturn(emptyList())
        `when`(mediaFileDao.getMediaFileCount(2)).thenReturn(3)
        val service = MediaFolderService(musicFolderDao, mediaFileDao)

        service.deleteMusicFolder(2)

        verify(musicFolderDao).reassignChildren(child, ancestor)
        verify(musicFolderDao).deleteMusicFolder(2)
        verify(musicFolderDao).updateMusicFolderId(2, -3)
        verify(musicFolderDao).updateMusicFolder(child)
        assertEquals(-3, child.id)
        assertEquals(false, child.isEnabled())
        verify(mediaFileDao).deleteMediaFiles(-3)
    }

    private fun folder(id: Int, path: Path, name: String, enabled: Boolean) =
        MusicFolder(id, path, name, MusicFolder.Type.MEDIA, enabled, null)
}
