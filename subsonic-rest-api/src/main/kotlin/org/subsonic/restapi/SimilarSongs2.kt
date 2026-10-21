@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SimilarSongs2", propOrder = ["song"])
class SimilarSongs2 {
    @field:XmlElement(name = "song")
    val song: MutableList<Child> = mutableListOf()

}
