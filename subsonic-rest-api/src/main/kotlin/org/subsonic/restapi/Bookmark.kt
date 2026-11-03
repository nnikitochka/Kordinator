@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlSchemaType
import jakarta.xml.bind.annotation.XmlType
import javax.xml.datatype.XMLGregorianCalendar

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Bookmark", propOrder = ["entry"])
class Bookmark {
    @field:XmlElement(name = "entry")
    var entry: Child? = null

    // Opt-in availability information for clients displaying offline media.
    @field:XmlAttribute(name = "entryPresent")
    var entryPresent: Boolean? = null

    @field:XmlAttribute(name = "position", required = true)
    var position: Long = 0L

    @field:XmlAttribute(name = "username", required = true)
    var username: String? = null

    @field:XmlAttribute(name = "comment")
    var comment: String? = null

    @field:XmlAttribute(name = "created", required = true)
    @field:XmlSchemaType(name = "dateTime")
    var created: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "changed", required = true)
    @field:XmlSchemaType(name = "dateTime")
    var changed: XMLGregorianCalendar? = null

}
