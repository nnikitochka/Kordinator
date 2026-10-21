@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlSchemaType
import jakarta.xml.bind.annotation.XmlType
import javax.xml.datatype.XMLGregorianCalendar

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PodcastEpisode")
class PodcastEpisode : Child() {
    @field:XmlAttribute(name = "streamId")
    var streamId: String? = null

    @field:XmlAttribute(name = "channelId", required = true)
    var channelId: String? = null

    @field:XmlAttribute(name = "description")
    var description: String? = null

    @field:XmlAttribute(name = "status", required = true)
    var status: PodcastStatus? = null

    @field:XmlAttribute(name = "publishDate")
    @field:XmlSchemaType(name = "dateTime")
    var publishDate: XMLGregorianCalendar? = null

}
