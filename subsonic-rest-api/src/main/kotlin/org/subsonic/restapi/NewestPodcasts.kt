@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "NewestPodcasts", propOrder = ["episode"])
class NewestPodcasts {
    @field:XmlElement(name = "episode")
    val episode: MutableList<PodcastEpisode> = mutableListOf()

}
