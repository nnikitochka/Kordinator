package org.airsonic.player.ajax.media;

import java.util.List;

public class MediaFileDirectoryEntry extends MediaFileEntry {
    private List<MediaFileEntry> files;
    private List<MediaFileEntry> subDirs;
    private List<MediaFileEntry> ancestors;
    private Integer userRating;
    private Double averageRating;
    private String comment;

    // Albums only
    private List<MediaFileEntry> siblingAlbums;
    private String musicBrainzReleaseId;

    public MediaFileDirectoryEntry(MediaFileEntry mfe) {
        super(mfe.getId(), mfe.getTrackNumber(), mfe.getDiscNumber(), mfe.getTitle(), mfe.getArtist(),
                mfe.getAlbumArtist(), mfe.getAlbum(), mfe.getGenre(), mfe.getYear(), mfe.getBitRate(),
                mfe.getDimensions(), mfe.getDuration(), mfe.getFormat(), mfe.getContentType(), mfe.getEntryType(),
                mfe.getFileSize(), mfe.getPlayCount(), mfe.getLastPlayed(), mfe.getCreated(), mfe.getChanged(),
                mfe.getLastScanned(), mfe.getStarred(), mfe.getPresent(), mfe.getAlbumUrl(), mfe.getStreamUrl(),
                mfe.getRemoteStreamUrl(), mfe.getCoverArtUrl(), mfe.getRemoteCoverArtUrl(), mfe.getAccentColor());
    }

    public List<MediaFileEntry> getFiles() {
        return files;
    }

    public void setFiles(List<MediaFileEntry> files) {
        this.files = files;
    }

    public Integer getUserRating() {
        return userRating;
    }

    public void setUserRating(Integer userRating) {
        this.userRating = userRating;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getMusicBrainzReleaseId() {
        return musicBrainzReleaseId;
    }

    public void setMusicBrainzReleaseId(String musicBrainzReleaseId) {
        this.musicBrainzReleaseId = musicBrainzReleaseId;
    }

    public List<MediaFileEntry> getSubDirs() {
        return subDirs;
    }

    public void setSubDirs(List<MediaFileEntry> subDirs) {
        this.subDirs = subDirs;
    }

    public List<MediaFileEntry> getAncestors() {
        return ancestors;
    }

    public void setAncestors(List<MediaFileEntry> ancestors) {
        this.ancestors = ancestors;
    }

    public List<MediaFileEntry> getSiblingAlbums() {
        return siblingAlbums;
    }

    public void setSiblingAlbums(List<MediaFileEntry> siblingAlbums) {
        this.siblingAlbums = siblingAlbums;
    }
}
