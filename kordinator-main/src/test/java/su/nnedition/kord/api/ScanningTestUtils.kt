package su.nnedition.kord.api

import su.nnedition.kord.TestCaseUtils
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.media.MediaScannerService
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
