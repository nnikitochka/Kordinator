@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlSchemaType
import jakarta.xml.bind.annotation.XmlType
import javax.xml.datatype.XMLGregorianCalendar

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArtistID3")
open class ArtistID3 {
    @field:XmlAttribute(name = "id", required = true)
    var id: String? = null

    @field:XmlAttribute(name = "name", required = true)
    var name: String? = null

    @field:XmlAttribute(name = "coverArt")
    var coverArt: String? = null

    @field:XmlAttribute(name = "albumCount", required = true)
    var albumCount: Int = 0

    @field:XmlAttribute(name = "starred")
    @field:XmlSchemaType(name = "dateTime")
    var starred: XMLGregorianCalendar? = null

}
