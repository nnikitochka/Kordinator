package org.airsonic.player.dao;

import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.service.MediaFolderService;
import org.springframework.stereotype.Component;

@Component
public class PlayerDaoPlayQueueFactory {
    private final MediaFolderService mediaFolderService;

    public PlayerDaoPlayQueueFactory(MediaFolderService mediaFolderService) {
        this.mediaFolderService = mediaFolderService;
    }

    public PlayQueue createPlayQueue() {
        return new PlayQueue(mediaFolderService::getMusicFolderById);
    }
}
