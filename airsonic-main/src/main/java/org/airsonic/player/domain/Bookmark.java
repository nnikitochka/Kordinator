package org.airsonic.player.domain;

import java.time.Instant;

/**
 * A bookmark within a media file, for a given user.
 */
public class Bookmark {
    private int id;
    private int mediaFileId;
    private long positionMillis;
    private String username;
    private String comment;
    private Instant created;
    private Instant changed;

    public Bookmark(int id, int mediaFileId, long positionMillis, String username, String comment, Instant created, Instant changed) {
        this.id = id;
        this.mediaFileId = mediaFileId;
        this.positionMillis = positionMillis;
        this.username = username;
        this.comment = comment;
        this.created = created;
        this.changed = changed;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getMediaFileId() {
        return mediaFileId;
    }

    public void setMediaFileId(int mediaFileId) {
        this.mediaFileId = mediaFileId;
    }

    public long getPositionMillis() {
        return positionMillis;
    }

    public void setPositionMillis(long positionMillis) {
        this.positionMillis = positionMillis;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getChanged() {
        return changed;
    }

    public void setChanged(Instant changed) {
        this.changed = changed;
    }
}
