package org.airsonic.player.dao

import org.airsonic.player.domain.PlayQueue
import org.airsonic.player.service.media.MediaFolderService

class PlayerDaoPlayQueueFactory(private val mediaFolderService: MediaFolderService) {
    fun createPlayQueue(): PlayQueue = PlayQueue { folderId -> mediaFolderService.getMusicFolderById(folderId)!! }
}
