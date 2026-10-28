package org.airsonic.player.domain;

import java.util.Comparator;

/**
 * Comparator for sorting media files.
 */
public class MediaFileComparator implements Comparator<MediaFile> {

    private final boolean sortAlbumsByYear;

    public MediaFileComparator(boolean sortAlbumsByYear) {
        this.sortAlbumsByYear = sortAlbumsByYear;
    }

    public int compare(MediaFile first, MediaFile second) {

        // Directories before files.
        if (first.isFile() && second.isDirectory()) {
            return 1;
        }
        if (first.isDirectory() && second.isFile()) {
            return -1;
        }

        // Non-album directories before album directories.
        if (first.isAlbum() && second.getMediaType() == MediaFile.MediaType.DIRECTORY) {
            return 1;
        }
        if (first.getMediaType() == MediaFile.MediaType.DIRECTORY && second.isAlbum()) {
            return -1;
        }

        // Sort albums by year
        if (sortAlbumsByYear && first.isAlbum() && second.isAlbum()) {
            int i = nullSafeCompare(first.getYear(), second.getYear(), false);
            if (i != 0) {
                return i;
            }
        }

        if (first.isDirectory() && second.isDirectory()) {
            int n = first.getName().compareToIgnoreCase(second.getName());
            return n == 0 ? first.getPath().compareToIgnoreCase(second.getPath()) : n; // To make it consistent to MediaFile.equals()
        }

        // Compare by disc and track numbers, if present.
        Integer trackA = getSortableDiscAndTrackNumber(first);
        Integer trackB = getSortableDiscAndTrackNumber(second);
        int i = nullSafeCompare(trackA, trackB, false);
        if (i != 0) {
            return i;
        }

        return first.getPath().compareToIgnoreCase(second.getPath());
    }

    private <T extends Comparable<T>> int nullSafeCompare(T a, T b, boolean nullIsSmaller) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return nullIsSmaller ? -1 : 1;
        }
        if (b == null) {
            return nullIsSmaller ? 1 : -1;
        }
        return a.compareTo(b);
    }

    private Integer getSortableDiscAndTrackNumber(MediaFile file) {
        if (file.getTrackNumber() == null) {
            return null;
        }

        int discNumber = file.getDiscNumber() == null ? 1 : file.getDiscNumber();
        return discNumber * 1000 + file.getTrackNumber();
    }
}

