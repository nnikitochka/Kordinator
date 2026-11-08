package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WebsiteSettings")
class WebsiteSettings {
    @field:XmlAttribute var showSideBar: Boolean = false
    @field:XmlAttribute var viewAsList: Boolean = false
}
