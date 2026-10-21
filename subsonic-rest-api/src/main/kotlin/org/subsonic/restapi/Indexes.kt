@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Indexes", propOrder = ["shortcut", "index", "child"])
class Indexes {
    @field:XmlElement(name = "shortcut")
    val shortcut: MutableList<Artist> = mutableListOf()

    @field:XmlElement(name = "index")
    val index: MutableList<Index> = mutableListOf()

    @field:XmlElement(name = "child")
    val child: MutableList<Child> = mutableListOf()

    @field:XmlAttribute(name = "lastModified", required = true)
    var lastModified: Long = 0L

    @field:XmlAttribute(name = "ignoredArticles", required = true)
    var ignoredArticles: String? = null

}
