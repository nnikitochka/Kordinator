@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SearchResult2", propOrder = ["artist", "album", "song"])
class SearchResult2 {
    @field:XmlElement(name = "artist")
    val artist: MutableList<Artist> = mutableListOf()

    @field:XmlElement(name = "album")
    val album: MutableList<Child> = mutableListOf()

    @field:XmlElement(name = "song")
    val song: MutableList<Child> = mutableListOf()

}
