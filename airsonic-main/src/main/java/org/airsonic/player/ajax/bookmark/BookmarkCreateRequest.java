package org.airsonic.player.ajax.bookmark;

public class BookmarkCreateRequest {
    private long positionMillis;
    private String comment;
    private int mediaFileId;

    public long getPositionMillis() {
        return positionMillis;
    }

    public void setPositionMillis(long positionMillis) {
        this.positionMillis = positionMillis;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public int getMediaFileId() {
        return mediaFileId;
    }

    public void setMediaFileId(int mediaFileId) {
        this.mediaFileId = mediaFileId;
    }
}
