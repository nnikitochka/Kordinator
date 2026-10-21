@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VideoInfo", propOrder = ["captions", "audioTrack", "conversion"])
class VideoInfo {
    @field:XmlElement(name = "captions")
    val captions: MutableList<Captions> = mutableListOf()

    @field:XmlElement(name = "audioTrack")
    val audioTrack: MutableList<AudioTrack> = mutableListOf()

    @field:XmlElement(name = "conversion")
    val conversion: MutableList<VideoConversion> = mutableListOf()

    @field:XmlAttribute(name = "id", required = true)
    var id: String? = null

}
