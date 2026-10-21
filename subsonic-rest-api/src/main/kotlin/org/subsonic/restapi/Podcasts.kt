@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Podcasts", propOrder = ["channel"])
class Podcasts {
    @field:XmlElement(name = "channel")
    val channel: MutableList<PodcastChannel> = mutableListOf()

}
