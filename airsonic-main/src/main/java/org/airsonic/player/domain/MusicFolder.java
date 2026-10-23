package org.airsonic.player.domain;

import com.google.common.base.Objects;

import java.io.Serializable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents a top level directory in which music or other media is stored.
 *
 * @author Sindre Mehus
 * @version $Revision: 1.1 $ $Date: 2005/11/27 14:32:05 $
 */
public class MusicFolder implements Serializable {

    private Integer id;
    private Path path;
    private String name;
    private Type type = Type.MEDIA;
    private boolean enabled;
    private Instant changed;

    /**
     * Creates a new music folder.
     *
     * @param id      The system-generated ID.
     * @param path    The path of the music folder.
     * @param name    The user-defined name.
     * @param enabled Whether the folder is enabled.
     * @param changed When the corresponding database entry was last changed.
     */
    public MusicFolder(Integer id, Path path, String name, Type type, boolean enabled, Instant changed) {
        this.id = id;
        this.path = path;
        this.name = name;
        this.type = type;
        this.enabled = enabled;
        this.changed = changed;
    }

    /**
     * Creates a new music folder.
     *
     * @param path    The path of the music folder.
     * @param name    The user-defined name.
     * @param enabled Whether the folder is enabled.
     * @param changed When the corresponding database entry was last changed.
     */
    public MusicFolder(Path path, String name, Type type, boolean enabled, Instant changed) {
        this(null, path, name, type, enabled, changed);
    }

    /**
     * Returns the system-generated ID.
     *
     * @return The system-generated ID.
     */
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Returns the path of the music folder.
     *
     * @return The path of the music folder.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Sets the path of the music folder.
     *
     * @param path The path of the music folder.
     */
    public void setPath(Path path) {
        this.path = path;
    }

    /**
     * Returns the user-defined name.
     *
     * @return The user-defined name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user-defined name.
     *
     * @param name The user-defined name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns whether the folder is enabled.
     *
     * @return Whether the folder is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the folder is enabled.
     *
     * @param enabled Whether the folder is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns when the corresponding database entry was last changed.
     *
     * @return When the corresponding database entry was last changed.
     */
    public Instant getChanged() {
        return changed;
    }

    /**
     * Sets when the corresponding database entry was last changed.
     *
     * @param changed When the corresponding database entry was last changed.
     */
    public void setChanged(Instant changed) {
        this.changed = changed;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equal(id, ((MusicFolder) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public static enum Type {
        MEDIA
    }


    public static List<Integer> toIdList(List<MusicFolder> from) {
        return from.stream().map(toId()).collect(Collectors.toList());
    }

    public static List<String> toPathList(List<MusicFolder> from) {
        return from.stream().map(toPath()).collect(Collectors.toList());
    }

    public static Function<MusicFolder, Integer> toId() {
        return from -> from.getId();
    }

    public static Function<MusicFolder, String> toPath() {
        return from -> from.getPath().toString();
    }
}