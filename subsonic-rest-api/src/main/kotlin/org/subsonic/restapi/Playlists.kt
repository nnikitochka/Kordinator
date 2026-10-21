@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Playlists", propOrder = ["playlist"])
class Playlists {
    @field:XmlElement(name = "playlist")
    val playlist: MutableList<Playlist> = mutableListOf()

}
