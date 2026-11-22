package org.airsonic.player.dao

import org.airsonic.player.domain.PlayQueue
import org.airsonic.player.service.MediaFolderService

class PlayerDaoPlayQueueFactory(private val mediaFolderService: MediaFolderService) {
    fun createPlayQueue(): PlayQueue = PlayQueue(mediaFolderService::getMusicFolderById)
}
