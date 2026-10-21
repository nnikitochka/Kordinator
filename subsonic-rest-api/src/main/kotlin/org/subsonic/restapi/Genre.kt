@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlType
import jakarta.xml.bind.annotation.XmlValue

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Genre", propOrder = ["content"])
class Genre {
    @field:XmlValue
    var content: String? = null

    @field:XmlAttribute(name = "songCount", required = true)
    var songCount: Int = 0

    @field:XmlAttribute(name = "albumCount", required = true)
    var albumCount: Int = 0

}
