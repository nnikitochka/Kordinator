@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlSchemaType
import jakarta.xml.bind.annotation.XmlType
import javax.xml.datatype.XMLGregorianCalendar

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AlbumID3")
open class AlbumID3 {
    @field:XmlAttribute(name = "id", required = true)
    var id: String? = null

    @field:XmlAttribute(name = "name", required = true)
    var name: String? = null

    @field:XmlAttribute(name = "artist")
    var artist: String? = null

    @field:XmlAttribute(name = "artistId")
    var artistId: String? = null

    @field:XmlAttribute(name = "coverArt")
    var coverArt: String? = null

    @field:XmlAttribute(name = "songCount", required = true)
    var songCount: Int = 0

    @field:XmlAttribute(name = "duration", required = true)
    var duration: Int = 0

    @field:XmlAttribute(name = "playCount")
    var playCount: Long? = null

    @field:XmlAttribute(name = "created", required = true)
    @field:XmlSchemaType(name = "dateTime")
    var created: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "starred")
    @field:XmlSchemaType(name = "dateTime")
    var starred: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "year")
    var year: Int? = null

    @field:XmlAttribute(name = "genre")
    var genre: String? = null

    @field:XmlAttribute(name = "accentColor")
    var accentColor: String? = null

}
