@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlSchemaType
import jakarta.xml.bind.annotation.XmlType
import javax.xml.datatype.XMLGregorianCalendar
import kotlin.jvm.JvmName

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Playlist", propOrder = ["allowedUser"])
open class Playlist {
    @field:XmlElement(name = "allowedUser")
    val allowedUser: MutableList<String> = mutableListOf()

    @field:XmlAttribute(name = "id", required = true)
    var id: String? = null

    @field:XmlAttribute(name = "name", required = true)
    var name: String? = null

    @field:XmlAttribute(name = "comment")
    var comment: String? = null

    @field:XmlAttribute(name = "owner")
    var owner: String? = null

    @field:XmlAttribute(name = "public")
    @get:JvmName("isPublic")
    @set:JvmName("setPublic")
    var publicValue: Boolean? = null

    @field:XmlAttribute(name = "songCount", required = true)
    var songCount: Int = 0

    @field:XmlAttribute(name = "duration", required = true)
    var duration: Int = 0

    @field:XmlAttribute(name = "created", required = true)
    @field:XmlSchemaType(name = "dateTime")
    var created: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "changed", required = true)
    @field:XmlSchemaType(name = "dateTime")
    var changed: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "coverArt")
    var coverArt: String? = null

}
