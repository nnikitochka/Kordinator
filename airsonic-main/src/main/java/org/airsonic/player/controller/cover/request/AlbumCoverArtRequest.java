package org.airsonic.player.controller.cover.request;

import org.airsonic.player.controller.cover.CoverArtController;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.CoverArt;

public class AlbumCoverArtRequest extends CoverArtRequest {
    private final Album album;

    public AlbumCoverArtRequest(CoverArtController coverArtController, Album album) {
        super(coverArtController, coverArtController.coverArtService.get(CoverArt.EntityType.ALBUM, album.getId()),
                () -> CoverArtController.ALBUM_COVERART_PREFIX + album.getId(),
                album::getLastScanned);
        this.album = album;
    }

    @Override
    public String getAlbum() {
        return album.getName();
    }

    @Override
    public String getArtist() {
        return album.getArtist();
    }

    @Override
    public String toString() {
        return "Album " + album.getId() + " - " + album.getName();
    }
}
