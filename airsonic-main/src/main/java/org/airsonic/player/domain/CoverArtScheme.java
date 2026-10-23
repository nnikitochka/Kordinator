package org.airsonic.player.domain;

/**
 * Enumeration of cover art schemes. Each value contains a size, which indicates how big the
 * scaled covert art images should be.
 */
public enum CoverArtScheme {
    OFF(0),
    SMALL(110),
    MEDIUM(160),
    LARGE(300);

    private final int size;

    CoverArtScheme(int size) {
        this.size = size;
    }

    /**
     * Returns the covert art size for this scheme.
     *
     * @return the covert art size for this scheme.
     */
    public int getSize() {
        return size;
    }

}
