package org.airsonic.player.domain;

import org.airsonic.player.dao.AbstractDao.Column;

import java.time.Instant;

public class Playlist {
    private int id;
    private String username;
    @Column("is_public")
    private boolean shared;
    private String name;
    private String comment;
    private int fileCount;
    private double duration;
    private Instant created;
    private Instant changed;
    private String importedFrom;

    public Playlist() {
    }

    public Playlist(int id, String username, boolean shared, String name, String comment, int fileCount,
                    double duration, Instant created, Instant changed, String importedFrom) {
        this.id = id;
        this.username = username;
        this.shared = shared;
        this.name = name;
        this.comment = comment;
        this.fileCount = fileCount;
        this.duration = duration;
        this.created = created;
        this.changed = changed;
        this.importedFrom = importedFrom;
    }

    public Playlist(Playlist p) {
        this(p.getId(), p.getUsername(), p.getShared(), p.getName(), p.getComment(), p.getFileCount(), p.getDuration(),
                p.getCreated(), p.getChanged(), p.getImportedFrom());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean getShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
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

    public String getImportedFrom() {
        return importedFrom;
    }

    public void setImportedFrom(String importedFrom) {
        this.importedFrom = importedFrom;
    }
}
