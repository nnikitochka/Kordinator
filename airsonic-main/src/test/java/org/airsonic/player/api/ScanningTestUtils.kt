package org.airsonic.player.api

import org.airsonic.player.TestCaseUtils
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.service.media.MediaFolderService
import org.airsonic.player.service.media.MediaScannerService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ScanningTestUtils private constructor() {
    companion object {
        private val foldersById = ConcurrentHashMap<UUID, List<MusicFolder>>()

        @JvmStatic
        fun before(
            musicFolders: List<MusicFolder>,
            mediaFolderService: MediaFolderService,
            mediaScannerService: MediaScannerService,
        ): UUID {
            val id = UUID.randomUUID()
            foldersById[id] = musicFolders
            musicFolders.forEach(mediaFolderService::createMusicFolder)
            TestCaseUtils.execScan(mediaScannerService)
            return id
        }

        @JvmStatic
        fun after(id: UUID, mediaFolderService: MediaFolderService) {
            foldersById.remove(id).orEmpty().forEach { folder ->
                // Maybe consider deleting media files too.
                mediaFolderService.deleteMusicFolder(folder.id)
            }
        }
    }
}
