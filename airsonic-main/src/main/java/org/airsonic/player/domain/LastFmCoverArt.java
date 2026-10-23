package org.airsonic.player.domain;

public class LastFmCoverArt {
    private final String imageUrl;
    private final String artist;
    private final String album;

    public LastFmCoverArt(String imageUrl, String artist, String album) {
        this.imageUrl = imageUrl;
        this.artist = artist;
        this.album = album;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }
}

