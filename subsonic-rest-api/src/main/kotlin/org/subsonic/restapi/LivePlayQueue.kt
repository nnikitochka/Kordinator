package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "LivePlayQueue", propOrder = ["entry", "player"])
class LivePlayQueue {
    @field:XmlElement(name = "entry") val entry: MutableList<LiveQueueEntry> = mutableListOf()
    @field:XmlElement(name = "player") val player: MutableList<LiveQueuePlayer> = mutableListOf()
    @field:XmlAttribute var revision: Long = 0
    @field:XmlAttribute var playStatus: String? = null
    @field:XmlAttribute var repeatStatus: String? = null
    @field:XmlAttribute var shuffleRadioEnabled: Boolean = false
    @field:XmlAttribute var gain: Float = 0f
    @field:XmlAttribute var startPlayerAt: Int = -1
    @field:XmlAttribute var startPlayerAtPosition: Long = 0
    @field:XmlAttribute var savedQueueId: Int? = null
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "LiveQueuePlayer")
class LiveQueuePlayer {
    @field:XmlAttribute var id: Int = 0
    @field:XmlAttribute var description: String? = null
    @field:XmlAttribute var tech: String? = null
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "LiveQueueEntry")
class LiveQueueEntry {
    @field:XmlAttribute var id: Int = 0
    @field:XmlAttribute var trackNumber: Int? = null
    @field:XmlAttribute var discNumber: Int? = null
    @field:XmlAttribute var title: String? = null
    @field:XmlAttribute var artist: String? = null
    @field:XmlAttribute var albumArtist: String? = null
    @field:XmlAttribute var album: String? = null
    @field:XmlAttribute var genre: String? = null
    @field:XmlAttribute var year: Int? = null
    @field:XmlAttribute var bitRate: String? = null
    @field:XmlAttribute var dimensions: String? = null
    @field:XmlAttribute var duration: Double? = null
    @field:XmlAttribute var format: String? = null
    @field:XmlAttribute var contentType: String? = null
    @field:XmlAttribute var entryType: String? = null
    @field:XmlAttribute var fileSize: String? = null
    @field:XmlAttribute var playCount: Int = 0
    @field:XmlAttribute var lastPlayed: String? = null
    @field:XmlAttribute var created: String? = null
    @field:XmlAttribute var changed: String? = null
    @field:XmlAttribute var lastScanned: String? = null
    @field:XmlAttribute var starred: Boolean = false
    @field:XmlAttribute var present: Boolean = false
    @field:XmlAttribute var albumUrl: String? = null
    @field:XmlAttribute var streamUrl: String? = null
    @field:XmlAttribute var remoteStreamUrl: String? = null
    @field:XmlAttribute var coverArtUrl: String? = null
    @field:XmlAttribute var remoteCoverArtUrl: String? = null
    @field:XmlAttribute var accentColor: String? = null
}
