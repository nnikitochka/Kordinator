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
@XmlType(name = "PlayQueue", propOrder = ["entry"])
class PlayQueue {
    @field:XmlElement(name = "entry")
    val entry: MutableList<Child> = mutableListOf()

    @field:XmlAttribute(name = "current")
    var current: Int? = null

    @field:XmlAttribute(name = "position")
    var position: Long? = null

    @field:XmlAttribute(name = "username", required = true)
    var username: String? = null

    @field:XmlAttribute(name = "changed", required = true)
    @field:XmlSchemaType(name = "dateTime")
    var changed: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "changedBy", required = true)
    var changedBy: String? = null

}
