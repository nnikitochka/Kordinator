package org.airsonic.player.controller.cover.request;

import org.airsonic.player.service.CoverArtImageService;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.CoverArt;

public class AlbumCoverArtRequest extends CoverArtRequest {
    private final Album album;

    public AlbumCoverArtRequest(CoverArtImageService coverArtImageService, Album album) {
        super(coverArtImageService, coverArtImageService.coverArtService.get(CoverArt.EntityType.ALBUM, album.getId()),
                () -> CoverArtImageService.ALBUM_COVERART_PREFIX + album.getId(),
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
