@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SearchResult", propOrder = ["match"])
class SearchResult {
    @field:XmlElement(name = "match")
    val match: MutableList<Child> = mutableListOf()

    @field:XmlAttribute(name = "offset", required = true)
    var offset: Int = 0

    @field:XmlAttribute(name = "totalHits", required = true)
    var totalHits: Int = 0

}
