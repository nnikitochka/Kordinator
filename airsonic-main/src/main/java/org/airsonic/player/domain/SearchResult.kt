package org.airsonic.player.domain

/** The outcome of a search. */
class SearchResult {
    val mediaFiles: MutableList<MediaFile> = ArrayList()
    val artists: MutableList<Artist> = ArrayList()
    val albums: MutableList<Album> = ArrayList()

    var offset: Int = 0
    var totalHits: Int = 0
}
