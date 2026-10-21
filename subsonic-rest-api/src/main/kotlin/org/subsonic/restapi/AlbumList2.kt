@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AlbumList2", propOrder = ["album"])
class AlbumList2 {
    @field:XmlElement(name = "album")
    val album: MutableList<AlbumID3> = mutableListOf()

}
