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
@XmlType(name = "Directory", propOrder = ["child"])
class Directory {
    @field:XmlElement(name = "child")
    val child: MutableList<Child> = mutableListOf()

    @field:XmlAttribute(name = "id", required = true)
    var id: String? = null

    @field:XmlAttribute(name = "parent")
    var parent: String? = null

    @field:XmlAttribute(name = "name", required = true)
    var name: String? = null

    @field:XmlAttribute(name = "starred")
    @field:XmlSchemaType(name = "dateTime")
    var starred: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "userRating")
    var userRating: Int? = null

    @field:XmlAttribute(name = "averageRating")
    var averageRating: Double? = null

    @field:XmlAttribute(name = "playCount")
    var playCount: Long? = null

    @field:XmlAttribute(name = "accentColor")
    var accentColor: String? = null

}
