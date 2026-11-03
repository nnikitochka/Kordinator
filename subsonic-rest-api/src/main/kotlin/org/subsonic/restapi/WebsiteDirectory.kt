package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WebsiteDirectory", propOrder = ["detail", "file", "subDir", "ancestor", "siblingAlbum"])
class WebsiteDirectory {
    @field:XmlAttribute var contentType: String? = null
    @field:XmlAttribute var comment: String? = null
    @field:XmlAttribute var userRating: Int? = null
    @field:XmlAttribute var averageRating: Double? = null
    @field:XmlAttribute var musicBrainzReleaseId: String? = null
    @field:XmlElement var detail: LiveQueueEntry? = null
    @field:XmlElement(name = "file") val file: MutableList<LiveQueueEntry> = mutableListOf()
    @field:XmlElement(name = "subDir") val subDir: MutableList<LiveQueueEntry> = mutableListOf()
    @field:XmlElement(name = "ancestor") val ancestor: MutableList<LiveQueueEntry> = mutableListOf()
    @field:XmlElement(name = "siblingAlbum") val siblingAlbum: MutableList<LiveQueueEntry> = mutableListOf()
}
