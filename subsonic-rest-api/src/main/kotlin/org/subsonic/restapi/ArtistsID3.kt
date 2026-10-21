@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArtistsID3", propOrder = ["index"])
class ArtistsID3 {
    @field:XmlElement(name = "index")
    val index: MutableList<IndexID3> = mutableListOf()

    @field:XmlAttribute(name = "ignoredArticles", required = true)
    var ignoredArticles: String? = null

}
