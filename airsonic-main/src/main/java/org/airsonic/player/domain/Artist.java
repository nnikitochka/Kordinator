package org.airsonic.player.domain;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class Artist {

    private int id;
    private String name;
    private final AtomicInteger albumCount = new AtomicInteger();
    private Instant lastScanned;
    private boolean present;
    private Integer folderId;

    public Artist() {
    }

    public Artist(int id, String name, int albumCount, Instant lastScanned, boolean present, Integer folderId) {
        this.id = id;
        this.name = name;
        this.albumCount.set(albumCount);
        this.lastScanned = lastScanned;
        this.present = present;
        this.folderId = folderId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAlbumCount() {
        return albumCount.get();
    }

    public void setAlbumCount(int albumCount) {
        this.albumCount.set(albumCount);
    }

    public Instant getLastScanned() {
        return lastScanned;
    }

    public void setLastScanned(Instant lastScanned) {
        this.lastScanned = lastScanned;
    }

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public void setFolderId(Integer folderId) {
        this.folderId = folderId;
    }

    public Integer getFolderId() {
        return folderId;
    }

    // placeholder for persistence later
    private CoverArt art;

    public CoverArt getArt() {
        return art;
    }

    public void setArt(CoverArt art) {
        this.art = art;
    }
}
