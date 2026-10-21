@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PodcastChannel", propOrder = ["episode"])
class PodcastChannel {
    @field:XmlElement(name = "episode")
    val episode: MutableList<PodcastEpisode> = mutableListOf()

    @field:XmlAttribute(name = "id", required = true)
    var id: String? = null

    @field:XmlAttribute(name = "url", required = true)
    var url: String? = null

    @field:XmlAttribute(name = "title")
    var title: String? = null

    @field:XmlAttribute(name = "description")
    var description: String? = null

    @field:XmlAttribute(name = "coverArt")
    var coverArt: String? = null

    @field:XmlAttribute(name = "originalImageUrl")
    var originalImageUrl: String? = null

    @field:XmlAttribute(name = "status", required = true)
    var status: PodcastStatus? = null

    @field:XmlAttribute(name = "errorMessage")
    var errorMessage: String? = null

}
