package org.airsonic.player.ajax.artist;

public class ArtistInfoRequest {
    private int mediaFileId;
    private int maxSimilarArtists;
    private int maxTopSongs;

    public int getMediaFileId() {
        return mediaFileId;
    }

    public void setMediaFileId(int mediaFileId) {
        this.mediaFileId = mediaFileId;
    }

    public int getMaxSimilarArtists() {
        return maxSimilarArtists;
    }

    public void setMaxSimilarArtists(int maxSimilarArtists) {
        this.maxSimilarArtists = maxSimilarArtists;
    }

    public int getMaxTopSongs() {
        return maxTopSongs;
    }

    public void setMaxTopSongs(int maxTopSongs) {
        this.maxTopSongs = maxTopSongs;
    }
}
