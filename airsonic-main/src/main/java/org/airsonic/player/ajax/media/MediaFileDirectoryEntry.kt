package org.airsonic.player.ajax.media

class MediaFileDirectoryEntry(mfileEntry: MediaFileEntry) : MediaFileEntry(
    mfileEntry.id, mfileEntry.trackNumber, mfileEntry.discNumber, mfileEntry.title, mfileEntry.artist,
    mfileEntry.albumArtist, mfileEntry.album, mfileEntry.genre, mfileEntry.year, mfileEntry.bitRate,
    mfileEntry.dimensions, mfileEntry.duration, mfileEntry.format, mfileEntry.contentType, mfileEntry.entryType,
    mfileEntry.fileSize, mfileEntry.playCount, mfileEntry.lastPlayed, mfileEntry.created, mfileEntry.changed,
    mfileEntry.lastScanned, mfileEntry.starred, mfileEntry.present, mfileEntry.albumUrl, mfileEntry.streamUrl,
    mfileEntry.remoteStreamUrl, mfileEntry.coverArtUrl, mfileEntry.remoteCoverArtUrl, mfileEntry.accentColor
) {
    var files: MutableList<MediaFileEntry>? = null
    var subDirs: MutableList<MediaFileEntry>? = null
    var ancestors: MutableList<MediaFileEntry>? = null
    var userRating: Int? = null
    var averageRating: Double? = null
    var comment: String? = null

    // Albums only
    var siblingAlbums: MutableList<MediaFileEntry>? = null
    var musicBrainzReleaseId: String? = null
}
