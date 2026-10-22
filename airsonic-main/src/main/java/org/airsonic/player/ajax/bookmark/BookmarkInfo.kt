package org.airsonic.player.ajax.bookmark;


import org.airsonic.player.ajax.media.MediaFileEntry;

import java.time.Instant;

public class BookmarkInfo {
    private final int id;
    private final MediaFileEntry mediaFileEntry;
    private final Instant changed;
    private final Instant created;
    private final String comment;
    private final long positionMillis;

    public BookmarkInfo(int id, MediaFileEntry mediaFileEntry, Instant changed, Instant created, String comment, long positionMillis) {
        this.id = id;
        this.mediaFileEntry = mediaFileEntry;
        this.changed = changed;
        this.created = created;
        this.comment = comment;
        this.positionMillis = positionMillis;
    }

    public int getId() {
        return id;
    }

    public MediaFileEntry getMediaFileEntry() {
        return mediaFileEntry;
    }

    public Instant getChanged() {
        return changed;
    }

    public Instant getCreated() {
        return created;
    }

    public String getComment() {
        return comment;
    }

    public long getPositionMillis() {
        return positionMillis;
    }
}
