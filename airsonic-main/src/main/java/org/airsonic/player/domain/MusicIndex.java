package org.airsonic.player.domain;

import java.io.Serializable;
import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A music index is a mapping from an index string to a list of prefixes.  A complete index consists of a list of
 * <code>MusicIndex</code> instances.<p/>
 * <p/>
 * For a normal alphabetical index, such a mapping would typically be <em>"A" -&gt; ["A"]</em>.  The index can also be used
 * to group less frequently used letters, such as  <em>"X-&Aring;" -&gt; ["X", "Y", "Z", "&AElig;", "&Oslash;", "&Aring;"]</em>, or to make multiple
 * indexes for frequently used letters, such as <em>"SA" -&gt; ["SA"]</em> and <em>"SO" -&gt; ["SO"]</em><p/>
 * <p/>
 * Clicking on an index in the user interface will typically bring up a list of all music files that are categorized
 * under that index.
 */
public class MusicIndex implements Serializable {

    public static final MusicIndex OTHER = new MusicIndex("#");

    private final String index;
    private final List<String> prefixes = new ArrayList<>();

    /**
     * Creates a new index with the given index string.
     *
     * @param index The index string, e.g., "A" or "The".
     */
    public MusicIndex(String index) {
        this.index = index;
    }

    /**
     * Adds a prefix to this index. Music files that starts with this prefix will be categorized under this index entry.
     *
     * @param prefix The prefix.
     */
    public void addPrefix(String prefix) {
        prefixes.add(prefix);
    }

    /**
     * Returns the index name.
     *
     * @return The index name.
     */
    public String getIndex() {
        return index;
    }

    /**
     * Returns the list of prefixes.
     *
     * @return The list of prefixes.
     */
    public List<String> getPrefixes() {
        return prefixes;
    }

    /**
     * Returns whether this object is equal to another one.
     *
     * @param o Object to compare to.
     * @return <code>true</code> if, and only if, the other object is a <code>MusicIndex</code> with the same
     *         index name as this one.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MusicIndex musicIndex)) {
            return false;
        }

        return Objects.equals(index, musicIndex.index);
    }

    /**
     * Returns a hash code for this object.
     *
     * @return A hash code for this object.
     */
    @Override
    public int hashCode() {
        return (index != null ? index.hashCode() : 0);
    }

    /**
     * An artist in an index.
     */
    public abstract static class SortableArtist implements Comparable<SortableArtist> {

        private final String name;
        private final String sortableName;
        private final CollationKey collationKey;

        public SortableArtist(String name, String sortableName, Collator collator) {
            this.name = name;
            this.sortableName = sortableName;
            collationKey = collator.getCollationKey(sortableName);
        }

        public String getName() {
            return name;
        }

        public String getSortableName() {
            return sortableName;
        }

        public int compareTo(SortableArtist other) {
            return collationKey.compareTo(other.collationKey);
        }
    }

    public static class SortableArtistWithMediaFiles extends SortableArtist {

        private final List<MediaFile> mediaFiles = new ArrayList<>();

        public SortableArtistWithMediaFiles(String name, String sortableName, Collator collator) {
            super(name, sortableName, collator);
        }

        public void addMediaFile(MediaFile mediaFile) {
            mediaFiles.add(mediaFile);
        }

        public List<MediaFile> getMediaFiles() {
            return mediaFiles;
        }
    }

    public static class SortableArtistWithArtist extends SortableArtist {

        private final Artist artist;

        public SortableArtistWithArtist(String name, String sortableName, Artist artist, Collator collator) {
            super(name, sortableName, collator);
            this.artist = artist;
        }

        public Artist getArtist() {
            return artist;
        }
    }
}
