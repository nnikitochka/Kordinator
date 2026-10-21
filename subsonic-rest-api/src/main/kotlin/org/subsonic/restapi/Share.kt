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
@XmlType(name = "Share", propOrder = ["entry"])
class Share {
    @field:XmlElement(name = "entry")
    val entry: MutableList<Child> = mutableListOf()

    @field:XmlAttribute(name = "id", required = true)
    var id: String? = null

    @field:XmlAttribute(name = "url", required = true)
    var url: String? = null

    @field:XmlAttribute(name = "description")
    var description: String? = null

    @field:XmlAttribute(name = "username", required = true)
    var username: String? = null

    @field:XmlAttribute(name = "created", required = true)
    @field:XmlSchemaType(name = "dateTime")
    var created: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "expires")
    @field:XmlSchemaType(name = "dateTime")
    var expires: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "lastVisited")
    @field:XmlSchemaType(name = "dateTime")
    var lastVisited: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "visitCount", required = true)
    var visitCount: Int = 0

}
