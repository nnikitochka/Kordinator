@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "JukeboxPlaylist", propOrder = ["entry"])
class JukeboxPlaylist : JukeboxStatus() {
    @field:XmlElement(name = "entry")
    val entry: MutableList<Child> = mutableListOf()

}
