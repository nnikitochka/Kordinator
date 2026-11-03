@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlType
import jakarta.xml.bind.annotation.XmlValue

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Lyrics", propOrder = ["content"])
class Lyrics {
    @field:XmlValue
    var content: String? = null

    // Optional lookup status, emitted only when explicitly requested by a client.
    @field:XmlAttribute(name = "tryLater")
    var tryLater: Boolean? = null

    @field:XmlAttribute(name = "artist")
    var artist: String? = null

    @field:XmlAttribute(name = "title")
    var title: String? = null

}
