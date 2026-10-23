package org.airsonic.player.domain;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a musical genre.
 */
public class Genre {
    private final String name;
    private final AtomicInteger songCount = new AtomicInteger(0);
    private final AtomicInteger albumCount = new AtomicInteger(0);

    public Genre(String name) {
        this.name = name;
    }

    public Genre(String name, int songCount, int albumCount) {
        this.name = name;
        this.songCount.set(songCount);
        this.albumCount.set(albumCount);
    }

    public String getName() {
        return name;
    }

    public int getSongCount() {
        return songCount.get();
    }

    public int getAlbumCount() {
        return albumCount.get();
    }

    public void incrementAlbumCount() {
        albumCount.incrementAndGet();
    }

    public void incrementSongCount() {
        songCount.incrementAndGet();
    }
}