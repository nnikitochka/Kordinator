package org.airsonic.player.ajax;

import org.airsonic.player.domain.ArtistBio;

import java.util.List;

public record ArtistInfo(
        List<SimilarArtist> similarArtists,
        ArtistBio artistBio,
        List<MediaFileEntry> topSongs
) {
    public List<SimilarArtist> getSimilarArtists() {
        return similarArtists;
    }

    public ArtistBio getArtistBio() {
        return artistBio;
    }

    public List<MediaFileEntry> getTopSongs() {
        return topSongs;
    }
}
