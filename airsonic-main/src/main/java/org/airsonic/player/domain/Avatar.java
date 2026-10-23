package org.airsonic.player.domain;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * An icon representing a user.
 */
public class Avatar {
    private final int id;
    private final String name;
    private final Instant createdDate;
    private final String mimeType;
    private final int width;
    private final int height;
    private final Path path;

    public Avatar(int id, String name, Instant createdDate, String mimeType, int width, int height, String path) {
        this(id, name, createdDate, mimeType, width, height, Paths.get(path));
    }

    public Avatar(int id, String name, Instant createdDate, String mimeType, int width, int height, Path path) {
        this.id = id;
        this.name = name;
        this.createdDate = createdDate;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
        this.path = path;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Path getPath() {
        return path;
    }
}
