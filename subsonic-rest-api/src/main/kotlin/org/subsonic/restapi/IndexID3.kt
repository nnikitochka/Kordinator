@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "IndexID3", propOrder = ["artist"])
class IndexID3 {
    @field:XmlElement(name = "artist")
    val artist: MutableList<ArtistID3> = mutableListOf()

    @field:XmlAttribute(name = "name", required = true)
    var name: String? = null

}
