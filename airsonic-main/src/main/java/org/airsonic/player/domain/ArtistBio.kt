package org.airsonic.player.domain;

public class ArtistBio {
    private final String biography;
    private final String musicBrainzId;
    private final String lastFmUrl;
    private final String smallImageUrl;
    private final String mediumImageUrl;
    private final String largeImageUrl;

    public ArtistBio(String biography, String musicBrainzId, String lastFmUrl, String smallImageUrl,
                     String mediumImageUrl, String largeImageUrl) {
        this.biography = biography;
        this.musicBrainzId = musicBrainzId;
        this.lastFmUrl = lastFmUrl;
        this.smallImageUrl = smallImageUrl;
        this.mediumImageUrl = mediumImageUrl;
        this.largeImageUrl = largeImageUrl;
    }

    public String getBiography() {
        return biography;
    }

    public String getMusicBrainzId() {
        return musicBrainzId;
    }

    public String getLastFmUrl() {
        return lastFmUrl;
    }

    public String getSmallImageUrl() {
        return smallImageUrl;
    }

    public String getMediumImageUrl() {
        return mediumImageUrl;
    }

    public String getLargeImageUrl() {
        return largeImageUrl;
    }
}
