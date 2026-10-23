package org.airsonic.player.domain;

public enum AlbumListType {
    RANDOM("random", "Random"),
    NEWEST("newest", "Recently Added"),
    STARRED("starred", "Starred"),
    HIGHEST("highest", "Top Rated"),
    FREQUENT("frequent", "Most Played"),
    RECENT("recent", "Recently Played"),
    DECADE("decade", "By Decade"),
    GENRE("genre", "By Genre"),
    ALPHABETICAL("alphabetical", "All");

    private final String id;
    private final String description;

    AlbumListType(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public static AlbumListType fromId(String id) {
        for (AlbumListType albumListType : values()) {
            if (albumListType.id.equals(id)) {
                return albumListType;
            }
        }
        return null;
    }
}
