package su.nnedition.kord.dao

import su.nnedition.kord.domain.PlayQueue
import su.nnedition.kord.service.media.MediaFolderService

class PlayerDaoPlayQueueFactory(private val mediaFolderService: MediaFolderService) {
    fun createPlayQueue(): PlayQueue = PlayQueue { folderId -> mediaFolderService.getMusicFolderById(folderId)!! }
}
