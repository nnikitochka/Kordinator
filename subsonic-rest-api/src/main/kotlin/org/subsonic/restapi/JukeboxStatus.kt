@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlType
import kotlin.jvm.JvmName

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "JukeboxStatus")
open class JukeboxStatus {
    @field:XmlAttribute(name = "currentIndex", required = true)
    var currentIndex: Int = 0

    @field:XmlAttribute(name = "playing", required = true)
    @get:JvmName("isPlaying")
    var playing: Boolean = false

    @field:XmlAttribute(name = "gain", required = true)
    var gain: Float = 0f

    @field:XmlAttribute(name = "position")
    var position: Int? = null

}
