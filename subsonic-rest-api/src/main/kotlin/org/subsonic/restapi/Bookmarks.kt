@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Bookmarks", propOrder = ["bookmark"])
class Bookmarks {
    @field:XmlElement(name = "bookmark")
    val bookmark: MutableList<Bookmark> = mutableListOf()

}
