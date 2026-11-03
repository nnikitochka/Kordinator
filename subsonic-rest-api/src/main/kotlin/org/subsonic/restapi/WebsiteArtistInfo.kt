package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WebsiteArtistInfo", propOrder = ["topSong"])
class WebsiteArtistInfo {
    @field:XmlElement(name = "topSong") val topSong: MutableList<LiveQueueEntry> = mutableListOf()
}
