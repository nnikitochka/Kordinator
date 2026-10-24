package org.airsonic.player.domain;

public class AlbumNotes {

    private final String notes;
    private final String musicBrainzId;
    private final String lastFmUrl;
    private final String smallImageUrl;
    private final String mediumImageUrl;
    private final String largeImageUrl;

    public AlbumNotes(String notes, String musicBrainzId, String lastFmUrl, String smallImageUrl,
                      String mediumImageUrl, String largeImageUrl) {
        this.notes = notes;
        this.musicBrainzId = musicBrainzId;
        this.lastFmUrl = lastFmUrl;
        this.smallImageUrl = smallImageUrl;
        this.mediumImageUrl = mediumImageUrl;
        this.largeImageUrl = largeImageUrl;
    }

    public String getNotes() {
        return notes;
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
