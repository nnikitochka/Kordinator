@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Index", propOrder = ["artist"])
class Index {
    @field:XmlElement(name = "artist")
    val artist: MutableList<Artist> = mutableListOf()

    @field:XmlAttribute(name = "name", required = true)
    var name: String? = null

}
