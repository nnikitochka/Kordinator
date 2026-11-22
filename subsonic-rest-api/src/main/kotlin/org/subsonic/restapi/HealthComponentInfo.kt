package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HealthComponentInfo", propOrder = ["details", "component"])
class HealthComponentInfo {
    @field:XmlAttribute
    var name: String = ""
    @field:XmlAttribute
    var status: String = "UNKNOWN"
    // JSON preserves heterogeneous provider details in both XML and JSON responses.
    @field:XmlElement
    var details: String? = null
    @field:XmlElement(name = "component") val component: MutableList<HealthComponentInfo> = mutableListOf()
}