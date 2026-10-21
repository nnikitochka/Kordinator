@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MusicFolder")
class MusicFolder {
    @field:XmlAttribute(name = "id", required = true)
    var id: Int = 0

    @field:XmlAttribute(name = "name")
    var name: String? = null

}
