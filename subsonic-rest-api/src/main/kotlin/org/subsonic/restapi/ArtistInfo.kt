@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArtistInfo", propOrder = ["similarArtist"])
class ArtistInfo : ArtistInfoBase() {
    @field:XmlElement(name = "similarArtist")
    val similarArtist: MutableList<Artist> = mutableListOf()
}
