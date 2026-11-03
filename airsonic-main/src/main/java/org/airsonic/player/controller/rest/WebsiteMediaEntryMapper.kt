package org.airsonic.player.controller.rest

import org.airsonic.player.ajax.media.MediaFileEntry
import org.subsonic.restapi.LiveQueueEntry

fun MediaFileEntry.toWebsiteEntry(): LiveQueueEntry = LiveQueueEntry().apply {
    id = this@toWebsiteEntry.id
    trackNumber = this@toWebsiteEntry.trackNumber
    discNumber = this@toWebsiteEntry.discNumber
    title = this@toWebsiteEntry.title
    artist = this@toWebsiteEntry.artist
    albumArtist = this@toWebsiteEntry.albumArtist
    album = this@toWebsiteEntry.album
    genre = this@toWebsiteEntry.genre
    year = this@toWebsiteEntry.year
    bitRate = this@toWebsiteEntry.bitRate
    dimensions = this@toWebsiteEntry.dimensions
    duration = this@toWebsiteEntry.duration
    format = this@toWebsiteEntry.format
    contentType = this@toWebsiteEntry.contentType
    entryType = this@toWebsiteEntry.entryType
    fileSize = this@toWebsiteEntry.fileSize
    playCount = this@toWebsiteEntry.playCount
    lastPlayed = this@toWebsiteEntry.lastPlayed?.toString()
    created = this@toWebsiteEntry.created?.toString()
    changed = this@toWebsiteEntry.changed?.toString()
    lastScanned = this@toWebsiteEntry.lastScanned?.toString()
    starred = this@toWebsiteEntry.starred
    present = this@toWebsiteEntry.present
    albumUrl = this@toWebsiteEntry.albumUrl
    streamUrl = this@toWebsiteEntry.streamUrl
    remoteStreamUrl = this@toWebsiteEntry.remoteStreamUrl
    coverArtUrl = this@toWebsiteEntry.coverArtUrl
    remoteCoverArtUrl = this@toWebsiteEntry.remoteCoverArtUrl
    accentColor = this@toWebsiteEntry.accentColor
}
