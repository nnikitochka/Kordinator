package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CoverArtSearchResults", propOrder = ["image"])
class CoverArtSearchResults {
    @field:XmlElement(name = "image")
    val image: MutableList<CoverArtSearchImage> = mutableListOf()
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CoverArtSearchImage")
class CoverArtSearchImage {
    @field:XmlAttribute var imageUrl: String? = null
    @field:XmlAttribute var artist: String? = null
    @field:XmlAttribute var album: String? = null
}
