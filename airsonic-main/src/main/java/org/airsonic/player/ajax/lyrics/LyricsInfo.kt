package org.airsonic.player.ajax.lyrics;

public class LyricsInfo {

    private final String lyrics;
    private final String artist;
    private final String title;
    private boolean tryLater;

    public LyricsInfo() {
        this(null, null, null);
    }

    public LyricsInfo(String lyrics, String artist, String title) {
        this.lyrics = lyrics;
        this.artist = artist;
        this.title = title;
    }

    public String getLyrics() {
        return lyrics;
    }

    public String getArtist() {
        return artist;
    }

    public String getTitle() {
        return title;
    }

    public void setTryLater(boolean tryLater) {
        this.tryLater = tryLater;
    }

    public boolean isTryLater() {
        return tryLater;
    }
}
