package org.airsonic.player.service.playlist;

import chameleon.playlist.SpecificPlaylist;
import chameleon.playlist.SpecificPlaylistProvider;

public interface PlaylistExportHandler {
    boolean canHandle(Class<? extends SpecificPlaylistProvider> providerClass);

    SpecificPlaylist handle(int id, SpecificPlaylistProvider provider) throws Exception;

    default int getOrder() {
        return Integer.MAX_VALUE;
    }
}
