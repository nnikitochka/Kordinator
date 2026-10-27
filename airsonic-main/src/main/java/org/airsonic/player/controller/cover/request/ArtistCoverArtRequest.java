package org.airsonic.player.controller.cover.request;

import org.airsonic.player.controller.cover.CoverArtController;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.CoverArt;

public class ArtistCoverArtRequest extends CoverArtRequest {
    private final Artist artist;

    public ArtistCoverArtRequest(CoverArtController coverArtController, Artist artist) {
        super(coverArtController, coverArtController.coverArtService.get(CoverArt.EntityType.ARTIST, artist.getId()),
                () -> CoverArtController.ARTIST_COVERART_PREFIX + artist.getId(),
                artist::getLastScanned);
        this.artist = artist;
    }

    @Override
    public String getAlbum() {
        return null;
    }

    @Override
    public String getArtist() {
        return artist.getName();
    }

    @Override
    public String toString() {
        return "Artist " + artist.getId() + " - " + artist.getName();
    }
}
