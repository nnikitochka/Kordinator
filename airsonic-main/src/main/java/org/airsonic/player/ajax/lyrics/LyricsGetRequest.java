package org.airsonic.player.ajax.lyrics;

public class LyricsGetRequest {
    private String artist;
    private String song;

    public LyricsGetRequest() {
    }

    public LyricsGetRequest(String artist, String song) {
        this.artist = artist;
        this.song = song;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getSong() {
        return song;
    }

    public void setSong(String song) {
        this.song = song;
    }
}
