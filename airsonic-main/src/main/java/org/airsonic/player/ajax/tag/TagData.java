package org.airsonic.player.ajax.tag;

import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang.StringUtils;

public class TagData {
    @NotNull
    private Integer mediaFileId;
    private Integer track;
    private String artist;
    private String album;
    private String title;
    private Integer year;
    private String genre;

    public Integer getMediaFileId() {
        return mediaFileId;
    }

    public void setMediaFileId(Integer mediaFileId) {
        this.mediaFileId = mediaFileId;
    }

    public Integer getTrack() {
        return track;
    }

    public void setTrack(Integer track) {
        this.track = track;
    }

    public String getArtist() {
        return StringUtils.trimToNull(artist);
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return StringUtils.trimToNull(album);
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getTitle() {
        return StringUtils.trimToNull(title);
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getGenre() {
        return StringUtils.trimToNull(genre);
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }
}
