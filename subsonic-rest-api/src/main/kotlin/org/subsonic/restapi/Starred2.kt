@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Starred2", propOrder = ["artist", "album", "song"])
class Starred2 {
    @field:XmlElement(name = "artist")
    val artist: MutableList<ArtistID3> = mutableListOf()

    @field:XmlElement(name = "album")
    val album: MutableList<AlbumID3> = mutableListOf()

    @field:XmlElement(name = "song")
    val song: MutableList<Child> = mutableListOf()

}
