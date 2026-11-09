package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlType

/** Admin extension for the UPnP/DLNA media server configuration. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DLNASettings")
class DLNASettings {
    @field:XmlAttribute var dlnaEnabled: Boolean = false
    @field:XmlAttribute var dlnaServerName: String = "Airsonic"
    @field:XmlAttribute var dlnaBaseLANURL: String = ""
}
