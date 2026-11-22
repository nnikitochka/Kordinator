package org.airsonic.player.domain

import java.time.Instant

/** Defines criteria used when generating random playlists. */
class RandomSearchCriteria(
    val count: Int,
    val genre: String?,
    val fromYear: Int?,
    val toYear: Int?,
    val musicFolders: List<MusicFolder>?,
    val minLastPlayedDate: Instant?,
    val maxLastPlayedDate: Instant?,
    val minAlbumRating: Int?,
    val maxAlbumRating: Int?,
    val minPlayCount: Int?,
    val maxPlayCount: Int?,
    val isShowStarredSongs: Boolean,
    val isShowUnstarredSongs: Boolean,
    val format: String?
) {
    /** Creates criteria with no optional filters and both starred states included. */
    constructor(
        count: Int,
        genre: String?,
        fromYear: Int?,
        toYear: Int?,
        musicFolders: List<MusicFolder>?
    ) : this(
        count, genre, fromYear, toYear, musicFolders,
        null, null, null, null, null, null, true, true, null
    )
}
