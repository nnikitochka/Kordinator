package org.airsonic.player.domain

import java.util.Comparator

/** Comparator for sorting media files. */
class MediaFileComparator(private val sortAlbumsByYear: Boolean) : Comparator<MediaFile> {
    override fun compare(first: MediaFile, second: MediaFile): Int {
        // Directories before files.
        if (first.isFile && second.isDirectory) return 1
        if (first.isDirectory && second.isFile) return -1

        // Non-album directories before album directories.
        if (first.isAlbum && second.mediaType == MediaFile.MediaType.DIRECTORY) return 1
        if (first.mediaType == MediaFile.MediaType.DIRECTORY && second.isAlbum) return -1

        // Sort albums by year.
        if (sortAlbumsByYear && first.isAlbum && second.isAlbum) {
            val yearComparison = nullSafeCompare(first.year, second.year)
            if (yearComparison != 0) return yearComparison
        }

        if (first.isDirectory && second.isDirectory) {
            val nameComparison = first.name.compareTo(second.name, ignoreCase = true)
            return if (nameComparison == 0) {
                first.path.compareTo(second.path, ignoreCase = true)
            } else {
                nameComparison
            }
        }

        // Compare by disc and track numbers, if present.
        val trackA = sortableDiscAndTrackNumber(first)
        val trackB = sortableDiscAndTrackNumber(second)
        val trackComparison = nullSafeCompare(trackA, trackB)
        if (trackComparison != 0) return trackComparison

        return first.path.compareTo(second.path, ignoreCase = true)
    }

    private fun nullSafeCompare(first: Int?, second: Int?): Int = when {
        first == null && second == null -> 0
        first == null -> 1
        second == null -> -1
        else -> first.compareTo(second)
    }

    private fun sortableDiscAndTrackNumber(file: MediaFile): Int? {
        val trackNumber = file.trackNumber ?: return null
        val discNumber = file.discNumber ?: 1
        return discNumber * 1000 + trackNumber
    }
}
