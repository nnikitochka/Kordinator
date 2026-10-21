@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VideoConversion")
class VideoConversion {
    @field:XmlAttribute(name = "id", required = true)
    var id: String? = null

    @field:XmlAttribute(name = "bitRate")
    var bitRate: Int? = null

    @field:XmlAttribute(name = "audioTrackId")
    var audioTrackId: Int? = null

}
