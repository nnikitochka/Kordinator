package org.airsonic.player.domain;

/**
 * Contains the ID and name for a theme.
 */
public class Theme {
    private final String id;
    private final String name;
    private final String parent;

    public Theme(String id, String name, String parent) {
        this.id = id;
        this.name = name;
        this.parent = parent;
    }

    public Theme(String id, String name) {
        this(id, name, null);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getParent() {
        return parent;
    }
}
