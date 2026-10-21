@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArtistInfo2", propOrder = ["similarArtist"])
class ArtistInfo2 : ArtistInfoBase() {
    @field:XmlElement(name = "similarArtist")
    val similarArtist: MutableList<ArtistID3> = mutableListOf()

}
