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

    public int compare(MediaFile a, MediaFile b) {

        // Directories before files.
        if (a.isFile() && b.isDirectory()) {
            return 1;
        }
        if (a.isDirectory() && b.isFile()) {
            return -1;
        }

        // Non-album directories before album directories.
        if (a.isAlbum() && b.getMediaType() == MediaFile.MediaType.DIRECTORY) {
            return 1;
        }
        if (a.getMediaType() == MediaFile.MediaType.DIRECTORY && b.isAlbum()) {
            return -1;
        }

        // Sort albums by year
        if (sortAlbumsByYear && a.isAlbum() && b.isAlbum()) {
            int i = nullSafeCompare(a.getYear(), b.getYear(), false);
            if (i != 0) {
                return i;
            }
        }

        if (a.isDirectory() && b.isDirectory()) {
            int n = a.getName().compareToIgnoreCase(b.getName());
            return n == 0 ? a.getPath().compareToIgnoreCase(b.getPath()) : n; // To make it consistent to MediaFile.equals()
        }

        // Compare by disc and track numbers, if present.
        Integer trackA = getSortableDiscAndTrackNumber(a);
        Integer trackB = getSortableDiscAndTrackNumber(b);
        int i = nullSafeCompare(trackA, trackB, false);
        if (i != 0) {
            return i;
        }

        return a.getPath().compareToIgnoreCase(b.getPath());
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

