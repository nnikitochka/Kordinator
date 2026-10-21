@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArtistWithAlbumsID3", propOrder = ["album"])
class ArtistWithAlbumsID3 : ArtistID3() {
    @field:XmlElement(name = "album")
    val album: MutableList<AlbumID3> = mutableListOf()

}
