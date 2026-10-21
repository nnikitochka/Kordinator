@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArtistInfoBase", propOrder = ["biography", "musicBrainzId", "lastFmUrl", "smallImageUrl", "mediumImageUrl", "largeImageUrl"])
open class ArtistInfoBase {
    @field:XmlElement(name = "biography")
    var biography: String? = null

    @field:XmlElement(name = "musicBrainzId")
    var musicBrainzId: String? = null

    @field:XmlElement(name = "lastFmUrl")
    var lastFmUrl: String? = null

    @field:XmlElement(name = "smallImageUrl")
    var smallImageUrl: String? = null

    @field:XmlElement(name = "mediumImageUrl")
    var mediumImageUrl: String? = null

    @field:XmlElement(name = "largeImageUrl")
    var largeImageUrl: String? = null

}
