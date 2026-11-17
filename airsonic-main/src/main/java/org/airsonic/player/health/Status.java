package org.airsonic.player.health;

import java.util.Objects;

/**
 * Framework-neutral health status descriptor.
 */
public final class Status implements Comparable<Status> {
    public static final Status UNKNOWN = new Status("UNKNOWN");
    public static final Status UP = new Status("UP");
    public static final Status DOWN = new Status("DOWN");
    public static final Status OUT_OF_SERVICE = new Status("OUT_OF_SERVICE");

    private final String code;
    private final String description;

    public Status(String code) {
        this(code, "");
    }

    public Status(String code, String description) {
        this.code = Objects.requireNonNull(code, "code").toUpperCase();
        this.description = description != null ? description : "";
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public int compareTo(Status other) {
        return this.code.compareTo(other.code);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Status status = (Status) o;
        return code.equals(status.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
